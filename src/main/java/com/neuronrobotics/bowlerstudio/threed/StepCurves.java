package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.List;

import com.neuronrobotics.bowlerstudio.threed.StepReader.Instance;
import com.neuronrobotics.bowlerstudio.threed.StepReader.StepException;
import com.neuronrobotics.bowlerstudio.threed.StepReader.Value;

/**
 * Turns the curve entities a STEP file can use for edges into sample points.
 * Straight and conic curves are exact; B-splines are evaluated with de Boor's
 * algorithm over the knot vector.
 */
final class StepCurves {

	private StepCurves() {
	}

	/** A cartesian point as {x, y, z}. */
	static double[] point(StepReader reader, Instance point) {
		List<Value> coordinates = reader.list(reader.attribute(point, "CARTESIAN_POINT", "coordinates"));
		return new double[]{reader.number(coordinates.get(0)), reader.number(coordinates.get(1)),
				reader.number(coordinates.get(2))};
	}

	static double[] point(StepReader reader, Value reference) {
		return point(reader, reader.instance(reference));
	}

	/** The origin, x axis and y axis of an AXIS2_PLACEMENT_3D. */
	static double[][] placement(StepReader reader, Instance placement) {
		double[] origin = point(reader, reader.attribute(placement, "PLACEMENT", "location"));
		double[] axis = direction(reader, reader.attribute(placement, "AXIS2_PLACEMENT_3D", "axis"));
		double[] ref = direction(reader, reader.attribute(placement, "AXIS2_PLACEMENT_3D", "ref_direction"));

		if (axis == null)
			axis = new double[]{0, 0, 1};
		if (ref == null)
			ref = new double[]{1, 0, 0};

		double[] x = normalise(orthogonalise(ref, axis));
		double[] y = normalise(cross(axis, x));
		return new double[][]{origin, x, y, normalise(axis)};
	}

	private static double[] direction(StepReader reader, Value reference) {
		if (reference == null || reference.kind() != Value.Kind.REFERENCE)
			return null;
		List<Value> ratios = reader.list(reader.attribute(reader.instance(reference), "DIRECTION", "direction_ratios"));
		double[] vector = new double[]{reader.number(ratios.get(0)), reader.number(ratios.get(1)),
				ratios.size() > 2 ? reader.number(ratios.get(2)) : 0};
		return normalise(vector);
	}

	private static double[] orthogonalise(double[] ref, double[] axis) {
		double projection = dot(ref, axis);
		return new double[]{ref[0] - projection * axis[0], ref[1] - projection * axis[1],
				ref[2] - projection * axis[2]};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] normalise(double[] v) {
		double length = Math.sqrt(dot(v, v));
		if (length == 0)
			return new double[]{0, 0, 1};
		return new double[]{v[0] / length, v[1] / length, v[2] / length};
	}

	/**
	 * Points along the whole curve, in order. {@code start}/{@code end} bound the
	 * curve when it is unbounded or trimmed; they may be null.
	 */
	static List<double[]> sample(StepReader reader, Instance curve, double[] start, double[] end, int segments) {
		if (curve == null)
			return line(start, end);

		// a surface curve is a 3D curve plus the surface it lies on
		Value curve3d = reader.optionalAttribute(curve, "SURFACE_CURVE", "curve_3d");
		if (curve3d != null)
			return sample(reader, reader.instance(curve3d), start, end, segments);

		if (curve.is("TRIMMED_CURVE"))
			return sampleTrimmed(reader, curve, segments);

		if (curve.is("LINE"))
			return line(start, end);

		if (curve.is("POLYLINE")) {
			List<double[]> points = new ArrayList<>();
			for (Value reference : reader.attribute(curve, "POLYLINE", "points").list())
				points.add(point(reader, reference));
			return points;
		}

		if (curve.is("CIRCLE") || curve.is("ELLIPSE"))
			return sampleConic(reader, curve, start, end, segments);

		if (curve.is("B_SPLINE_CURVE_WITH_KNOTS"))
			return sampleBSpline(reader, curve, start, end, segments);

		return line(start, end);
	}

