package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/**
 * Holds the written faces to the model's feature edges.
 *
 * <p>
 * A feature edge is where the model itself bends: the edge between two faces that
 * are not the same plane, which a reader must draw as a border. Every other border
 * of a written face is a crack — two pieces of one plane written as two faces
 * because the grouping let them apart. A reader shows that as a seam through what
 * is one flat surface, and a CAD package makes the user select and edit the pieces
 * separately.
 *
 * <p>
 * The check is relative rather than absolute, because a planar face cannot always
 * reach a feature edge. Two faces are one plane when the model's own tessellation
 * bends less than the angle parameter across their shared border — but a bend of
 * half a degree over a fifty millimetre face still lifts the far corner by a
 * quarter of a millimetre, which no plane a reader accepts can carry. Fusing such
 * a pair would write a face off its own plane, which is a worse defect than the
 * seam. So a border is an error only when both faces could have been one:
 *
 * <ul>
 * <li>the model has no feature along it at the given angle, and
 * <li>one plane holds every point of both faces, within the export's plane
 * tolerance.
 * </ul>
 *
 * <p>
 * The first alone is the model's own resolution limit and is reported by
 * {@link #curvedBorders} so a caller can see it; the second alone would flag a
 * genuine corner. Only both together mean the export fractured a face.
 */
public final class StepFaceBorders {

	/**
	 * How far the model may bend across a border before that border is a feature of
	 * the model rather than a crack in a face.
	 *
	 * <p>
	 * Two degrees is the scale of the mesh's own tessellation noise: a plane written
	 * by a boolean kernel holds to well under a tenth of that, while a fillet or a
	 * fold is an order of magnitude above it.
	 */
	public static final double DEFAULT_ANGLE_DEGREES = 2.0;

	/** One face border that is a crack within a plane. */
	public static final class Fracture {
		/** The border's first end. */
		public final double[] from;
		/** The border's second end. */
		public final double[] to;
		/** How far the pair of faces strays from one plane, in model units. */
		public final double offset;

		Fracture(double[] from, double[] to, double offset) {
			this.from = from;
			this.to = to;
			this.offset = offset;
		}

		@Override
		public String toString() {
			return "crack from (" + from[0] + "," + from[1] + "," + from[2] + ") to (" + to[0] + "," + to[1] + ","
					+ to[2] + "), " + offset + " off one plane";
		}
	}

	private StepFaceBorders() {
	}

	/**
	 * The borders of the faces written for the part that no feature edge carries and
	 * that one plane holds: the faces the export fractured. Empty is the correct
	 * answer.
	 */
	public static List<Fracture> fractures(CSG csg, double angleDegrees) {
		List<StepFace> faces = StepBRepWrite.faces(csg);
		List<FeatureEdgeExtractor.Edge> features = FeatureEdgeExtractor.extract(csg, angleDegrees,
				STEPFile.DEFAULT_TOPOLOGY_TOLERANCE, STEPFile.DEFAULT_STITCH_TOLERANCE);
		return fractures(faces, features);
	}

	/**
	 * See {@link #fractures(CSG, double)}; the plane budget is the tighter of the two
	 * faces' own, so a pair the writer declined to fuse is never reported as one it
	 * should have.
	 */
	public static List<Fracture> fractures(List<StepFace> faces, List<FeatureEdgeExtractor.Edge> features) {
		List<Fracture> fractures = new ArrayList<>();
		for (Border border : borders(faces)) {
			if (carried(border, features))
				continue;
			double offset = planeOffset(border);
			if (offset <= planeBudget(border))
				fractures.add(new Fracture(border.from, border.to, offset));
		}
		return fractures;
	}

	/** The budget a fused face of the pair would have been held to. */
	private static double planeBudget(Border border) {
		double budget = Double.POSITIVE_INFINITY;
		for (StepFace face : border.pair)
			budget = Math.min(budget, face.planeTolerance);
		return budget;
	}

	/**
	 * The borders no feature edge carries, whether or not one plane would hold the
	 * pair: the model's own curvature below the angle parameter, which a planar face
	 * cannot follow. Not a defect, but the reason a face may stop where it does.
	 */
	public static List<Fracture> curvedBorders(List<StepFace> faces, List<FeatureEdgeExtractor.Edge> features) {
		List<Fracture> curved = new ArrayList<>();
		for (Border border : borders(faces))
			if (!carried(border, features))
				curved.add(new Fracture(border.from, border.to, planeOffset(border)));
		return curved;
	}

	// --- borders ---

	/** A border segment of one written face, with the face across it. */
	private static final class Border {
		private final List<StepFace> pair = new ArrayList<>();
		private final double[] from;
		private final double[] to;

		private Border(double[] from, double[] to) {
			this.from = from;
			this.to = to;
		}
	}

	/**
	 * The boundary segments each two faces share.
	 *
	 * <p>
	 * A segment is matched by its ends' coordinates, which is exact because the writer
	 * writes both faces' loops through the same junction points: a T-junction is
	 * expanded on both sides of the border before either loop is written, so the two
	 * faces name the same corners even where the mesh's own indices differ.
	 */
	private static List<Border> borders(List<StepFace> faces) {
		Map<String, Border> borders = new LinkedHashMap<>();
		for (StepFace face : faces)
			for (List<double[]> loop : face.loops)
				for (int at = 0; at < loop.size(); at++) {
					double[] from = loop.get(at);
					double[] to = loop.get((at + 1) % loop.size());
					if (samePoint(from, to))
						continue;
					Border border = borders.computeIfAbsent(key(from, to), unused -> new Border(from, to));
					if (!border.pair.contains(face))
						border.pair.add(face);
				}
		List<Border> shared = new ArrayList<>();
		for (Border border : borders.values())
			// a segment one face names is on the shell, not between two faces; that is
			// what an open shell is, and it is reported where the shell is written
			if (border.pair.size() == 2)
				shared.add(border);
		return shared;
	}

