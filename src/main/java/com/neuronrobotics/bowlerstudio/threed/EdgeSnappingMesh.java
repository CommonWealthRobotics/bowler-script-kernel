package com.neuronrobotics.bowlerstudio.threed;

import java.util.Collections;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.mihosoft.vrl.v3d.CSG;

/** Proximity welding for compatibility export; not a manifold repair or validity guarantee. */
final class EdgeSnappingMesh {
	private static final class Cell {
		private final long x;
		private final long y;
		private final long z;

		private Cell(long x, long y, long z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public long x() {
			return x;
		}

		public long y() {
			return y;
		}

		public long z() {
			return z;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Cell))
				return false;
			Cell that = (Cell) other;
			return x == that.x && y == that.y && z == that.z;
		}

		@Override
		public int hashCode() {
			int result = 0;
			result = 31 * result + Long.hashCode(x);
			result = 31 * result + Long.hashCode(y);
			result = 31 * result + Long.hashCode(z);
			return result;
		}

		@Override
		public String toString() {
			return "Cell[x=" + x + ", y=" + y + ", z=" + z + "]";
		}

	}

	private EdgeSnappingMesh() {
	}

	static CSG prepare(CSG source) {
		double[] points = source.getVertices();
		long[] triangles = source.getTriangles();
		if (points == null || triangles == null || points.length == 0 || points.length % 3 != 0 || triangles.length == 0
				|| triangles.length % 3 != 0)
			throw new IllegalArgumentException("Edge snapping requires an indexed triangle mesh");
		int count = points.length / 3;
		double[] min = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
		double[] max = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		for (int i = 0; i < points.length; i++) {
			if (!Double.isFinite(points[i]))
				throw new IllegalArgumentException("Non-finite snapping coordinate");
			min[i % 3] = Math.min(min[i % 3], points[i]);
			max[i % 3] = Math.max(max[i % 3], points[i]);
		}
		double tolerance = Math.hypot(Math.hypot(max[0] - min[0], max[1] - min[1]), max[2] - min[2]) * 1e-6;
		if (!Double.isFinite(tolerance) || tolerance <= 0)
			throw new IllegalArgumentException("Edge snapping requires a finite positive mesh extent");
		// Compatibility clusters are transitive; their diameter can exceed the pair tolerance.
		int[] parent = new int[count];
		Map<Cell, List<Integer>> cells = new HashMap<>();
		for (int i = 0; i < count; i++) {
			parent[i] = i;
			long x = (long) Math.floor((points[3 * i] - min[0]) / tolerance);
			long y = (long) Math.floor((points[3 * i + 1] - min[1]) / tolerance);
			long z = (long) Math.floor((points[3 * i + 2] - min[2]) / tolerance);
			for (int dx = -1; dx <= 1; dx++)
				for (int dy = -1; dy <= 1; dy++)
					for (int dz = -1; dz <= 1; dz++)
						for (int other : cells.getOrDefault(new Cell(x + dx, y + dy, z + dz), Collections.emptyList())) {
							double distance = Math.hypot(
									Math.hypot(points[3 * i] - points[3 * other],
											points[3 * i + 1] - points[3 * other + 1]),
									points[3 * i + 2] - points[3 * other + 2]);
							if (distance <= tolerance) {
								int a = root(parent, i), b = root(parent, other);
								parent[Math.max(a, b)] = Math.min(a, b);
							}
						}
			cells.computeIfAbsent(new Cell(x, y, z), unused -> new ArrayList<>()).add(i);
		}
		long[] kept = new long[triangles.length];
		int size = 0;
		for (int t = 0; t < triangles.length; t += 3) {
			int[] face = new int[3];
			for (int j = 0; j < 3; j++) {
				long index = triangles[t + j];
				if (index < 0 || index >= count)
					throw new IllegalArgumentException("Snapping triangle index out of range: " + index);
				face[j] = root(parent, (int) index);
			}
			if (face[0] == face[1] || face[1] == face[2] || face[2] == face[0])
				continue;
			for (int vertex : face)
				kept[size++] = vertex;
		}
		if (size == 0)
			throw new IllegalStateException("Edge snapping collapsed every triangle");
		// Compact to referenced representatives; curves and vertices use this single geometry.
		int[] compact = new int[count];
		Arrays.fill(compact, -1);
		double[] output = new double[points.length];
		int used = 0;
		for (int i = 0; i < size; i++) {
			int vertex = (int) kept[i];
			if (compact[vertex] == -1) {
				compact[vertex] = used;
				System.arraycopy(points, vertex * 3, output, used++ * 3, 3);
			}
			kept[i] = compact[vertex];
		}
		CSG result = new CSG(Arrays.copyOf(output, used * 3), Arrays.copyOf(kept, size), source.getColor());
		result.historySync(source);
		return result;
	}

	private static int root(int[] parent, int vertex) {
		while (parent[vertex] != vertex) {
			parent[vertex] = parent[parent[vertex]];
			vertex = parent[vertex];
		}
		return vertex;
	}
}