	private static List<double[]> sampleTrimmed(StepReader reader, Instance trimmed, int segments) {
		Instance basis = reader.optionalInstance(reader.attribute(trimmed, "TRIMMED_CURVE", "basis_curve"));
		double[] start = trimPoint(reader, trimmed, "trim_1");
		double[] end = trimPoint(reader, trimmed, "trim_2");
		return sample(reader, basis, start, end, segments);
	}

	private static double[] trimPoint(StepReader reader, Instance trimmed, String attribute) {
		Value value = reader.attribute(trimmed, "TRIMMED_CURVE", attribute);
		if (value == null || !value.isList() || value.list().isEmpty())
			return null;
		Value first = value.list().get(0);
		if (first.kind() == Value.Kind.REFERENCE)
			return point(reader, reader.instance(first));
		return null;
	}

	private static List<double[]> line(double[] start, double[] end) {
		List<double[]> points = new ArrayList<>(2);
		if (start != null)
			points.add(start);
		if (end != null)
			points.add(end);
		return points;
	}

	/** Samples the arc between the two endpoints of a circle or ellipse. */
	private static List<double[]> sampleConic(StepReader reader, Instance conic, double[] start, double[] end,
			int segments) {
		List<double[]> points = new ArrayList<>();
		if (start == null || end == null)
			return line(start, end);

		double[][] frame = placement(reader, reader.instance(reader.attribute(conic, "CONIC", "position")));
		double[] origin = frame[0];
		double[] x = frame[1];
		double[] y = frame[2];

		double radius1;
		double radius2;
		if (conic.is("CIRCLE")) {
			radius1 = radius2 = reader.number(reader.attribute(conic, "CIRCLE", "radius"));
		} else {
			radius1 = reader.number(reader.attribute(conic, "ELLIPSE", "semi_axis_1"));
			radius2 = reader.number(reader.attribute(conic, "ELLIPSE", "semi_axis_2"));
		}

		double startAngle = angle(start, origin, x, y);
		double endAngle = angle(end, origin, x, y);

		double sweep;
		if (coincident(start, end)) {
			// a closed conic: the edge starts and ends on the same seam vertex
			sweep = 2 * Math.PI;
		} else {
			sweep = endAngle - startAngle;
			while (sweep <= -Math.PI)
				sweep += 2 * Math.PI;
			while (sweep > Math.PI)
				sweep -= 2 * Math.PI;
			if (sweep == 0)
				sweep = 2 * Math.PI;
		}

		for (int i = 0; i <= segments; i++) {
			double angle = startAngle + sweep * i / segments;
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			points.add(new double[]{origin[0] + radius1 * cos * x[0] + radius2 * sin * y[0],
					origin[1] + radius1 * cos * x[1] + radius2 * sin * y[1],
					origin[2] + radius1 * cos * x[2] + radius2 * sin * y[2]});
		}
		return points;
	}

	private static double angle(double[] point, double[] origin, double[] x, double[] y) {
		double[] offset = {point[0] - origin[0], point[1] - origin[1], point[2] - origin[2]};
		return Math.atan2(dot(offset, y), dot(offset, x));
	}

	/** True when two points coincide to within a relative rounding epsilon. */
	private static boolean coincident(double[] a, double[] b) {
		double tolerance = 1e-9 * (1 + Math.max(magnitude(a), magnitude(b)));
		return Math.abs(a[0] - b[0]) <= tolerance && Math.abs(a[1] - b[1]) <= tolerance
				&& Math.abs(a[2] - b[2]) <= tolerance;
	}

	private static double magnitude(double[] point) {
		return Math.max(Math.abs(point[0]), Math.max(Math.abs(point[1]), Math.abs(point[2])));
	}

