package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

public final class FeatureEdgeExtractor {

	public static final class Edge {
		public final Vector3d a;
		public final Vector3d b;

		private Edge(Vector3d a, Vector3d b) {
			this.a = a;
			this.b = b;
		}
	}

	private static final class CandidateEdge {
		private final Vector3d a;
		private final Vector3d b;
		private final List<Vector3d> normals = new ArrayList<>();

		private CandidateEdge(Vector3d a, Vector3d b) {
			this.a = a;
			this.b = b;
		}
	}

	private static final class VertexKey {
		private final long x;
		private final long y;
		private final long z;

		private VertexKey(long x, long y, long z) {
			this.x = x;
			this.y = y;
			this.z = z;
		}

		@Override
		public boolean equals(Object object) {
			if (this == object)
				return true;

			if (!(object instanceof VertexKey))
				return false;

			VertexKey other = (VertexKey) object;

			return x == other.x && y == other.y && z == other.z;
		}

		@Override
		public int hashCode() {
			int result = Long.hashCode(x);
			result = 31 * result + Long.hashCode(y);
			result = 31 * result + Long.hashCode(z);
			return result;
		}
	}

	private static final class BoundaryEdge {
		private final Vector3d a;
		private final Vector3d b;
		private final Vector3d normal;

		private BoundaryEdge(Vector3d a, Vector3d b, Vector3d normal) {
			this.a = a;
			this.b = b;
			this.normal = normal;
		}
	}

	private static final class SplitPoint {
		private final double t;
		private final Vector3d point;

		private SplitPoint(double t, Vector3d point) {
			this.t = t;
			this.point = point;
		}
	}

	private static final class StitchedSegment {
		private final Vector3d a;
		private final Vector3d b;
		private final List<Vector3d> normals = new ArrayList<>();

		private StitchedSegment(Vector3d a, Vector3d b) {
			this.a = a;
			this.b = b;
		}
	}

	private FeatureEdgeExtractor() {
	}

