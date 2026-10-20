package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Ear clipping for a planar polygon given as an ordered loop of vertex indices.
 * Handles the near-degenerate triangles a tessellated STEP face can produce by
 * falling back to a fan when no ear is found.
 *
 * <p>
 * A face may also be an annulus, an outer loop with holes inside it. Ear clipping
 * needs a simple polygon, so each hole is first spliced into the outer loop along
 * a bridge that crosses nothing, the standard keyhole construction, and the
 * result is clipped as usual.
 */
final class Triangulator {

	private Triangulator() {
	}

	/** Indices into {@code vertices}, three per triangle. */
	static List<int[]> triangulate(List<Integer> loop, List<double[]> vertices) {
		List<int[]> triangles = new ArrayList<>();
		if (loop.size() < 3)
			return triangles;

		double[] normal = normal(loop, vertices);
		List<Integer> remaining = new ArrayList<>(loop);

		int guard = remaining.size() * remaining.size();
		while (remaining.size() > 3 && guard-- > 0) {
			boolean clipped = false;
			for (int i = 0; i < remaining.size(); i++) {
				int previous = remaining.get((i - 1 + remaining.size()) % remaining.size());
				int current = remaining.get(i);
				int next = remaining.get((i + 1) % remaining.size());

				if (!isEar(previous, current, next, remaining, vertices, normal))
					continue;

				triangles.add(new int[]{previous, current, next});
				remaining.remove(i);
				clipped = true;
				break;
			}
			if (!clipped)
				break; // self intersecting or non planar: fall through to the fan
		}

		if (remaining.size() == 3)
			triangles.add(new int[]{remaining.get(0), remaining.get(1), remaining.get(2)});
		else
			fan(loop, triangles);

		return triangles;
	}

	/**
	 * Triangulates the region an outer loop bounds, less the region each hole
	 * bounds. Every hole is spliced into the outer loop along a bridge that
	 * crosses no loop, which turns the region into one simple polygon. A hole with
	 * no clear bridge to reach it is filled in instead, which is what a region
	 * with no triangulation available would have to do anyway.
	 */
	static List<int[]> triangulate(List<Integer> outer, List<List<Integer>> holes, List<double[]> vertices) {
		List<Integer> merged = new ArrayList<>(outer);
		double[] normal = normal(outer, vertices);
		for (List<Integer> hole : holes) {
			List<Integer> spliced = splice(merged, hole, vertices, normal);
			if (spliced == null)
				return triangulate(outer, vertices);
			merged = spliced;
		}
		return triangulate(merged, vertices);
	}

	/**
	 * The outer loop with the hole spliced into it: walk the outer loop to the
	 * bridge's outer end, cross to the hole, walk the hole back to that point, and
	 * cross back. The two crossings repeat a vertex, so the result traces the
	 * region as one loop.
	 */
	private static List<Integer> splice(List<Integer> outer, List<Integer> hole, List<double[]> vertices,
			double[] normal) {
		if (hole.size() < 3)
			return outer;

		List<int[]> candidates = new ArrayList<>();
		for (int i = 0; i < outer.size(); i++)
			for (int j = 0; j < hole.size(); j++)
				candidates.add(new int[]{i, j});
		candidates.sort(Comparator
				.comparingDouble(pair -> distance(vertices.get(outer.get(pair[0])), vertices.get(hole.get(pair[1])))));

		for (int[] candidate : candidates) {
			int at = candidate[0];
			int from = candidate[1];
			if (!clear(outer, at, hole, from, vertices, normal))
				continue;

			List<Integer> merged = new ArrayList<>(outer.size() + hole.size() + 2);
			merged.addAll(outer.subList(0, at + 1));
			for (int i = 0; i < hole.size(); i++)
				merged.add(hole.get((from + i) % hole.size()));
			merged.add(hole.get(from));
			merged.add(outer.get(at));
			merged.addAll(outer.subList(at + 1, outer.size()));
			return merged;
		}
		return null;
	}

	/** True when the bridge between the two loops crosses or touches neither. */
	private static boolean clear(List<Integer> outer, int at, List<Integer> hole, int from, List<double[]> vertices,
			double[] normal) {
		int start = outer.get(at);
		int end = hole.get(from);
		double[] a = vertices.get(start);
		double[] b = vertices.get(end);

		for (List<Integer> loop : List.of(outer, hole))
			for (int i = 0; i < loop.size(); i++) {
				int c = loop.get(i);
				int d = loop.get((i + 1) % loop.size());
				if (c == start || c == end || d == start || d == end)
					continue;
				double[] p = vertices.get(c);
				double[] q = vertices.get(d);
				if (crosses(a, b, p, q, normal))
					return false;
				if (onSegment(a, b, p) || onSegment(a, b, q))
					return false; // the bridge would run through a vertex
			}
		return true;
	}