	/** de Boor evaluation of a B-spline curve, clipped to the endpoints. */
	private static List<double[]> sampleBSpline(StepReader reader, Instance curve, double[] start, double[] end,
			int segments) {
		int degree = reader.integer(reader.attribute(curve, "B_SPLINE_CURVE", "degree"));
		List<Value> controlPoints = reader.attribute(curve, "B_SPLINE_CURVE", "control_points_list").list();
		List<Value> multiplicities = reader
				.list(reader.attribute(curve, "B_SPLINE_CURVE_WITH_KNOTS", "knot_multiplicities"));
		List<Value> knots = reader.list(reader.attribute(curve, "B_SPLINE_CURVE_WITH_KNOTS", "knots"));

		List<double[]> controls = new ArrayList<>();
		for (Value reference : controlPoints)
			controls.add(point(reader, reference));

		List<Double> knotVector = new ArrayList<>();
		for (int i = 0; i < multiplicities.size(); i++) {
			int count = (int) multiplicities.get(i).number();
			for (int k = 0; k < count; k++)
				knotVector.add(knots.get(i).number());
		}

		int controlCount = controls.size();
		if (degree < 1 || controlCount <= degree || knotVector.size() < controlCount + degree + 1)
			return line(start, end);

		double first = knotVector.get(degree);
		double last = knotVector.get(controlCount);
		if (!(last > first))
			return line(start, end);

		double from = first;
		double to = last;
		if (start != null && end != null) {
			from = closestParameter(reader, controls, knotVector, degree, first, last, start);
			to = closestParameter(reader, controls, knotVector, degree, first, last, end);
			if (to < from)
				return line(start, end);
		}

		List<double[]> points = new ArrayList<>();
		for (int i = 0; i <= segments; i++) {
			double parameter = from + (to - from) * i / segments;
			points.add(deBoor(controls, knotVector, degree, parameter));
		}
		return points;
	}

	private static double closestParameter(StepReader reader, List<double[]> controls, List<Double> knots, int degree,
			double first, double last, double[] target) {
		double best = first;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i <= 32; i++) {
			double parameter = first + (last - first) * i / 32.0;
			double[] sample = deBoor(controls, knots, degree, parameter);
			double distance = squaredDistance(sample, target);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = parameter;
			}
		}
		return best;
	}

	private static double squaredDistance(double[] a, double[] b) {
		double dx = a[0] - b[0];
		double dy = a[1] - b[1];
		double dz = a[2] - b[2];
		return dx * dx + dy * dy + dz * dz;
	}

	private static double[] deBoor(List<double[]> controls, List<Double> knots, int degree, double parameter) {
		int controlCount = controls.size();
		int span = degree;
		while (span < controlCount - 1 && parameter >= knots.get(span + 1))
			span++;

		double[][] working = new double[degree + 1][];
		for (int j = 0; j <= degree; j++)
			working[j] = controls.get(Math.min(Math.max(span - degree + j, 0), controlCount - 1)).clone();

		for (int r = 1; r <= degree; r++) {
			for (int j = degree; j >= r; j--) {
				int index = span - degree + j;
				double denominator = knots.get(index + degree - r + 1) - knots.get(index);
				double alpha = denominator == 0 ? 0 : (parameter - knots.get(index)) / denominator;
				for (int axis = 0; axis < 3; axis++)
					working[j][axis] = (1 - alpha) * working[j - 1][axis] + alpha * working[j][axis];
			}
		}
		return working[degree];
	}

	/** The endpoints of an edge curve, resolved through its two vertices. */
	static double[][] edgeEndpoints(StepReader reader, Instance edge) {
		double[] start = vertexPoint(reader, reader.attribute(edge, "EDGE_CURVE", "edge_start"));
		double[] end = vertexPoint(reader, reader.attribute(edge, "EDGE_CURVE", "edge_end"));
		return new double[][]{start, end};
	}

	static double[] vertexPoint(StepReader reader, Value vertexReference) {
		Instance vertex = reader.optionalInstance(vertexReference);
		if (vertex == null)
			return null;
		if (!vertex.is("VERTEX_POINT"))
			throw new StepException("Only VERTEX_POINT is supported, found " + vertex.primaryEntity());
		Value geometry = reader.attribute(vertex, "VERTEX_POINT", "vertex_geometry");
		return geometry == null || geometry.kind() != Value.Kind.REFERENCE ? null : point(reader, geometry);
	}
}