	public static List<Edge> extract(CSG csg, double angleDegrees, double topologyRepairTolerance,
			double stitchTolerance) {

		List<Edge> visibleEdges = new ArrayList<>();

		long[] triangles = csg.getTriangles();
		int triangleCount = (int) csg.getTriCount();

		if (triangles == null || triangleCount == 0)
			return visibleEdges;

		double repairTolerance = Math.max(0.0, topologyRepairTolerance);

		int vertexCount = (int) csg.getVertCount();
		int[] vertexRemap = null;

		double[] topologyX = null;
		double[] topologyY = null;
		double[] topologyZ = null;

		double[] featureX = null;
		double[] featureY = null;
		double[] featureZ = null;

		if (repairTolerance > 0.0 && vertexCount > 0) {
			vertexRemap = new int[vertexCount];

			topologyX = new double[vertexCount];
			topologyY = new double[vertexCount];
			topologyZ = new double[vertexCount];

			featureX = new double[vertexCount];
			featureY = new double[vertexCount];
			featureZ = new double[vertexCount];

			Map<VertexKey, Integer> canonicalVertices = new HashMap<>();

			for (int vertex = 0; vertex < vertexCount; vertex++) {
				double x = csg.getVertex_X(vertex);
				double y = csg.getVertex_Y(vertex);
				double z = csg.getVertex_Z(vertex);

				long qx = Math.round(x / repairTolerance);
				long qy = Math.round(y / repairTolerance);
				long qz = Math.round(z / repairTolerance);

				VertexKey key = new VertexKey(qx, qy, qz);
				Integer repairedIndex = canonicalVertices.get(key);

				if (repairedIndex == null) {
					repairedIndex = canonicalVertices.size();
					canonicalVertices.put(key, repairedIndex);

					topologyX[repairedIndex] = qx * repairTolerance;
					topologyY[repairedIndex] = qy * repairTolerance;
					topologyZ[repairedIndex] = qz * repairTolerance;

					featureX[repairedIndex] = x;
					featureY[repairedIndex] = y;
					featureZ[repairedIndex] = z;
				}

				vertexRemap[vertex] = repairedIndex;
			}
		}

		Map<Long, CandidateEdge> edgeMap = new LinkedHashMap<>();

		for (int triangle = 0; triangle < triangleCount; triangle++) {
			int sourceI0 = (int) triangles[triangle * 3];
			int sourceI1 = (int) triangles[triangle * 3 + 1];
			int sourceI2 = (int) triangles[triangle * 3 + 2];

			int i0 = vertexRemap == null ? sourceI0 : vertexRemap[sourceI0];
			int i1 = vertexRemap == null ? sourceI1 : vertexRemap[sourceI1];
			int i2 = vertexRemap == null ? sourceI2 : vertexRemap[sourceI2];

			if (i0 == i1 || i1 == i2 || i2 == i0)
				continue;

			if (vertexRemap != null) {
				double ux = topologyX[i1] - topologyX[i0];
				double uy = topologyY[i1] - topologyY[i0];
				double uz = topologyZ[i1] - topologyZ[i0];

				double vx = topologyX[i2] - topologyX[i0];
				double vy = topologyY[i2] - topologyY[i0];
				double vz = topologyZ[i2] - topologyZ[i0];

				double nx = uy * vz - uz * vy;
				double ny = uz * vx - ux * vz;
				double nz = ux * vy - uy * vx;

				if (Math.sqrt(nx * nx + ny * ny + nz * nz) < 1e-12)
					continue;
			}

			double ax = csg.getVertex_X(sourceI0);
			double ay = csg.getVertex_Y(sourceI0);
			double az = csg.getVertex_Z(sourceI0);

			double bx = csg.getVertex_X(sourceI1);
			double by = csg.getVertex_Y(sourceI1);
			double bz = csg.getVertex_Z(sourceI1);

			double cx = csg.getVertex_X(sourceI2);
			double cy = csg.getVertex_Y(sourceI2);
			double cz = csg.getVertex_Z(sourceI2);

			double ux = bx - ax;
			double uy = by - ay;
			double uz = bz - az;

			double vx = cx - ax;
			double vy = cy - ay;
			double vz = cz - az;

			double nx = uy * vz - uz * vy;
			double ny = uz * vx - ux * vz;
			double nz = ux * vy - uy * vx;

			double length = Math.sqrt(nx * nx + ny * ny + nz * nz);

			if (length < 1e-12)
				continue;

			Vector3d normal = new Vector3d(nx / length, ny / length, nz / length);

			int[] indices = {i0, i1, i2};

			for (int edgeIndex = 0; edgeIndex < 3; edgeIndex++) {
				int ia = indices[edgeIndex];
				int ib = indices[(edgeIndex + 1) % 3];

				int low = Math.min(ia, ib);
				int high = Math.max(ia, ib);

				long edgeKey = ((long) low << 32) | (high & 0xffffffffL);

				CandidateEdge edge = edgeMap.get(edgeKey);

				if (edge == null) {
					double lowX = vertexRemap == null ? csg.getVertex_X(low) : featureX[low];
					double lowY = vertexRemap == null ? csg.getVertex_Y(low) : featureY[low];
					double lowZ = vertexRemap == null ? csg.getVertex_Z(low) : featureZ[low];

					double highX = vertexRemap == null ? csg.getVertex_X(high) : featureX[high];
					double highY = vertexRemap == null ? csg.getVertex_Y(high) : featureY[high];
					double highZ = vertexRemap == null ? csg.getVertex_Z(high) : featureZ[high];

					edge = new CandidateEdge(new Vector3d(lowX, lowY, lowZ), new Vector3d(highX, highY, highZ));

					edgeMap.put(edgeKey, edge);
				}

				edge.normals.add(normal);
			}
		}

		double clampedAngle = Math.max(0.0, Math.min(180.0, angleDegrees));

		double cosThreshold = Math.cos(Math.toRadians(clampedAngle));

		double tolerance = Math.max(1e-9, stitchTolerance);

		List<BoundaryEdge> boundaries = new ArrayList<>();

		for (CandidateEdge edge : edgeMap.values()) {
			if (edge.normals.size() == 1) {
				boundaries.add(new BoundaryEdge(edge.a, edge.b, edge.normals.get(0)));
				continue;
			}

			if (isFeature(edge.normals, cosThreshold))
				visibleEdges.add(new Edge(edge.a, edge.b));
		}

		visibleEdges.addAll(stitchBoundaries(boundaries, tolerance, cosThreshold));

		return visibleEdges;
	}