	/** True when two segments cross, sharing no endpoint. */
	private static boolean crosses(double[] a, double[] b, double[] c, double[] d, double[] normal) {
		double abC = side(a, b, c, normal);
		double abD = side(a, b, d, normal);
		double cdA = side(c, d, a, normal);
		double cdB = side(c, d, b, normal);
		return (abC > 0 && abD < 0 || abC < 0 && abD > 0) && (cdA > 0 && cdB < 0 || cdA < 0 && cdB > 0);
	}

	/** Which side of the line a to b the point lies on, in the loops' plane. */
	private static double side(double[] a, double[] b, double[] p, double[] normal) {
		double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		double vx = p[0] - a[0], vy = p[1] - a[1], vz = p[2] - a[2];
		double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
		return cx * normal[0] + cy * normal[1] + cz * normal[2];
	}

	/** True when the point lies on the segment, endpoints included. */
	private static boolean onSegment(double[] a, double[] b, double[] p) {
		double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		double vx = p[0] - a[0], vy = p[1] - a[1], vz = p[2] - a[2];
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		double length = Math.sqrt(ux * ux + uy * uy + uz * uz);
		if (length == 0)
			return false;
		if (Math.sqrt(nx * nx + ny * ny + nz * nz) / length > 1e-9 * (1 + length))
			return false;
		double t = (vx * ux + vy * uy + vz * uz) / (length * length);
		return t >= 0 && t <= 1;
	}

	private static double distance(double[] a, double[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
		return dx * dx + dy * dy + dz * dz;
	}

	private static void fan(List<Integer> loop, List<int[]> triangles) {
		for (int i = 1; i + 1 < loop.size(); i++)
			triangles.add(new int[]{loop.get(0), loop.get(i), loop.get(i + 1)});
	}

	private static boolean isEar(int previous, int current, int next, List<Integer> remaining, List<double[]> vertices,
			double[] normal) {
		double[] a = vertices.get(previous);
		double[] b = vertices.get(current);
		double[] c = vertices.get(next);

		if (cross(a, b, c, normal) <= 0)
			return false;

		for (int candidate : remaining) {
			if (candidate == previous || candidate == current || candidate == next)
				continue;
			double[] point = vertices.get(candidate);
			// a point coincident with a corner is not inside the ear; a spliced
			// hole puts such a point there on purpose
			if (coincident(point, a) || coincident(point, b) || coincident(point, c))
				continue;
			if (contains(a, b, c, point, normal))
				return false;
		}
		return true;
	}

	private static boolean coincident(double[] a, double[] b) {
		double scale = 1 + Math.max(magnitude(a), magnitude(b));
		return Math.abs(a[0] - b[0]) <= 1e-9 * scale && Math.abs(a[1] - b[1]) <= 1e-9 * scale
				&& Math.abs(a[2] - b[2]) <= 1e-9 * scale;
	}

	private static double magnitude(double[] p) {
		return Math.max(Math.abs(p[0]), Math.max(Math.abs(p[1]), Math.abs(p[2])));
	}

	/** Newell's method, so the normal is well defined for any planar loop. */
	private static double[] normal(List<Integer> loop, List<double[]> vertices) {
		double nx = 0;
		double ny = 0;
		double nz = 0;
		for (int i = 0; i < loop.size(); i++) {
			double[] a = vertices.get(loop.get(i));
			double[] b = vertices.get(loop.get((i + 1) % loop.size()));
			nx += (a[1] - b[1]) * (a[2] + b[2]);
			ny += (a[2] - b[2]) * (a[0] + b[0]);
			nz += (a[0] - b[0]) * (a[1] + b[1]);
		}
		double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length == 0)
			return new double[]{0, 0, 1};
		return new double[]{nx / length, ny / length, nz / length};
	}

	/** Signed area of the triangle about the polygon normal. */
	private static double cross(double[] a, double[] b, double[] c, double[] normal) {
		double ux = b[0] - a[0];
		double uy = b[1] - a[1];
		double uz = b[2] - a[2];
		double vx = c[0] - a[0];
		double vy = c[1] - a[1];
		double vz = c[2] - a[2];
		double cx = uy * vz - uz * vy;
		double cy = uz * vx - ux * vz;
		double cz = ux * vy - uy * vx;
		return cx * normal[0] + cy * normal[1] + cz * normal[2];
	}

	/** Point-in-triangle test projected onto the plane with the given normal. */
	private static boolean contains(double[] a, double[] b, double[] c, double[] point, double[] normal) {
		double d1 = cross(a, b, point, normal);
		double d2 = cross(b, c, point, normal);
		double d3 = cross(c, a, point, normal);
		return (d1 >= 0 && d2 >= 0 && d3 >= 0) || (d1 <= 0 && d2 <= 0 && d3 <= 0);
	}
}
