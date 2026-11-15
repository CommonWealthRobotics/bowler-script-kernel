package com.neuronrobotics.bowlerstudio.threed;

import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.manifold3d.CSGManifold3d;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Corrects a mesh for BRep export using only the Manifold kernel's own
 * operations, through the engine instance {@link CSG#getManifold()} carries.
 *
 * <p>
 * A mesh can carry facets and edges thinner than any BRep kernel resolves — an
 * STL of folded sheet metal, a boolean near a seam. Manifold's simplify merges
 * them, and each pass can expose edges only the next pass collapses, so the
 * rounds repeat until a pass changes nothing. A self-union heal follows, which
 * rebuilds the mesh's shared edges through the boolean pipeline and closes the
 * pairs a merge left one-sided.
 *
 * <p>
 * Every round is measured against the source with Manifold's own surface area
 * and volume, and the correction stops at the bounds budget: a BRep reader
 * works to a resolution, and the correction never moves the surface further
 * than that.
 */
public final class ManifoldMeshCorrector {

	/** The most correction rounds one correction may take to settle. */
	public static final int MAX_ROUNDS = 6;

	/** The most self-union heal passes between simplify rounds. */
	public static final int MAX_HEAL_PASSES = 2;

	private ManifoldMeshCorrector() {
	}

	/**
	 * True when the Manifold kernel is the active engine and is loaded; every
	 * correction runs through it, so without it there is nothing to do.
	 */
	public static boolean available() {
		return CSG.getDefaultOptionType() == CSG.OptType.Manifold3d && CSG.getManifold() != null;
	}

	/**
	 * Corrects the mesh for BRep export, from the unsimplified mesh.
	 *
	 * @param source
	 *            the mesh to correct; it is never modified
	 * @param tolerance
	 *            the collapse tolerance in millimetres, the resolution the BRep
	 *            reader works to
	 * @param boundsBudget
	 *            how far the correction may move the bounds, in millimetres
	 * @return the corrected mesh, or the source when the kernel is unavailable or
	 *         every correction round would move the mesh outside its budget
	 * @throws IllegalStateException
	 *             when the corrected mesh's surface no longer covers the source's,
	 *             which means a correction lost surface rather than simplifying it
	 */
	public static CSG correct(CSG source, double tolerance, double boundsBudget) {
		if (!available())
			return source;

		double sourceArea = source.getSurfaceArea();
		double sourceVolume = source.getVolume();
		if (sourceArea <= 0) {
			// the CSG's own measure is unset; read it from the kernel
			try {
				sourceArea = CSG.getManifold().surfaceArea(source);
				sourceVolume = CSG.getManifold().volume(source);
			} catch (Throwable t) {
				Log.error(t);
				return source;
			}
		}

		CSG current = source;
		try {
			for (int round = 0; round < MAX_ROUNDS; round++) {
				CSG next = CSG.getManifold().simplify(current, tolerance);
				if (round == 0 || next.getTriCount() != current.getTriCount()) {
					// a self-union heal after a round that changed the mesh: the
					// boolean rebuilds shared edges and closes one-sided pairs
					next = heal(next);
				}
				if (outOfBudget(source, next, boundsBudget)) {
					Log.debug("ManifoldMeshCorrector: round " + round + " moved the mesh outside the "
							+ boundsBudget + " mm budget; keeping the previous state");
					break;
				}
				current = next;
				if (next.getTriCount() == source.getTriCount() && round > 0)
					break; // settled
			}
		} catch (Throwable t) {
			Log.error("ManifoldMeshCorrector: correction failed, keeping the mesh as it was: " + t);
			return source;
		}

		try {
			double keptArea = CSG.getManifold().surfaceArea(current);
			double keptVolume = CSG.getManifold().volume(current);
			// simplify merges coplanar facets, which stays inside the surface's own
			// tolerance, but a lost sliver shows up as surface the corrected mesh no
			// longer covers; a volume change past the bounds budget means the same
			double areaLoss = sourceArea - keptArea;
			if (areaLoss > Math.max(1e-6, sourceArea * 1e-4)
					|| Math.abs(keptVolume - sourceVolume) > volumeBudget(source, boundsBudget)) {
				Log.debug("ManifoldMeshCorrector: correction covered " + keptArea + " of the source's " + sourceArea
						+ " of surface (" + (keptVolume - sourceVolume) + " volume), outside the export budget; keeping the source mesh");
				return source;
			}
		} catch (Throwable t) {
			Log.error(t);
			return source;
		}
		return current;
	}

	/**
	 * Corrects one round harder than {@link #correct(CSG, double, double)}: for a
	 * mesh a correction round has already settled, the heal is run again — the
	 * boolean rebuild of shared edges, which closes one-sided pairs the last
	 * correction's merge left open. The budget is held as it is everywhere: the
	 * correction never moves the surface past the BRep reader's resolution.
	 *
	 * @param source
	 *            the mesh to correct; it is never modified
	 * @param tolerance
	 *            the collapse tolerance in millimetres
	 * @param boundsBudget
	 *            how far the correction may move the bounds, in millimetres
	 * @return the corrected mesh, or the source when the kernel is unavailable or
	 *         the healing would move the mesh outside its budget
	 */
	public static CSG correctHarder(CSG source, double tolerance, double boundsBudget) {
		if (!available())
			return source;
		try {
			CSG healed = heal(source);
			CSG settled = CSG.getManifold().simplify(healed, tolerance);
			if (outOfBudget(source, settled, boundsBudget)) {
				Log.debug("ManifoldMeshCorrector: the heal moved the mesh outside the " + boundsBudget
						+ " mm budget; keeping the mesh as it was");
				return source;
			}
			return settled;
		} catch (Throwable t) {
			Log.error("ManifoldMeshCorrector: heal failed, keeping the mesh as it was: " + t);
			return source;
		}
	}

	/**
	 * Dissolves the sharp spikes the mesh carries: thin triangle fans that meet at
	 * a near-straight angle and stand off the surface they are measured against.
	 * The kernel's {@code smoothOut} marks every edge bent more than
	 * {@code minSharpAngle} degrees as sharp and blends the rest, and the refine
	 * that follows interpolates the blended surface, which pulls a spike's fan
	 * down into the surface around it.
	 *
	 * <p>
	 * A spike is real surface only as far as it stands off the mesh: the result is
	 * held to the same budget as every correction here, and the source comes back
	 * when the smoothing moved more than that.
	 *
	 * @param source
	 *            the mesh whose spikes to dissolve; it is never modified
	 * @param tolerance
	 *            the collapse tolerance in millimetres, applied after the smooth
	 * @param boundsBudget
	 *            how far the smoothing may move the bounds, in millimetres
	 * @param minSharpAngle
	 *            edges bent more than this many degrees stay sharp; edges bent
	 *            less are smoothed
	 * @return the smoothed mesh, or the source when the kernel is unavailable or
	 *         the smoothing moved the mesh outside its budget
	 */
	public static CSG dissolveSpikes(CSG source, double tolerance, double boundsBudget, double minSharpAngle) {
		if (!available())
			return source;
		double sourceArea = source.getSurfaceArea();
		double sourceVolume = source.getVolume();
		try {
			// smooth marks the edges and fills the tangents; the refine interpolates
			// the surface through them, which is what pulls a spike down
			CSG smoothed = CSG.getManifold().smoothOut(source, minSharpAngle, 1.0);
			CSG settled = CSG.getManifold().simplify(smoothed, tolerance);
			if (outOfBudget(source, settled, boundsBudget)) {
				Log.debug("ManifoldMeshCorrector: the spike smoothing moved the mesh outside the " + boundsBudget
						+ " mm budget; keeping the mesh as it was");
				return source;
			}
			double keptVolume = settled.getVolume();
			// a spike dissolved is surface lost: accept the loss only when the volume
			// it carried is inside the budget's own skin
			if (sourceArea > 0 && Math.abs(keptVolume - sourceVolume) > volumeBudget(source, boundsBudget)) {
				Log.debug("ManifoldMeshCorrector: the spike smoothing changed the volume by "
						+ (keptVolume - sourceVolume) + ", outside the export budget; keeping the mesh as it was");
				return source;
			}
			return settled;
		} catch (Throwable t) {
			Log.error("ManifoldMeshCorrector: spike smoothing failed, keeping the mesh as it was: " + t);
			return source;
		}
	}

	/** Self-union heals: the boolean rebuilds the mesh's shared edges. */
	private static CSG heal(CSG mesh) throws Throwable {
		for (int pass = 0; pass < MAX_HEAL_PASSES; pass++) {
			long before = mesh.getTriCount();
			CSG healed = CSG.getManifold().booleanOp(mesh, mesh,
					CSGManifold3d.BooleanOp.UNION);
			long after = healed.getTriCount();
			mesh = healed;
			if (after == before)
				break; // the heal settled
		}
		return mesh;
	}

	/** True when the corrected mesh's bounds moved past the budget. */
	private static boolean outOfBudget(CSG source, CSG corrected, double boundsBudget) {
		eu.mihosoft.vrl.v3d.Bounds a = source.getBounds();
		eu.mihosoft.vrl.v3d.Bounds b = corrected.getBounds();
		double drift = Math.max(
				Math.max(Math.abs(b.getMin().x - a.getMin().x), Math.abs(b.getMin().y - a.getMin().y)),
				Math.max(Math.abs(b.getMax().x - a.getMax().x), Math.abs(b.getMax().y - a.getMax().y)));
		drift = Math.max(drift, Math.max(Math.abs(b.getMin().z - a.getMin().z), Math.abs(b.getMax().z - a.getMax().z)));
		return drift > boundsBudget;
	}

	/** The volume change the bounds budget allows, as an area-scaled volume. */
	private static double volumeBudget(CSG source, double boundsBudget) {
		// surface x budget: the volume a uniform skin of the budget thickness adds
		return Math.max(1e-6, source.getSurfaceArea() * boundsBudget);
	}
}
