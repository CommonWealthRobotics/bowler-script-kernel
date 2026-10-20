package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.List;

import com.neuronrobotics.bowlerstudio.threed.StepImport.MeshBuilder;
import com.neuronrobotics.bowlerstudio.threed.StepReader.Instance;
import com.neuronrobotics.bowlerstudio.threed.StepReader.Value;

/**
 * Reads BRep topology: every face of a solid or surface model is triangulated
 * from its bound loops, and every edge is emitted as a straight segment.
 *
 * <p>
 * Faces are expected to be planar or faceted, which is the case for the faceted
 * BRep files that are exchanged alongside tessellated ones. A curved face is
 * approximated by its bound loops, so its facets follow the loop chords rather
 * than the true surface.
 */
final class StepBRep {

	private StepBRep() {
	}

	static void read(StepReader reader, Instance model, double scale, MeshBuilder mesh,
			List<FeatureEdgeExtractor.Edge> edges) {
		Value boundary = boundaryOf(reader, model);
		for (Value faceReference : reader.list(boundary)) {
			Instance face = reader.instance(faceReference);
			if (!StepReader.kindOf(face, "FACE_SURFACE"))
				continue;
			readFace(reader, face, scale, mesh, edges);
		}
	}

	/**
	 * The faces owned directly by a solid or surface model. A surface model names
	 * a set of shells rather than faces, and each shell is a connected face set,
	 * so its shells are flattened into the faces they hold.
	 */
	static Value boundaryOf(StepReader reader, Instance model) {
		if (model.is("MANIFOLD_SOLID_BREP"))
			return reader.attribute(reader.instance(reader.attribute(model, "MANIFOLD_SOLID_BREP", "outer")),
					"CONNECTED_FACE_SET", "cfs_faces");
		if (model.is("SHELL_BASED_SURFACE_MODEL")) {
			List<Value> faces = new ArrayList<>();
			for (Value shell : reader.list(reader.attribute(model, "SHELL_BASED_SURFACE_MODEL", "sbsm_boundary")))
				faces.addAll(reader.list(reader.attribute(reader.instance(shell), "CONNECTED_FACE_SET", "cfs_faces")));
			return Value.list(faces);
		}
		return reader.attribute(model, "CONNECTED_FACE_SET", "cfs_faces");
	}

	/** Models that describe their own solid, read as one shape each. */
	static boolean isSolidModel(Instance instance) {
		return StepReader.kindOf(instance, "SOLID_MODEL") || StepReader.kindOf(instance, "SHELL_BASED_SURFACE_MODEL");
	}

	private static void readFace(StepReader reader, Instance face, double scale, MeshBuilder mesh,
			List<FeatureEdgeExtractor.Edge> edges) {
		List<Value> bounds = reader.list(reader.attribute(face, "FACE_SURFACE", "bounds"));
		// a face that names no bound has no geometry to place, though its surface
		// may still be referenced by the rest of the model
		if (bounds.isEmpty())
			return;

		List<List<double[]>> loops = new ArrayList<>();
		for (Value boundReference : bounds)
			loops.add(loopPoints(reader, outerLoop(reader, boundReference), scale, edges));

		// the bound a face marks as its outer one is the outer loop; when none is
		// marked, the one enclosing the most area is
		int outer = 0;
		for (int i = 0; i < loops.size(); i++)
			if (isOuter(reader, bounds.get(i)))
				outer = i;
		for (int i = 0; i < loops.size(); i++)
			if (!isOuter(reader, bounds.get(i)) && signedArea(loops.get(i)) > signedArea(loops.get(outer)))
				outer = i;

		if (!isPlanar(loops.get(outer)))
			return; // curved face: only its edges are meaningful

		List<List<Integer>> indices = new ArrayList<>();
		for (List<double[]> loop : loops) {
			List<Integer> index = new ArrayList<>();
			for (double[] point : loop)
				index.add(mesh.vertex(point));
			indices.add(index);
		}

		List<List<Integer>> holes = new ArrayList<>(indices);
		holes.remove(outer);
		for (int[] triangle : Triangulator.triangulate(indices.get(outer), holes, mesh.positions()))
			mesh.addTriangle(triangle[0], triangle[1], triangle[2]);
	}

	private static Instance outerLoop(StepReader reader, Value bound) {
		return reader.instance(reader.attribute(reader.instance(bound), "FACE_BOUND", "bound"));
	}

	/** True when a bound is the face's own outer one. */
	private static boolean isOuter(StepReader reader, Value bound) {
		return reader.instance(bound).is("FACE_OUTER_BOUND");
	}

