package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.List;

/**
 * Ear clipping for a planar polygon given as an ordered loop of vertex indices.
 * Handles the near-degenerate triangles a tessellated STEP face can produce by
 * falling back to a fan when no ear is found.
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
			if (contains(a, b, c, vertices.get(candidate), normal))
				return false;
		}
		return true;
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