	private static boolean isFeature(List<Vector3d> normals, double cosThreshold) {

		for (int i = 0; i < normals.size(); i++) {
			for (int j = i + 1; j < normals.size(); j++) {
				double dot = normals.get(i).dot(normals.get(j));

				double planeDot = Math.max(-1.0, Math.min(1.0, dot));

				if (planeDot < cosThreshold)
					return true;
			}
		}

		return false;
	}

	private static List<Edge> stitchBoundaries(List<BoundaryEdge> boundaries, double tolerance, double cosThreshold) {

		List<Edge> result = new ArrayList<>();

		if (boundaries.isEmpty())
			return result;

		Map<String, Vector3d> endpointMap = new LinkedHashMap<>();

		for (BoundaryEdge edge : boundaries) {
			endpointMap.putIfAbsent(pointKey(edge.a, tolerance), edge.a);

			endpointMap.putIfAbsent(pointKey(edge.b, tolerance), edge.b);
		}

		List<Vector3d> endpoints = new ArrayList<>(endpointMap.values());

		List<Vector3d> endpointsByX = new ArrayList<>(endpoints);

		List<Vector3d> endpointsByY = new ArrayList<>(endpoints);

		List<Vector3d> endpointsByZ = new ArrayList<>(endpoints);

		endpointsByX.sort(Comparator.comparingDouble(point -> point.x));

		endpointsByY.sort(Comparator.comparingDouble(point -> point.y));

		endpointsByZ.sort(Comparator.comparingDouble(point -> point.z));

		Map<String, StitchedSegment> segments = new LinkedHashMap<>();

		for (BoundaryEdge edge : boundaries) {
			List<SplitPoint> splits = new ArrayList<>();

			splits.add(new SplitPoint(0.0, edge.a));
			splits.add(new SplitPoint(1.0, edge.b));

			double minX = Math.min(edge.a.x, edge.b.x) - tolerance;
			double maxX = Math.max(edge.a.x, edge.b.x) + tolerance;

			double minY = Math.min(edge.a.y, edge.b.y) - tolerance;
			double maxY = Math.max(edge.a.y, edge.b.y) + tolerance;

			double minZ = Math.min(edge.a.z, edge.b.z) - tolerance;
			double maxZ = Math.max(edge.a.z, edge.b.z) + tolerance;

			int candidateStart = lowerBound(endpointsByX, 0, minX);

			int candidateEnd = upperBound(endpointsByX, 0, maxX);

			List<Vector3d> candidates = endpointsByX;

			int yStart = lowerBound(endpointsByY, 1, minY);

			int yEnd = upperBound(endpointsByY, 1, maxY);

			if (yEnd - yStart < candidateEnd - candidateStart) {
				candidateStart = yStart;
				candidateEnd = yEnd;
				candidates = endpointsByY;
			}

			int zStart = lowerBound(endpointsByZ, 2, minZ);

			int zEnd = upperBound(endpointsByZ, 2, maxZ);

			if (zEnd - zStart < candidateEnd - candidateStart) {
				candidateStart = zStart;
				candidateEnd = zEnd;
				candidates = endpointsByZ;
			}

			for (int candidateIndex = candidateStart; candidateIndex < candidateEnd; candidateIndex++) {

				Vector3d candidate = candidates.get(candidateIndex);

				if (candidate.x < minX || candidate.x > maxX || candidate.y < minY || candidate.y > maxY
						|| candidate.z < minZ || candidate.z > maxZ)
					continue;

				double t = segmentParameter(candidate, edge.a, edge.b);

				if (t <= 1e-9 || t >= 1.0 - 1e-9)
					continue;

				if (distanceToSegment(candidate, edge.a, edge.b) <= tolerance)
					splits.add(new SplitPoint(t, candidate));
			}

			splits.sort(Comparator.comparingDouble(split -> split.t));

			List<SplitPoint> unique = new ArrayList<>();

			for (SplitPoint split : splits) {
				if (unique.isEmpty() || distance(split.point, unique.get(unique.size() - 1).point) > tolerance)
					unique.add(split);
			}

			for (int i = 0; i + 1 < unique.size(); i++) {
				Vector3d a = unique.get(i).point;
				Vector3d b = unique.get(i + 1).point;

				if (distance(a, b) <= tolerance)
					continue;

				String key = segmentKey(a, b, tolerance);

				StitchedSegment segment = segments.get(key);

				if (segment == null) {
					segment = new StitchedSegment(a, b);
					segments.put(key, segment);
				}

				segment.normals.add(edge.normal);
			}
		}

		for (StitchedSegment segment : segments.values()) {
			if (isFeature(segment.normals, cosThreshold))
				result.add(new Edge(segment.a, segment.b));
		}

		return result;
	}