	/**
	 * True when the loop lies in a plane. A loop whose points are collinear, such as
	 * a meridian of a cylinder, is trivially planar.
	 */
	private static boolean isPlanar(List<double[]> points) {
		if (points.size() < 4)
			return true;

		double[] origin = points.get(0);
		double[] first = null;
		for (int i = 1; i < points.size() && first == null; i++)
			first = subtract(points.get(i), origin);

		if (first == null)
			return true;

		double[] normal = null;
		for (int i = 1; i < points.size(); i++) {
			double[] candidate = cross(first, subtract(points.get(i), origin));
			double length = Math.sqrt(dot(candidate, candidate));
			if (length > 1e-9) {
				normal = new double[]{candidate[0] / length, candidate[1] / length, candidate[2] / length};
				break;
			}
		}

		if (normal == null)
			return true; // all points are collinear

		double extent = 0;
		for (double[] point : points) {
			double[] offset = subtract(point, origin);
			extent = Math.max(extent, Math.abs(dot(offset, normal)));
		}
		return extent <= 1e-6 * (1 + scaleOf(points));
	}

	private static double scaleOf(List<double[]> points) {
		double extent = 0;
		for (double[] point : points)
			for (int axis = 0; axis < 3; axis++)
				extent = Math.max(extent, Math.abs(point[axis]));
		return extent;
	}

	private static double[] subtract(double[] a, double[] b) {
		return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	/**
	 * The polygon of a face bound, walking oriented edges through their curves, and
	 * recording every edge of the loop as a segment.
	 */
	private static List<double[]> loopPoints(StepReader reader, Instance loop, double scale,
			List<FeatureEdgeExtractor.Edge> edges) {
		List<double[]> points = new ArrayList<>();

		if (loop.is("VERTEX_LOOP"))
			return points; // a single point: a degenerate face such as a sphere pole

		if (loop.is("POLY_LOOP")) {
			for (Value point : reader.list(reader.attribute(loop, "POLY_LOOP", "polygon")))
				points.add(StepImport.scaled(StepCurves.point(reader, point), scale));
			close(points, edges);
			return points;
		}

		for (Value orientedReference : reader.list(reader.attribute(loop, "EDGE_LOOP", "edge_list"))) {
			Instance oriented = reader.instance(orientedReference);
			Instance edgeCurve = reader.instance(reader.attribute(oriented, "ORIENTED_EDGE", "edge_element"));
			boolean forward = isTrue(reader.attribute(oriented, "ORIENTED_EDGE", "orientation"));

			List<double[]> samples = edgeSamples(reader, edgeCurve, scale, forward, edges);
			for (double[] sample : samples)
				if (points.isEmpty() || !same(points.get(points.size() - 1), sample))
					points.add(sample);
		}

		// the loop closes back onto its first point
		if (points.size() > 1 && same(points.get(0), points.get(points.size() - 1)))
			points.remove(points.size() - 1);

		return points;
	}

	/** Samples one edge, emitting its segments and returning them in walk order. */
	private static List<double[]> edgeSamples(StepReader reader, Instance edge, double scale, boolean forward,
			List<FeatureEdgeExtractor.Edge> edges) {
		double[][] endpoints = StepCurves.edgeEndpoints(reader, edge);
		double[] start = forward ? endpoints[0] : endpoints[1];
		double[] end = forward ? endpoints[1] : endpoints[0];

		Instance geometry = reader.optionalInstance(reader.attribute(edge, "EDGE_CURVE", "edge_geometry"));
		List<double[]> samples = StepCurves.sample(reader, geometry, start, end, 8);

		List<double[]> scaledSamples = new ArrayList<>(samples.size());
		for (double[] sample : samples)
			scaledSamples.add(StepImport.scaled(sample, scale));

		appendSegments(scaledSamples, edges);
		return scaledSamples;
	}

	/** A POLY_LOOP carries no edges, so its segments are recorded directly. */
	private static void close(List<double[]> points, List<FeatureEdgeExtractor.Edge> edges) {
		appendSegments(points, edges);
		if (points.size() > 1)
			appendSegments(List.of(points.get(points.size() - 1), points.get(0)), edges);
	}

	private static void appendSegments(List<double[]> points, List<FeatureEdgeExtractor.Edge> edges) {
		for (int i = 0; i + 1 < points.size(); i++)
			StepImport.addSegment(points.get(i), points.get(i + 1), edges);
	}

	private static boolean isTrue(Value value) {
		return value != null && value.kind() == Value.Kind.ENUM && "T".equals(value.enumeration());
	}

	private static boolean same(double[] a, double[] b) {
		double tolerance = 1e-9;
		return Math.abs(a[0] - b[0]) < tolerance && Math.abs(a[1] - b[1]) < tolerance
				&& Math.abs(a[2] - b[2]) < tolerance;
	}

	/** Projected area of a loop, used to pick the outer bound. */
	private static double signedArea(List<double[]> loop) {
		if (loop.size() < 3)
			return 0;
		double nx = 0;
		double ny = 0;
		double nz = 0;
		for (int i = 0; i < loop.size(); i++) {
			double[] a = loop.get(i);
			double[] b = loop.get((i + 1) % loop.size());
			nx += (a[1] - b[1]) * (a[2] + b[2]);
			ny += (a[2] - b[2]) * (a[0] + b[0]);
			nz += (a[0] - b[0]) * (a[1] + b[1]);
		}
		return Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
	}
}
