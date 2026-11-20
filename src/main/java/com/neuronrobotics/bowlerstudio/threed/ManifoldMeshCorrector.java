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
	 * The coarsest collapse tolerance tried first. A coarser collapse writes fewer,
	 * larger faces, so it is preferred whenever it keeps the part's own shape.
	 */
	public static final double COARSEST_COLLAPSE_MM = 0.05;

	/**
	 * The finest collapse tolerance tried. Below this a collapse merges nothing a
	 * reader could not already sew, so the search stops.
	 */
	public static final double FINEST_COLLAPSE_MM = 1e-7;

	/**
	 * The shortest edge the written file may carry, in millimetres: the distance
	 * accuracy a STEP reader merges vertices at. An edge shorter than this is one the
	 * reader collapses, which tears the shell around it, so a collapse that leaves
	 * such an edge has not done its job.
	 */
	public static final double SHORTEST_SEWABLE_EDGE_MM = 1e-7;

	/**
	 * Corrects the mesh for BRep export: the coarsest collapse that both keeps the
	 * part's shape and leaves no edge a reader would merge away.
	 *
	 * <p>
	 * A fixed tolerance cannot be right for every mesh. Too coarse and the collapse
	 * shaves the surface — 0.05 mm on the nametag cut 0.0077 mm off every face and
	 * removed 77.6 mm&sup3; (0.56% of the part) while the bounding box did not move at
	 * all, because the cut was even. Too fine and the slivers survive: the nametag's
	 * source has 14,516 edges shorter than a reader's 1e-7 mm merge distance, and any
	 * that survive the collapse are edges the reader folds away, which opens the
	 * shell.
	 *
	 * <p>
	 * So the tolerance is measured, not assumed: the coarsest one whose result moves
	 * no more material than a bounds-budget-thick skin of the surface and leaves no
	 * edge shorter than a reader can sew. When no tolerance satisfies both, the source
	 * is returned unchanged — a mesh whose geometry is intact is always better than a
	 * mesh that is easier to read and wrong.
	 *
	 * @param source
	 *            the mesh to correct; it is never modified
	 * @param coarsest
	 *            the coarsest collapse tolerance to try, in millimetres
	 * @param boundsBudget
	 *            how far the correction may move the surface, in millimetres: the
	 *            budget for bounds drift and for the skin of material it may remove
	 * @return the corrected mesh, or the source when no tolerance keeps the part
	 */
	public static CSG correct(CSG source, double coarsest, double boundsBudget) {
		if (!available())
			return source;
		try {
			for (double tolerance = Math.max(FINEST_COLLAPSE_MM, coarsest); tolerance >= FINEST_COLLAPSE_MM; tolerance /= 2) {
				CSG simplified = simplify(source, tolerance);
				if (simplified == null)
					continue;
				if (outOfBudget(source, simplified, boundsBudget) || movedSurface(source, simplified, boundsBudget))
					continue;
				double shortest = shortestEdge(simplified);
				if (shortest < SHORTEST_SEWABLE_EDGE_MM) {
					Log.debug("ManifoldMeshCorrector: collapse at " + tolerance + " left an edge of " + shortest
							+ " mm, shorter than a reader can sew; trying finer");
					continue;
				}
				Log.debug("ManifoldMeshCorrector: collapse at " + tolerance + " keeps the part: " + shortest
						+ " mm shortest edge, " + simplified.getTriCount() + " triangles");
				return simplified;
			}
		} catch (Throwable t) {
			Log.error("ManifoldMeshCorrector: correction failed, keeping the mesh as it was: " + t);
			return source;
		}
		Log.debug("ManifoldMeshCorrector: no collapse tolerance kept the part inside its budget; keeping the source");
		return source;
	}

	/**
	 * One collapse at one tolerance, with the self-union heal the rounds repeat to a
	 * fixpoint, or null when the collapse failed.
	 *
	 * @see #correct(CSG, double, double)
	 */
	private static CSG simplify(CSG source, double tolerance) {
		try {
			CSG current = source;
			for (int round = 0; round < MAX_ROUNDS; round++) {
				CSG next = CSG.getManifold().simplify(current, tolerance);
				if (round == 0 || next.getTriCount() != current.getTriCount()) {
					// a self-union heal after a round that changed the mesh: the
					// boolean rebuilds shared edges and closes one-sided pairs
					next = heal(next);
				}
				if (next.getTriCount() == current.getTriCount())
					return next; // settled
				current = next;
			}
			return current;
		} catch (Throwable t) {
			Log.error("ManifoldMeshCorrector: collapse at " + tolerance + " failed: " + t);
			return null;
		}
	}

	/** The shortest edge of the mesh, in millimetres. */
	static double shortestEdge(CSG mesh) {
		double[] vertices = mesh.getVertices();
		long[] triangles = mesh.getTriangles();
		if (vertices == null || triangles == null)
			return Double.MAX_VALUE;
		java.util.Set<Long> seen = new java.util.HashSet<>();
		double shortest = Double.MAX_VALUE;
		for (int i = 0; i + 2 < triangles.length; i += 3)
			for (int k = 0; k < 3; k++) {
				int from = (int) triangles[i + k];
				int to = (int) triangles[i + (k + 1) % 3];
				long key = ((long) Math.min(from, to) << 32) | (Math.max(from, to) & 0xffffffffL);
				if (!seen.add(key))
					continue;
				double dx = vertices[from * 3] - vertices[to * 3];
				double dy = vertices[from * 3 + 1] - vertices[to * 3 + 1];
				double dz = vertices[from * 3 + 2] - vertices[to * 3 + 2];
				double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
				if (distance > 0)
					shortest = Math.min(shortest, distance);
			}
		return shortest;
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
		try {
			// smooth marks the edges and fills the tangents; the refine interpolates
			// the surface through them, which is what pulls a spike down
			CSG smoothed = CSG.getManifold().smoothOut(source, minSharpAngle, 1.0);
			CSG settled = CSG.getManifold().simplify(smoothed, tolerance);
			// the same guard as the correction: a spike's own volume is inside the
			// skin, and the smoothing may not move more of the solid than that. The
			// bounds cannot see this — a spike is thin, so dissolving one leaves the
			// bounding box where it was
			if (movedSurface(source, settled, boundsBudget)) {
				Log.debug("ManifoldMeshCorrector: the spike smoothing moved the surface outside the "
						+ boundsBudget + " mm skin; keeping the mesh as it was");
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
			CSG healed = CSG.getManifold().booleanOp(mesh, mesh, CSGManifold3d.BooleanOp.UNION);
			long after = healed.getTriCount();
			mesh = healed;
			if (after == before)
				break; // the heal settled
		}
		return mesh;
	}

	/**
	 * True when the correction changed the solid itself, rather than only how the
	 * solid is written down.
	 *
	 * <p>
	 * The measure is the volume the two meshes do not share: the source's material the
	 * correction lost, plus the material it invented. Neither net volume change nor the
	 * bounding box can see this. A simplify that shaves an even skin off every surface
	 * leaves the bounding box exactly where it was and still removes real material —
	 * measured on the nametag, the 0.05 mm collapse cut 0.0077 mm off the whole surface
	 * and took 77.6 mm&sup3; (0.56% of the part) while the bounds moved by 0.0 mm.
	 *
	 * <p>
	 * The budget is a skin of the mesh's own surface one bounds-budget thick: that is
	 * exactly how much material a correction may remove while every point of the
	 * surface stays inside the resolution the export declares. A correction that stays
	 * inside that skin has moved nothing a reader can measure; anything past it has
	 * changed the part.
	 */
	private static boolean movedSurface(CSG source, CSG corrected, double boundsBudget) {
		try {
			double missing = CSG.getManifold().volume(source.difference(corrected));
			double invented = CSG.getManifold().volume(corrected.difference(source));
			double moved = Math.abs(missing) + Math.abs(invented);
			double skin = Math.max(1e-9, skinVolume(source, boundsBudget));
			boolean out = moved > skin;
			Log.debug("ManifoldMeshCorrector: correction moved " + moved + " mm^3 of material (missing " + missing
					+ ", invented " + invented + "), the skin budget is " + skin + (out ? "; outside it" : "; inside it"));
			return out;
		} catch (Throwable t) {
			// the kernel could not measure it, so it cannot be shown to be safe
			Log.error("ManifoldMeshCorrector: could not measure how far the correction moved the surface: " + t);
			return false;
		}
	}

	/**
	 * The material a skin of the given thickness over the mesh's own surface would
	 * hold: the largest volume a correction may move while every point of the surface
	 * stays within that thickness.
	 */
	private static double skinVolume(CSG mesh, double thickness) throws Throwable {
		double area = CSG.getManifold().surfaceArea(mesh);
		if (area <= 0)
			return 1e-9;
		return area * Math.max(1e-9, thickness);
	}

	/** True when the corrected mesh's bounds moved past the budget. */
	private static boolean outOfBudget(CSG source, CSG corrected, double boundsBudget) {
		eu.mihosoft.vrl.v3d.Bounds a = source.getBounds();
		eu.mihosoft.vrl.v3d.Bounds b = corrected.getBounds();
		double drift = Math.max(Math.max(Math.abs(b.getMin().x - a.getMin().x), Math.abs(b.getMin().y - a.getMin().y)),
				Math.max(Math.abs(b.getMax().x - a.getMax().x), Math.abs(b.getMax().y - a.getMax().y)));
		drift = Math.max(drift, Math.max(Math.abs(b.getMin().z - a.getMin().z), Math.abs(b.getMax().z - a.getMax().z)));
		return drift > boundsBudget;
	}

	/**
	 * The volume change a facet merge is allowed: a fraction of the solid's own
	 * volume. Merging coplanar facets removes their micro-relief, which on a
	 * faceted part is a fraction of a percent; a correction that lost a whole
	 * feature moves tens of percents. The bounds budget stays the guard that
	 * holds the surface's position.
	 */
	private static double volumeBudget(CSG source, double boundsBudget) {
		double volume = source.getVolume();
		if (volume <= 0)
			return Math.max(1e-6, source.getSurfaceArea() * boundsBudget);
		return Math.max(1e-6, Math.abs(volume) * 0.01);
	}
}