	/** The undirected segment key: the two ends in a fixed order, to a coordinate's precision. */
	private static String key(double[] from, double[] to) {
		String first = from[0] + "," + from[1] + "," + from[2];
		String second = to[0] + "," + to[1] + "," + to[2];
		return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
	}

	/** True when two points are the same corner to the resolution the writer works to. */
	private static boolean samePoint(double[] a, double[] b) {
		return a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
	}

	// --- the two tests ---

	/**
	 * How far the pair of faces across the border strays from one plane, in model
	 * units.
	 *
	 * <p>
	 * The measure is the one the writer's own grouping uses, so the gate and the
	 * writer cannot disagree: a plane is fitted to every point of both faces, and
	 * the worst distance from it is the pair's offset. Two faces each of which fits
	 * the other's plane can still fail this, when the surface bends gradually
	 * between their far corners — two nearly parallel planes a tenth of a
	 * millimetre apart. The writer declines to fuse that, and correctly: it is the
	 * surface curving, not a face in pieces. Taking the union's fit rather than each
	 * face's own is what tells the two apart.
	 *
	 * <p>
	 * The offset is reported so a caller can see how close the pair came.
	 */
	private static double planeOffset(Border border) {
		if (border.pair.size() != 2)
			return Double.POSITIVE_INFINITY;
		// the writer's own fit of the fusion: area-weighted normals summed, centroid
		// area-weighted, then every point of the union measured against that plane
		Vector3d normalSum = new Vector3d(0, 0, 0);
		double[] weightedCentre = {0, 0, 0};
		double totalArea = 0;
		for (StepFace face : border.pair) {
			normalSum = normalSum.plus(face.normalSum);
			totalArea += face.area;
			for (int axis = 0; axis < 3; axis++)
				weightedCentre[axis] += face.area * face.origin[axis];
		}
		if (normalSum.magnitude() == 0 || totalArea == 0)
			return Double.POSITIVE_INFINITY;
		Vector3d unit = normalSum.normalized();
		double[] centre = {weightedCentre[0] / totalArea, weightedCentre[1] / totalArea, weightedCentre[2] / totalArea};
		double worst = 0;
		for (StepFace face : border.pair)
			for (double[] point : face.points)
				worst = Math.max(worst, Math.abs((point[0] - centre[0]) * unit.x + (point[1] - centre[1]) * unit.y
						+ (point[2] - centre[2]) * unit.z));
		return worst;
	}

	/**
	 * True when the model has a feature along the border, so the border is where the
	 * part bends rather than a crack in a face.
	 *
	 * <p>
	 * A feature edge can be longer than the border segment (the model's edge was not
	 * split where the face's was) or shorter (it was), so the test is coverage: the
	 * border must be spanned by collinear feature segments. A segment is collinear
	 * when each of its ends lies on the border's line and the two directions agree,
	 * within the stitch tolerance the edges were extracted with. That tolerance also
	 * lets a feature run on past the border's ends, because the ends are the corners
	 * the two faces share and a feature there is the same feature.
	 */
	private static boolean carried(Border border, List<FeatureEdgeExtractor.Edge> features) {
		double[] direction = unit(border.from, border.to);
		double length = distance(border.from, border.to);
		if (length == 0)
			return false;
		double tolerance = coverageTolerance(length);

		List<double[]> spans = new ArrayList<>();
		for (FeatureEdgeExtractor.Edge edge : features) {
			double[] from = {edge.a.x, edge.a.y, edge.a.z};
			double[] to = {edge.b.x, edge.b.y, edge.b.z};
			if (Math.abs(dot(direction, unit(from, to))) < 1 - 1e-9)
				continue;
			if (perpendicular(from, border.from, direction) > tolerance
					|| perpendicular(to, border.from, direction) > tolerance)
				continue;
			double fromAt = dot(
					new double[]{from[0] - border.from[0], from[1] - border.from[1], from[2] - border.from[2]},
					direction);
			double toAt = dot(new double[]{to[0] - border.from[0], to[1] - border.from[1], to[2] - border.from[2]},
					direction);
			spans.add(new double[]{Math.min(fromAt, toAt), Math.max(fromAt, toAt)});
		}
		spans.sort(Comparator.comparingDouble(span -> span[0]));

		// walk the spans in order: each must reach the covered end, and together they
		// must reach the far end of the border
		double covered = 0;
		for (double[] span : spans) {
			if (span[0] - tolerance > covered)
				return false;
			covered = Math.max(covered, span[1]);
			if (covered >= length - tolerance)
				return true;
		}
		return false;
	}

	/**
	 * The distance within which a feature edge counts as lying along a border: the
	 * stitch tolerance the feature edges were built with, and never more than the
	 * border's own length, so a short border cannot claim a distant feature.
	 */
	private static double coverageTolerance(double length) {
		return Math.min(Math.max(STEPFile.DEFAULT_STITCH_TOLERANCE, 1e-9), length / 2);
	}

	// --- geometry ---

	private static double perpendicular(double[] point, double[] origin, double[] direction) {
		double[] delta = {point[0] - origin[0], point[1] - origin[1], point[2] - origin[2]};
		double along = dot(delta, direction);
		double[] residual = {delta[0] - along * direction[0], delta[1] - along * direction[1],
				delta[2] - along * direction[2]};
		return Math.sqrt(dot(residual, residual));
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static double[] unit(double[] from, double[] to) {
		double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		return length == 0 ? new double[]{0, 0, 0} : new double[]{dx / length, dy / length, dz / length};
	}

	private static double distance(double[] from, double[] to) {
		double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