	private static long quantize(double value, double tolerance) {

		return Math.round(value / tolerance);
	}

	private static String pointKey(Vector3d point, double tolerance) {

		return quantize(point.x, tolerance) + "," + quantize(point.y, tolerance) + "," + quantize(point.z, tolerance);
	}

	private static String segmentKey(Vector3d a, Vector3d b, double tolerance) {

		String ka = pointKey(a, tolerance);
		String kb = pointKey(b, tolerance);

		return ka.compareTo(kb) <= 0 ? ka + "|" + kb : kb + "|" + ka;
	}

	private static double coordinate(Vector3d point, int axis) {

		if (axis == 0)
			return point.x;

		if (axis == 1)
			return point.y;

		return point.z;
	}

	private static int lowerBound(List<Vector3d> points, int axis, double value) {

		int low = 0;
		int high = points.size();

		while (low < high) {
			int mid = (low + high) >>> 1;

			if (coordinate(points.get(mid), axis) < value)
				low = mid + 1;
			else
				high = mid;
		}

		return low;
	}

	private static int upperBound(List<Vector3d> points, int axis, double value) {

		int low = 0;
		int high = points.size();

		while (low < high) {
			int mid = (low + high) >>> 1;

			if (coordinate(points.get(mid), axis) <= value)
				low = mid + 1;
			else
				high = mid;
		}

		return low;
	}

	private static double segmentParameter(Vector3d point, Vector3d a, Vector3d b) {

		double abX = b.x - a.x;
		double abY = b.y - a.y;
		double abZ = b.z - a.z;

		double lengthSquared = abX * abX + abY * abY + abZ * abZ;

		if (lengthSquared < 1e-18)
			return 0.0;

		return ((point.x - a.x) * abX + (point.y - a.y) * abY + (point.z - a.z) * abZ) / lengthSquared;
	}

	private static double distanceToSegment(Vector3d point, Vector3d a, Vector3d b) {

		double t = Math.max(0.0, Math.min(1.0, segmentParameter(point, a, b)));

		double x = a.x + (b.x - a.x) * t;
		double y = a.y + (b.y - a.y) * t;
		double z = a.z + (b.z - a.z) * t;

		double dx = point.x - x;
		double dy = point.y - y;
		double dz = point.z - z;

		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static double distance(Vector3d a, Vector3d b) {

		double dx = a.x - b.x;
		double dy = a.y - b.y;
		double dz = a.z - b.z;

		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
