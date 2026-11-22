package com.neuronrobotics.bowlerstudio.threed;

import java.util.Objects;

import java.util.Collections;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Indexed planar regions of a closed, consistently oriented native mesh; coordinates are never changed. */
public final class ManifoldBRepPlan {
	public static final double PLANE_TOLERANCE = 1e-7;

	public static final class Point {
		private final double x;
		private final double y;
		private final double z;

		public Point(double x, double y, double z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public double x() {
			return x;
		}

		public double y() {
			return y;
		}

		public double z() {
			return z;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Point))
				return false;
			Point that = (Point) other;
			return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0 && Double.compare(z, that.z) == 0;
		}

		@Override
		public int hashCode() {
			int result = 0;
			result = 31 * result + Double.hashCode(x);
			result = 31 * result + Double.hashCode(y);
			result = 31 * result + Double.hashCode(z);
			return result;
		}

		@Override
		public String toString() {
			return "Point[x=" + x + ", y=" + y + ", z=" + z + "]";
		}

		Point minus(Point b) {
			return new Point(x - b.x, y - b.y, z - b.z);
		}

		double dot(Point b) {
			return x * b.x + y * b.y + z * b.z;
		}

		Point cross(Point b) {
			return new Point(y * b.z - z * b.y, z * b.x - x * b.z, x * b.y - y * b.x);
		}
	}

	/** Loops start with the positive outer loop; remaining loops are negative holes. */
	public static final class Face {
		private final Point origin;
		private final Point normal;
		private final List<List<Integer>> loops;
		private final List<Integer> triangleIndices;

		public Face(Point origin, Point normal, List<List<Integer>> loops, List<Integer> triangleIndices) {
			this.origin = origin;
			this.normal = normal;
			List<List<Integer>> copiedLoops = new ArrayList<>();
			for (List<Integer> loop : loops)
				copiedLoops.add(immutableCopy(loop));
			this.loops = immutableCopy(copiedLoops);
			this.triangleIndices = immutableCopy(triangleIndices);
		}

		public Point origin() {
			return origin;
		}

		public Point normal() {
			return normal;
		}

		public List<List<Integer>> loops() {
			return loops;
		}

		public List<Integer> triangleIndices() {
			return triangleIndices;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Face))
				return false;
			Face that = (Face) other;
			return Objects.equals(origin, that.origin) && Objects.equals(normal, that.normal) && Objects.equals(loops, that.loops) && Objects.equals(triangleIndices, that.triangleIndices);
		}

		@Override
		public int hashCode() {
			int result = 0;
			result = 31 * result + Objects.hashCode(origin);
			result = 31 * result + Objects.hashCode(normal);
			result = 31 * result + Objects.hashCode(loops);
			result = 31 * result + Objects.hashCode(triangleIndices);
			return result;
		}

		@Override
		public String toString() {
			return "Face[origin=" + origin + ", normal=" + normal + ", loops=" + loops + ", triangleIndices=" + triangleIndices + "]";
		}

		

		public int triangleCount() {
			return triangleIndices.size();
		}
	}

	public static final class Plan {
		private final List<Face> faces;
		private final int triangleCount;
		private final int fallbackRegionCount;
		private final int fallbackTriangleCount;

		public Plan(List<Face> faces, int triangleCount, int fallbackRegionCount, int fallbackTriangleCount) {
			this.faces = immutableCopy(faces);
			this.triangleCount = triangleCount;
			this.fallbackRegionCount = fallbackRegionCount;
			this.fallbackTriangleCount = fallbackTriangleCount;
		}

		public List<Face> faces() {
			return faces;
		}

		public int triangleCount() {
			return triangleCount;
		}

		public int fallbackRegionCount() {
			return fallbackRegionCount;
		}

		public int fallbackTriangleCount() {
			return fallbackTriangleCount;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Plan))
				return false;
			Plan that = (Plan) other;
			return Objects.equals(faces, that.faces) && triangleCount == that.triangleCount && fallbackRegionCount == that.fallbackRegionCount && fallbackTriangleCount == that.fallbackTriangleCount;
		}

		@Override
		public int hashCode() {
			int result = 0;
			result = 31 * result + Objects.hashCode(faces);
			result = 31 * result + Integer.hashCode(triangleCount);
			result = 31 * result + Integer.hashCode(fallbackRegionCount);
			result = 31 * result + Integer.hashCode(fallbackTriangleCount);
			return result;
		}

		@Override
		public String toString() {
			return "Plan[faces=" + faces + ", triangleCount=" + triangleCount + ", fallbackRegionCount=" + fallbackRegionCount + ", fallbackTriangleCount=" + fallbackTriangleCount + "]";
		}

		
	}

	private static final class Edge {
		private final int low;
		private final int high;

		private Edge(int low, int high) {
			this.low = low;
			this.high = high;
		}

		public int low() {
			return low;
		}

		public int high() {
			return high;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Edge))
				return false;
			Edge that = (Edge) other;
			return low == that.low && high == that.high;
		}

		@Override
		public int hashCode() {
			int result = 0;
			result = 31 * result + Integer.hashCode(low);
			result = 31 * result + Integer.hashCode(high);
			return result;
		}

		@Override
		public String toString() {
			return "Edge[low=" + low + ", high=" + high + "]";
		}

		static Edge of(int a, int b) {
			return new Edge(Math.min(a, b), Math.max(a, b));
		}
	}
	private static final class HalfEdge {
		private final int triangle;
		private final int corner;
		private final int from;
		private final int to;

		private HalfEdge(int triangle, int corner, int from, int to) {
			this.triangle = triangle;
			this.corner = corner;
			this.from = from;
			this.to = to;
		}

		public int triangle() {
			return triangle;
		}

		public int corner() {
			return corner;
		}

		public int from() {
			return from;
		}

		public int to() {
			return to;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof HalfEdge))
				return false;
			HalfEdge that = (HalfEdge) other;
			return triangle == that.triangle && corner == that.corner && from == that.from && to == that.to;
		}

		@Override
		public int hashCode() {
			int result = 0;
			result = 31 * result + Integer.hashCode(triangle);
			result = 31 * result + Integer.hashCode(corner);
			result = 31 * result + Integer.hashCode(from);
			result = 31 * result + Integer.hashCode(to);
			return result;
		}

		@Override
		public String toString() {
			return "HalfEdge[triangle=" + triangle + ", corner=" + corner + ", from=" + from + ", to=" + to + "]";
		}

	}
	private static final class XY {
		private final double x;
		private final double y;

		private XY(double x, double y) {
			this.x = x;
			this.y = y;
		}

		public double x() {
			return x;
		}

		public double y() {
			return y;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof XY))
				return false;
			XY that = (XY) other;
			return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0;
		}

		@Override
		public int hashCode() {
			int result = 0;
			result = 31 * result + Double.hashCode(x);
			result = 31 * result + Double.hashCode(y);
			return result;
		}

		@Override
		public String toString() {
			return "XY[x=" + x + ", y=" + y + "]";
		}

	}
	private static final class Segment {
		private final int from;
		private final int to;
		private final XY a;
		private final XY b;

		private Segment(int from, int to, XY a, XY b) {
			this.from = from;
			this.to = to;
			this.a = a;
			this.b = b;
		}

		public int from() {
			return from;
		}

		public int to() {
			return to;
		}

		public XY a() {
			return a;
		}

		public XY b() {
			return b;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Segment))
				return false;
			Segment that = (Segment) other;
			return from == that.from && to == that.to && Objects.equals(a, that.a) && Objects.equals(b, that.b);
		}

		@Override
		public int hashCode() {
			int result = 0;
			result = 31 * result + Integer.hashCode(from);
			result = 31 * result + Integer.hashCode(to);
			result = 31 * result + Objects.hashCode(a);
			result = 31 * result + Objects.hashCode(b);
			return result;
		}

		@Override
		public String toString() {
			return "Segment[from=" + from + ", to=" + to + ", a=" + a + ", b=" + b + "]";
		}

		double minX() {
			return Math.min(a.x, b.x);
		}

		double maxX() {
			return Math.max(a.x, b.x);
		}

		double minY() {
			return Math.min(a.y, b.y);
		}

		double maxY() {
			return Math.max(a.y, b.y);
		}
	}

	private static <T> List<T> immutableCopy(List<? extends T> values) {
		List<T> copy = new ArrayList<>(values.size());
		for (T value : values)
			copy.add(Objects.requireNonNull(value));
		return Collections.unmodifiableList(copy);
	}

	private ManifoldBRepPlan() {
	}

	public static Plan plan(double[] positions, long[] triangles) {
		if (positions == null || triangles == null || positions.length % 3 != 0 || triangles.length % 3 != 0)
			throw new IllegalArgumentException("Expected position and triangle triples");
		Point[] points = new Point[positions.length / 3];
		for (int i = 0; i < points.length; i++) {
			for (int c = 0; c < 3; c++)
				if (!Double.isFinite(positions[3 * i + c]))
					throw new IllegalArgumentException("Nonfinite position at vertex " + i);
			points[i] = new Point(positions[3 * i], positions[3 * i + 1], positions[3 * i + 2]);
		}
		int[] indices = new int[triangles.length];
		for (int i = 0; i < indices.length; i++) {
			if (triangles[i] < 0 || triangles[i] >= points.length)
				throw new IllegalArgumentException("Invalid vertex index at corner " + i);
			indices[i] = (int) triangles[i];
		}
		int count = indices.length / 3;
		int[][] neighbors = new int[count][3];
		Point[] normals = new Point[count];
		Map<Edge, HalfEdge> edges = new HashMap<>();
		Set<Edge> paired = new HashSet<>();
		for (int t = 0; t < count; t++) {
			normals[t] = normal(points, indices, t);
			for (int c = 0; c < 3; c++) {
				int a = indices[3 * t + c], b = indices[3 * t + (c + 1) % 3];
				Edge key = Edge.of(a, b);
				if (paired.contains(key))
					throw new IllegalArgumentException("Edge has more than two incident triangles: " + key);
				HalfEdge previous = edges.remove(key);
				if (previous == null)
					edges.put(key, new HalfEdge(t, c, a, b));
				else {
					if (previous.from != b || previous.to != a)
						throw new IllegalArgumentException("Inconsistent edge orientation: " + key);
					neighbors[t][c] = previous.triangle;
					neighbors[previous.triangle][previous.corner] = t;
					paired.add(key);
				}
			}
		}
		if (!edges.isEmpty())
			throw new IllegalArgumentException("Mesh is not closed; unpaired edges: " + edges.size());
		int[] owner = new int[count];
		Arrays.fill(owner, -1);
		List<Face> faces = new ArrayList<>();
		int fallbackRegions = 0, fallbackTriangles = 0;
		// Larger seed triangles reduce normal sensitivity; the chosen plane remains fixed.
		double[] areas = new double[count];
		for (int t = 0; t < count; t++) {
			Point a = points[indices[3 * t]];
			Point cross = points[indices[3 * t + 1]].minus(a).cross(points[indices[3 * t + 2]].minus(a));
			areas[t] = Math.hypot(Math.hypot(cross.x, cross.y), cross.z);
		}
		Integer[] order = new Integer[count];
		for (int t = 0; t < count; t++)
			order[t] = t;
		Arrays.sort(order, Comparator.<Integer>comparingDouble(t -> areas[t]).reversed());
		for (int seed : order) {
			if (owner[seed] != -1)
				continue;
			Point origin = points[indices[3 * seed]], normal = normals[seed];
			List<Integer> region = new ArrayList<>();
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			owner[seed] = seed;
			queue.add(seed);
			while (!queue.isEmpty()) {
				int t = queue.remove();
				region.add(t);
				for (int adjacent : neighbors[t]) {
					if (owner[adjacent] != -1 || normals[adjacent].dot(normal) <= 0)
						continue;
					boolean planar = true;
					for (int c = 0; c < 3; c++)
						planar &= Math
								.abs(points[indices[3 * adjacent + c]].minus(origin).dot(normal)) <= PLANE_TOLERANCE;
					if (planar) {
						owner[adjacent] = seed;
						queue.add(adjacent);
					}
				}
			}
			List<List<Integer>> loops = boundary(region, seed, owner, neighbors, indices, points, origin, normal);
			if (!loops.isEmpty())
				faces.add(new Face(origin, normal, loops, region));
			else {
				fallbackRegions++;
				fallbackTriangles += region.size();
				for (int t : region)
					faces.add(new Face(points[indices[3 * t]], normals[t],
							Collections.singletonList(Arrays.asList(indices[3 * t], indices[3 * t + 1], indices[3 * t + 2])), Collections.singletonList(t)));
			}
		}
		return new Plan(faces, count, fallbackRegions, fallbackTriangles);
	}

	private static Point normal(Point[] points, int[] indices, int t) {
		Point a = points[indices[3 * t]];
		Point n = points[indices[3 * t + 1]].minus(a).cross(points[indices[3 * t + 2]].minus(a));
		double length = Math.hypot(Math.hypot(n.x, n.y), n.z);
		if (!(length > 0) || !Double.isFinite(length))
			throw new IllegalArgumentException("Degenerate or unrepresentable triangle " + t);
		Point unit = new Point(n.x / length, n.y / length, n.z / length);
		for (int c = 1; c < 3; c++) {
			double distance = points[indices[3 * t + c]].minus(a).dot(unit);
			if (!Double.isFinite(distance) || Math.abs(distance) > PLANE_TOLERANCE)
				throw new IllegalArgumentException("Triangle plane exceeds numerical precision at triangle " + t);
		}
		return unit;
	}

	private static List<List<Integer>> boundary(List<Integer> region, int seed, int[] owner, int[][] neighbors,
			int[] indices, Point[] points, Point origin, Point normal) {
		Map<Integer, Integer> next = new HashMap<>();
		Set<Integer> incoming = new HashSet<>();
		for (int t : region)
			for (int c = 0; c < 3; c++) {
				if (owner[neighbors[t][c]] == seed)
					continue;
				int a = indices[3 * t + c], b = indices[3 * t + (c + 1) % 3];
				if (next.put(a, b) != null || !incoming.add(b))
					return Collections.emptyList();
			}
		if (!next.keySet().equals(incoming))
			return Collections.emptyList();
		Point axis = Math.abs(normal.x) < 0.8 ? new Point(1, 0, 0) : new Point(0, 1, 0);
		Point u = normal.cross(axis), v = normal.cross(u);
		Map<Integer, XY> projected = new HashMap<>();
		for (int vertex : next.keySet()) {
			Point p = points[vertex].minus(origin);
			double x = p.dot(u), y = p.dot(v);
			if (!Double.isFinite(x) || !Double.isFinite(y))
				return Collections.emptyList();
			projected.put(vertex, new XY(x, y));
		}
		List<Segment> segments = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : next.entrySet())
			segments.add(new Segment(e.getKey(), e.getValue(), projected.get(e.getKey()), projected.get(e.getValue())));
		if (intersects(segments))
			return Collections.emptyList();
		List<List<Integer>> loops = new ArrayList<>();
		int outers = 0;
		while (!next.isEmpty()) {
			int start = next.keySet().iterator().next(), vertex = start;
			List<Integer> loop = new ArrayList<>();
			double area = 0, compensation = 0;
			XY local = projected.get(start);
			do {
				loop.add(vertex);
				int to = next.remove(vertex);
				XY a = projected.get(vertex), b = projected.get(to);
				double term = (a.x - local.x) * (b.y - local.y) - (a.y - local.y) * (b.x - local.x) - compensation;
				double sum = area + term;
				compensation = (sum - area) - term;
				area = sum;
				vertex = to;
			} while (vertex != start);
			if (loop.size() < 3 || area == 0 || !Double.isFinite(area))
				return Collections.emptyList();
			if (area > 0) {
				outers++;
				loops.add(0, loop);
			} else
				loops.add(loop);
		}
		if (outers != 1)
			return Collections.emptyList();
		// Disjoint simple cycles with one positive outer still require containment.
		for (int i = 1; i < loops.size(); i++) {
			XY sample = projected.get(loops.get(i).get(0));
			if (!inside(sample, loops.get(0), projected))
				return Collections.emptyList();
			for (int j = 1; j < i; j++)
				if (inside(sample, loops.get(j), projected)
						|| inside(projected.get(loops.get(j).get(0)), loops.get(i), projected))
					return Collections.emptyList();
		}
		return loops;
	}

	private static boolean inside(XY p, List<Integer> loop, Map<Integer, XY> points) {
		boolean inside = false;
		for (int i = 0, j = loop.size() - 1; i < loop.size(); j = i++) {
			XY a = points.get(loop.get(i)), b = points.get(loop.get(j));
			if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x)
				inside = !inside;
		}
		return inside;
	}

	private static boolean intersects(List<Segment> segments) {
		segments.sort(Comparator.comparingDouble(Segment::minX));
		PriorityQueue<Segment> ends = new PriorityQueue<>(Comparator.comparingDouble(Segment::maxX));
		Set<Segment> active = new HashSet<>();
		for (Segment s : segments) {
			if (s.a.equals(s.b))
				return true;
			while (!ends.isEmpty() && ends.peek().maxX() < s.minX())
				active.remove(ends.remove());
			for (Segment t : active) {
				if (t.maxY() < s.minY() || s.maxY() < t.minY())
					continue;
				if (s.from == t.to || s.to == t.from) {
					XY joint = s.from == t.to ? s.a : s.b;
					XY a = s.from == t.to ? s.b : s.a, b = s.from == t.to ? t.a : t.b;
					if (orient(joint, a, b) == 0
							&& (a.x - joint.x) * (b.x - joint.x) + (a.y - joint.y) * (b.y - joint.y) > 0)
						return true;
					continue;
				}
				if (opposite(orient(s.a, s.b, t.a), orient(s.a, s.b, t.b))
						&& opposite(orient(t.a, t.b, s.a), orient(t.a, t.b, s.b)))
					return true;
			}
			active.add(s);
			ends.add(s);
		}
		return false;
	}

	private static double orient(XY a, XY b, XY c) {
		double left = (b.x - a.x) * (c.y - a.y), right = (b.y - a.y) * (c.x - a.x);
		double determinant = left - right;
		if (Double.isFinite(determinant) && Math.abs(determinant) > 8 * Math.ulp(Math.abs(left) + Math.abs(right)))
			return determinant;
		// Resolve cancellation with exact arithmetic on the projected double coordinates.
		BigDecimal ax = new BigDecimal(a.x), ay = new BigDecimal(a.y);
		return new BigDecimal(b.x).subtract(ax).multiply(new BigDecimal(c.y).subtract(ay))
				.subtract(new BigDecimal(b.y).subtract(ay).multiply(new BigDecimal(c.x).subtract(ax))).signum();
	}

	private static boolean opposite(double a, double b) {
		return a == 0 || b == 0 || (a > 0) != (b > 0);
	}
}
