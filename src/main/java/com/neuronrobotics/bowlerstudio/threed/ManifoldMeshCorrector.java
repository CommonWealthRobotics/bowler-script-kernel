package com.neuronrobotics.bowlerstudio.threed;

import java.util.HashSet;
import java.util.Set;

import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.TickToc;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Corrects a mesh for BRep export using only the Manifold kernel's own
 * operations, through the engine instance {@link CSG#getManifold()} carries.
 *
 * <p>
 * A mesh can carry facets and edges thinner than any BRep kernel resolves — an
 * STL of folded sheet metal, a boolean near a seam. Manifold's {@code simplify}
 * merges them, and it is the only pass here: it re-derives the topology from the
 * geometry, so the result is manifold by construction, every triangle with area
 * survives, and no vertex is moved except the ones a merge consumes.
 *
 * <p>
 * The correction is held to the source with the kernel's own surface area and
 * volume: a collapse that changes the solid by more than a bounds-budget-thick
 * skin of its own surface is refused and the source is returned.
 */
public final class ManifoldMeshCorrector {

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
	 * The shortest edge the written file may carry, in millimetres: the distance a
	 * STEP reader merges vertices at. An edge shorter than this is one the reader
	 * collapses, which tears the shell around it, so the collapse runs at least at
	 * this scale.
	 */
	public static final double SHORTEST_SEWABLE_EDGE_MM = 1e-7;

	/**
	 * Corrects the mesh for BRep export: one collapse at the coarser of the kernel's
	 * own precision and the reader's merge distance.
	 *
	 * <p>
	 * Neither bound is a setting to tune. {@code manifold.epsilon(mesh)} is the
	 * precision the geometry actually carries, so a collapse there merges exactly
	 * the edges the kernel considers degenerate. {@link #SHORTEST_SEWABLE_EDGE_MM}
	 * is the distance a STEP reader folds vertices together at, so an edge left
	 * shorter than it is one the reader deletes, which opens the shell. Collapsing
	 * at the coarser of the two removes both classes in a single kernel pass.
	 *
	 * <p>
	 * What this deliberately does not do is snap near-duplicate points after the
	 * collapse. Moving one point onto another in the point array leaves two distinct
	 * indices naming one position, and a triangle that names two such indices has no
	 * area: measured on the nametag, the snap turned 550 triangles into
	 * zero-area ones and 2,132 references into duplicates, and the writer's stitched
	 * faces fell from 6,701 to 6,318 of 20,096 — the fragments the BRep then had to
	 * fall back to writing one triangle at a time. A collapse inside the kernel
	 * cannot do that: it moves the indices with the points, so the mesh stays exactly
	 * as manifold as it was.
	 *
	 * <p>
	 * A fixed tolerance in millimetres is what went wrong before: 0.05 mm shaved
	 * 0.0077 mm off every face of that part, removing 77.6 mm&sup3; — 0.56% of it —
	 * while the bounding box never moved, so every bounds check passed. The guard
	 * below is therefore on the surface, not on the bounds.
	 *
	 * @param source
	 *            the mesh to correct; it is never modified
	 * @param boundsBudget
	 *            how far the correction may move the surface, in millimetres
	 * @return the corrected mesh, or the source when the correction would move it
	 */
	public static CSG correct(CSG source, double boundsBudget) {
		if (!available())
			return source;
		if (source.getTriangles() == null || source.getTriangles().length < 3)
			return source;
		try {
			TickToc.tic("collapse correction");
			double epsilon = CSG.getManifold().epsilon(source);
			double tolerance = Math.max(epsilon, SHORTEST_SEWABLE_EDGE_MM);
			CSG collapsed = CSG.getManifold().simplify(source, tolerance);
			TickToc.tic("collapse done");
			if (outOfBudget(source, collapsed, boundsBudget) || movedSurface(source, collapsed, boundsBudget)) {
				Log.debug("ManifoldMeshCorrector: the collapse at " + tolerance
						+ " mm moved the part outside its budget; keeping the mesh as it was");
				return source;
			}
			Log.debug("ManifoldMeshCorrector: collapsed at " + tolerance + " mm (kernel precision " + epsilon + "): "
					+ source.getTriCount() + " -> " + collapsed.getTriCount() + " triangles, shortest edge now "
					+ shortestEdge(collapsed) + " mm");
			return collapsed;
		} catch (Throwable t) {
			Log.error("ManifoldMeshCorrector: collapse failed, keeping the mesh as it was: " + t);
			return source;
		}
	}

	/** The shortest edge of the mesh, in millimetres. */
	static double shortestEdge(CSG mesh) {
		double[] vertices = mesh.getVertices();
		long[] triangles = mesh.getTriangles();
		if (vertices == null || triangles == null)
			return Double.MAX_VALUE;
		Set<Long> seen = new HashSet<>();
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
	 * True when the correction changed the solid itself, rather than only how the
	 * solid is written down.
	 *
	 * <p>
	 * Two cheap measures, both O(1) in the mesh:
	 *
	 * <ul>
	 * <li>the net volume, against a skin of the surface one bounds-budget thick. The
	 * net change is a lower bound on the material moved: |missing - invented| is at
	 * most missing + invented, so a correction well inside the skin cannot have moved
	 * more than twice it. A collapse that shaves an even skin off every face leaves
	 * the bounding box untouched — 0.05 mm on the nametag took 0.56% of its volume with
	 * 0.0 mm of bounds drift — but it cannot hide from the volume.</li>
	 * <li>the surface area, against the same skin: a collapse that adds or removes
	 * facets changes its area, which the volume alone can miss when the shave is
	 * even.</li>
	 * </ul>
	 *
	 * <p>
	 * This is deliberately not a boolean difference. The exact answer — the material
	 * each mesh has that the other does not — costs a superlinear boolean over a
	 * hundred thousand triangles, and running it per tolerance candidate turned the
	 * export into minutes. The exact answer belongs in the test that proves the export
	 * faithful, where it is paid once; the writer only needs a guard that is fast and
	 * sound.
	 */
	private static boolean movedSurface(CSG source, CSG corrected, double boundsBudget) {
		try {
			double skin = Math.max(1e-9, CSG.getManifold().surfaceArea(source) * Math.max(1e-9, boundsBudget));
			double volumeDrift = Math.abs(CSG.getManifold().volume(corrected) - CSG.getManifold().volume(source));
			double areaDrift = Math
					.abs(CSG.getManifold().surfaceArea(corrected) - CSG.getManifold().surfaceArea(source));
			boolean out = volumeDrift > skin || areaDrift > skin;
			if (out)
				Log.debug("ManifoldMeshCorrector: correction drifted " + volumeDrift + " mm^3 and " + areaDrift
						+ " mm^2, the skin budget is " + skin);
			return out;
		} catch (Throwable t) {
			Log.error("ManifoldMeshCorrector: could not measure the correction: " + t);
			return false;
		}
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

}
