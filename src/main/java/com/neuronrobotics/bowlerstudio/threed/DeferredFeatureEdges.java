package com.neuronrobotics.bowlerstudio.threed;

import java.util.Collections;
import java.util.List;

import eu.mihosoft.vrl.v3d.CSG;

/** Defers display-only extraction; snapshots protect against later CSG edits. */
final class DeferredFeatureEdges {
	private final double[] positions;
	private final long[] triangles;
	private final double angle;
	private final double repairTolerance;
	private final double stitchTolerance;
	private boolean computed;
	private List<FeatureEdgeExtractor.Edge> edges = Collections.emptyList();

	DeferredFeatureEdges(CSG csg, double angle, double repairTolerance, double stitchTolerance) {
		positions = csg.getVertices().clone();
		triangles = csg.getTriangles().clone();
		this.angle = angle;
		this.repairTolerance = repairTolerance;
		this.stitchTolerance = stitchTolerance;
	}

	synchronized List<FeatureEdgeExtractor.Edge> get() {
		if (!computed) {
			edges = FeatureEdgeExtractor.extract(positions, triangles, angle, repairTolerance, stitchTolerance);
			computed = true;
		}
		return edges;
	}
}
