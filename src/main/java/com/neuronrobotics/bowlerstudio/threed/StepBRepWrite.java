package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/**
 * Writes a mesh as BRep topology, so a reader shows a solid with selectable
 * faces rather than a cloud of lines.
 *
 * <p>
 * The coplanar, connected triangles of the mesh become one planar face; the
 * boundary of that face becomes a {@code FACE_OUTER_BOUND} plus a
 * {@code FACE_BOUND} per hole, each an {@code EDGE_LOOP} of {@code EDGE_CURVE}s
 * through shared {@code VERTEX_POINT}s; and the faces close a shell. A mesh that
 * does not close yields a surface model, which is still valid.
 *
 * <p>
 * Three properties keep the result a solid rather than a pile of slivers. A face
 * holds triangles that lie in one plane, not merely triangles with parallel
 * normals: two triangles with the same normal but different offsets make the face
 * they share curved, which a reader rejects. Every triangle is kept, however thin
 * it is: a dropped triangle leaves its neighbours' edges one sided, which opens
 * the shell, and a reader fills the gap with a face of no volume. And vertices
 * that a mesh stores apart but that describe one point are unified first, because
 * the edges between them are shorter than the file's own precision and a reader
 * cannot close a wire over them.
 */
final class StepBRepWrite {

	/**
	 * How far outside a face's plane a triangle may reach, as a fraction of the
	 * model's own size. A mesh stores rounded doubles, so triangles that are
	 * coplanar by design differ by a few ulps, and the model's extent scales that
	 * error; a ten millionth of the extent is far below any feature a mesh
	 * resolves, yet well above the rounding.
	 */
	private static final double PLANE_TOLERANCE = 1e-7;

	/**
	 * The distance below which two of the mesh's vertices describe one point, as a
	 * fraction of the model's size. A mesh a modelling kernel cut out of its own
	 * geometry stores a point that lies on another triangle as a vertex of its own,
	 * tens of nanometres away, and the edge between the two is shorter than the
	 * file's own length precision; a reader cannot place a vertex that close to an
	 * edge, so the wires that use it never close. For the test mesh the longest
	 * such edge is a five hundred thousandth of its extent and the shortest edge it
	 * genuinely resolves is a fifty thousandth, so this sits an order of magnitude
	 * above the one and an order below the other.
	 */
	private static final double WELD_TOLERANCE = 5e-9;

	private StepBRepWrite() {
	}

	/**
	 * A representation item list for the mesh: the representation's own placement
	 * and, when there is geometry, the solid it bounds.
	 */
	static List<Long> items(StepEmitter emitter, CSG csg, String name) {
		long placement = placement(emitter, name);
		long body = solid(emitter, csg, name);
		if (body == 0)
			return List.of(placement);
		return List.of(placement, body);
	}

	/** A solid or surface model over the mesh, or 0 when the mesh has no faces. */
	static long solid(StepEmitter emitter, CSG csg, String name) {
		double[] vertices = csg.getVertices();
		long[] sourceTriangles = csg.getTriangles();
		// a part with no triangles has nothing to describe, and a STEP file cannot
		// carry an empty shape representation
		if (vertices == null || sourceTriangles == null || sourceTriangles.length < 3)
			return 0;

		Mesh mesh = new Mesh(vertices, sourceTriangles);
		if (mesh.faces.isEmpty())
			return 0;
		List<Long> faces = new ArrayList<>();
		for (List<Integer> triangles : mesh.faces)
			faces.add(mesh.face(emitter, triangles, name));
		if (faces.isEmpty())
			return 0;

		String shellKind = mesh.isClosed() ? "CLOSED_SHELL" : "OPEN_SHELL";
		long shell = emitter.add(shellKind + "(''," + references(faces) + ")");
		if (mesh.isClosed())
			return emitter.add("MANIFOLD_SOLID_BREP(" + quote(name) + ",#" + shell + ")");
		return emitter.add("SHELL_BASED_SURFACE_MODEL(" + quote(name) + ",(#" + shell + "))");
	}

	private static long placement(StepEmitter emitter, String name) {
		long origin = emitter.add("CARTESIAN_POINT('',(0.,0.,0.))");
		long axis = emitter.add("DIRECTION('',(0.,0.,1.))");
		long direction = emitter.add("DIRECTION('',(1.,0.,0.))");
		return emitter.add("AXIS2_PLACEMENT_3D(" + quote(name) + ",#" + origin + ",#" + axis + ",#" + direction + ")");
	}

	/** The mesh, grouped into coplanar faces and sharing vertices and edges. */
	private static final class Mesh {
		private final double[] points;
		private final List<int[]> triangles = new ArrayList<>();
		private final List<List<Integer>> faces = new ArrayList<>();
		/** Undirected edge to the triangles that use it, for growing a face. */
		private final Map<Long, List<Integer>> byEdge = new HashMap<>();
		private final Map<Long, Long> edges = new HashMap<>();
		private final Map<Integer, Long> vertices = new HashMap<>();
		private final double planeTolerance;
		private final double weldTolerance;
		/** A mesh vertex to the vertex that represents it. */
		private final Map<Integer, Integer> weldMap = new HashMap<>();
		private final Map<Long, List<Integer>> weldGrid = new HashMap<>();

		private Mesh(double[] points, long[] sourceTriangles) {
			this.points = points;
			this.planeTolerance = PLANE_TOLERANCE * diagonal(points);
			this.weldTolerance = WELD_TOLERANCE * diagonal(points);
			if (sourceTriangles != null)
				for (int t = 0; t + 2 < sourceTriangles.length; t += 3)
					addTriangle((int) sourceTriangles[t], (int) sourceTriangles[t + 1], (int) sourceTriangles[t + 2]);
			index();
			group();
		}

		// --- the mesh's own vertices ---

		/**
		 * Takes the mesh's triangles, welding the vertices that describe one point
		 * first. A triangle that welding collapses to a line is dropped: it bounds no
		 * area, so no face needs it.
		 */
		private void addTriangle(int a, int b, int c) {
			a = weld(a);
			b = weld(b);
			c = weld(c);
			if (a == b || b == c || c == a)
				return;
			int[] triangle = new int[]{a, b, c};
			if (normal(triangle) == null)
				return; // a triangle of no area has no plane
			triangles.add(triangle);
		}

		/**
		 * The representative of a mesh vertex, welding any vertices that lie within
		 * the weld distance of one another. The representative keeps its own
		 * coordinates, so a welded mesh still describes the source's surface: the
		 * vertices it unifies are the same point to the precision a STEP file can
		 * carry.
		 */
		private int weld(int vertex) {
			Integer existing = weldMap.get(vertex);
			if (existing != null)
				return existing;

			double[] p = point(vertex);
			long x = (long) Math.floor(p[0] / weldTolerance);
			long y = (long) Math.floor(p[1] / weldTolerance);
			long z = (long) Math.floor(p[2] / weldTolerance);

			// a point within the weld distance lies in this cell or one next to it
			for (long dx = -1; dx <= 1; dx++)
				for (long dy = -1; dy <= 1; dy++)
					for (long dz = -1; dz <= 1; dz++)
						for (int candidate : weldGrid.getOrDefault(cell(x + dx, y + dy, z + dz), List.of()))
							if (distance(point(candidate), p) <= weldTolerance) {
								weldMap.put(vertex, candidate);
								return candidate;
							}

			weldGrid.computeIfAbsent(cell(x, y, z), unused -> new ArrayList<>()).add(vertex);
			weldMap.put(vertex, vertex);
			return vertex;
		}

		private static long cell(long x, long y, long z) {
			return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
		}

		private static double distance(double[] a, double[] b) {
			double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		private double[] point(int vertex) {
			return new double[]{points[vertex * 3], points[vertex * 3 + 1], points[vertex * 3 + 2]};
		}

		private void index() {
			for (int index = 0; index < triangles.size(); index++) {
				int[] triangle = triangles.get(index);
				for (int i = 0; i < 3; i++)
					byEdge.computeIfAbsent(key(triangle[i], triangle[(i + 1) % 3]), unused -> new ArrayList<>())
							.add(index);
			}
		}

		// --- grouping into faces ---

		/** Grows one face from every triangle, absorbing the coplanar neighbours. */
		private void group() {
			boolean[] used = new boolean[triangles.size()];
			for (int start = 0; start < triangles.size(); start++) {
				if (used[start])
					continue;

				Vector3d normal = normal(triangles.get(start));
				double[] origin = point(triangles.get(start)[0]);
				List<Integer> face = new ArrayList<>();
				face.add(start);
				used[start] = true;

				List<Integer> frontier = new ArrayList<>();
				frontier.add(start);
				while (!frontier.isEmpty()) {
					int current = frontier.remove(frontier.size() - 1);
					int[] triangle = triangles.get(current);
					for (int i = 0; i < 3; i++)
						for (int index : byEdge.get(key(triangle[i], triangle[(i + 1) % 3]))) {
							if (used[index])
								continue;
							// a face is planar, so its triangles must face the same way
							// and lie in the same plane; parallel normals alone would let
							// a triangle from a parallel face join and bend it
							Vector3d candidate = normal(triangles.get(index));
							if (candidate == null || candidate.dot(normal) <= 0)
								continue;
							if (!coplanar(triangles.get(index), origin, normal))
								continue;
							used[index] = true;
							face.add(index);
							frontier.add(index);
						}
				}
				faces.add(face);
			}
		}

		/** True when every vertex of the triangle lies in the face's plane. */
		private boolean coplanar(int[] triangle, double[] origin, Vector3d normal) {
			for (int vertex : triangle) {
				double[] p = point(vertex);
				double offset = (p[0] - origin[0]) * normal.x + (p[1] - origin[1]) * normal.y
						+ (p[2] - origin[2]) * normal.z;
				if (Math.abs(offset) > planeTolerance)
					return false;
			}
			return true;
		}

		// --- face and loop emission ---

		private long face(StepEmitter emitter, List<Integer> face, String name) {
			int first = triangles.get(face.get(0))[0];
			Vector3d normal = normal(triangles.get(face.get(0)));
			// the plane must pass through the face, not the origin, or the face does
			// not lie on it and the model has no volume
			long plane = plane(emitter, point(first), normal, name);

			List<List<Integer>> loops = loops(face, normal);
			// the loops are largest first, so the first is the outer bound and any
			// others are holes the outer one encloses
			List<Long> bounds = new ArrayList<>();
			for (int index = 0; index < loops.size(); index++) {
				long loopId = edgeLoop(emitter, loops.get(index));
				String kind = index == 0 ? "FACE_OUTER_BOUND" : "FACE_BOUND";
				bounds.add(emitter.add(kind + "('',#" + loopId + ",.T.)"));
			}
			return emitter.add("ADVANCED_FACE(" + quote(name) + "," + references(bounds) + ",#" + plane + ",.T.)");
		}

		private long plane(StepEmitter emitter, double[] origin, Vector3d normal, String name) {
			long location = emitter.add("CARTESIAN_POINT(''," + coords(origin) + ")");
			long axis = emitter.add("DIRECTION(''," + vector(normal) + ")");
			long ref = emitter.add("DIRECTION(''," + vector(perpendicularTo(normal)) + ")");
			long placement = emitter.add("AXIS2_PLACEMENT_3D('',#" + location + ",#" + axis + ",#" + ref + ")");
			return emitter.add("PLANE(" + quote(name) + ",#" + placement + ")");
		}

		/**
		 * The boundary loops of a face, largest first. The outer loop and each hole
		 * are separate cycles, told apart by the area they enclose.
		 *
		 * <p>
		 * A face's boundary is read from its triangles' own indices: within one face
		 * a mesh edge has one triangle on each side, so the two traversals of it
		 * cancel and what remains is the outline. A mesh may still leave a boundary
		 * edge running through a boundary vertex, where the neighbouring face is
		 * triangulated without a vertex on that edge, so each edge is split at the
		 * boundary vertices lying on it before the outline is walked. At a vertex the
		 * outline passes through twice, such as the tip of a facet that meets the
		 * rest of the face only at that point, the walk takes the tightest turn, so
		 * the two passes become two loops rather than one that crosses itself.
		 */
		private List<List<Integer>> loops(List<Integer> face, Vector3d normal) {
			List<int[]> boundary = split(boundary(face));
			Map<Integer, List<int[]>> byStart = new LinkedHashMap<>();
			for (int[] edge : boundary)
				byStart.computeIfAbsent(edge[0], unused -> new ArrayList<>()).add(edge);

			List<List<Integer>> loops = new ArrayList<>();
			Map<Long, Boolean> visited = new HashMap<>();
			for (int[] edge : boundary) {
				if (Boolean.TRUE.equals(visited.get(key(edge[0], edge[1]))))
					continue;

				List<Integer> loop = new ArrayList<>();
				int[] at = edge;
				while (at != null && !Boolean.TRUE.equals(visited.get(key(at[0], at[1])))) {
					visited.put(key(at[0], at[1]), Boolean.TRUE);
					loop.add(at[0]);
					at = next(byStart, visited, at, normal);
				}
				if (bounds(loop, normal))
					loops.add(loop);
			}

			loops.sort((a, b) -> Double.compare(Math.abs(area(b, normal)), Math.abs(area(a, normal))));
			return loops;
		}

		/**
		 * True when a loop encloses an area, so it is a bound of the face. A face made
		 * of thin facets can pinch to a point, and the outline then contains a cycle
		 * that runs out to the pinch and back; such a cycle is as wide as the plane
		 * tolerance at most, which its area over its perimeter measures.
		 */
		private boolean bounds(List<Integer> loop, Vector3d normal) {
			if (loop.size() < 3)
				return false;

			double perimeter = 0;
			for (int i = 0; i < loop.size(); i++)
				perimeter += distance(point(loop.get(i)), point(loop.get((i + 1) % loop.size())));
			if (perimeter == 0)
				return false;
			return Math.abs(area(loop, normal)) / perimeter > planeTolerance;
		}

		/**
		 * Splits every boundary edge at the boundary vertices lying on it, in order,
		 * so an edge that another face leaves a vertex in the middle of meets the
		 * outline at that vertex rather than passing through it.
		 */
		private List<int[]> split(List<int[]> boundary) {
			// a boundary vertex is one an edge ends at; only those can be junctions
			Set<Integer> boundaryVertices = new LinkedHashSet<>();
			for (int[] edge : boundary) {
				boundaryVertices.add(edge[0]);
				boundaryVertices.add(edge[1]);
			}

			List<int[]> split = new ArrayList<>();
			for (int[] edge : boundary)
				for (int[] piece : split(edge, boundaryVertices))
					split.add(piece);
			return split;
		}

		/** The edge cut at every boundary vertex between its endpoints, in order. */
		private List<int[]> split(int[] edge, Set<Integer> boundaryVertices) {
			double[] a = point(edge[0]);
			double[] b = point(edge[1]);
			double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
			double lengthSquared = dx * dx + dy * dy + dz * dz;

			List<double[]> cuts = new ArrayList<>();
			for (int vertex : boundaryVertices) {
				if (vertex == edge[0] || vertex == edge[1])
					continue;
				double[] p = point(vertex);
				double t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy + (p[2] - a[2]) * dz) / lengthSquared;
				if (t <= 1e-9 || t >= 1 - 1e-9)
					continue;
				double x = a[0] + dx * t, y = a[1] + dy * t, z = a[2] + dz * t;
				double off = Math.pow(p[0] - x, 2) + Math.pow(p[1] - y, 2) + Math.pow(p[2] - z, 2);
				if (off > weldTolerance * weldTolerance)
					continue;
				cuts.add(new double[]{t, vertex});
			}

			if (cuts.isEmpty())
				return List.of(edge);

			cuts.sort((left, right) -> Double.compare(left[0], right[0]));
			List<int[]> pieces = new ArrayList<>();
			int from = edge[0];
			for (double[] cut : cuts) {
				int vertex = (int) cut[1];
				if (vertex != from) {
					pieces.add(new int[]{from, vertex});
					from = vertex;
				}
			}
			if (from != edge[1])
				pieces.add(new int[]{from, edge[1]});
			return pieces;
		}

		/**
		 * The unvisited boundary edge leaving a vertex that turns tightest from the
		 * one just walked. Turning tightest keeps the outline on the same side of
		 * itself, so a vertex the outline reaches twice starts a new loop instead of
		 * being crossed.
		 */
		private int[] next(Map<Integer, List<int[]>> byStart, Map<Long, Boolean> visited, int[] at, Vector3d normal) {
			double[] incoming = direction(point(at[1]), point(at[0]));
			int[] best = null;
			double bestAngle = 0;
			for (int[] candidate : byStart.getOrDefault(at[1], List.of())) {
				if (Boolean.TRUE.equals(visited.get(key(candidate[0], candidate[1]))))
					continue;

				double angle = turn(incoming, direction(point(at[1]), point(candidate[1])), normal);
				if (best == null || angle < bestAngle) {
					best = candidate;
					bestAngle = angle;
				}
			}
			return best;
		}

		/** The unit vector from one point to another. */
		private static double[] direction(double[] from, double[] to) {
			double dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			return length == 0 ? new double[]{0, 0, 0} : new double[]{dx / length, dy / length, dz / length};
		}

		/** The angle from one direction to another, measured anticlockwise about the normal. */
		private static double turn(double[] from, double[] to, Vector3d normal) {
			double dot = from[0] * to[0] + from[1] * to[1] + from[2] * to[2];
			double cx = from[1] * to[2] - from[2] * to[1];
			double cy = from[2] * to[0] - from[0] * to[2];
			double cz = from[0] * to[1] - from[1] * to[0];
			double sine = cx * normal.x + cy * normal.y + cz * normal.z;
			double angle = Math.atan2(sine, dot);
			return angle <= 0 ? angle + 2 * Math.PI : angle;
		}

		/** The directed edges used by exactly one of the face's triangles. */
		private List<int[]> boundary(List<Integer> face) {
			Map<Long, int[]> once = new LinkedHashMap<>();
			Map<Long, Boolean> repeated = new HashMap<>();
			for (int index : face)
				collectEdges(triangles.get(index), once, repeated);

			List<int[]> boundary = new ArrayList<>();
			for (Map.Entry<Long, int[]> entry : once.entrySet())
				if (!Boolean.TRUE.equals(repeated.get(entry.getKey())))
					boundary.add(entry.getValue());
			return boundary;
		}

		/** Records the triangle's directed edges, dropping any seen twice. */
		private void collectEdges(int[] triangle, Map<Long, int[]> once, Map<Long, Boolean> repeated) {
			int[][] pairs = {{triangle[0], triangle[1]}, {triangle[1], triangle[2]}, {triangle[2], triangle[0]}};
			for (int[] pair : pairs) {
				long key = key(pair[0], pair[1]);
				if (Boolean.TRUE.equals(repeated.get(key)))
					continue;
				if (once.containsKey(key))
					repeated.put(key, Boolean.TRUE);
				else
					once.put(key, pair);
			}
		}

		/** The loop as oriented edges, each pointing the way the loop walks. */
		private long edgeLoop(StepEmitter emitter, List<Integer> loop) {
			List<Long> oriented = new ArrayList<>();
			for (int i = 0; i < loop.size(); i++) {
				int from = loop.get(i);
				int to = loop.get((i + 1) % loop.size());
				long edge = edge(emitter, from, to);
				boolean forward = from < to;
				oriented.add(emitter.add("ORIENTED_EDGE('',*,*,#" + edge + "," + (forward ? ".T." : ".F.") + ")"));
			}
			return emitter.add("EDGE_LOOP(''," + references(oriented) + ")");
		}

		/** Twice the signed area of a loop seen along the normal; its sign is the winding. */
		private double area(List<Integer> loop, Vector3d normal) {
			Vector3d u = perpendicularTo(normal);
			Vector3d v = normal.cross(u);
			double area = 0;
			for (int i = 0; i < loop.size(); i++) {
				double[] a = point(loop.get(i));
				double[] b = point(loop.get((i + 1) % loop.size()));
				area += (a[0] * u.x + a[1] * u.y + a[2] * u.z) * (b[0] * v.x + b[1] * v.y + b[2] * v.z)
						- (b[0] * u.x + b[1] * u.y + b[2] * u.z) * (a[0] * v.x + a[1] * v.y + a[2] * v.z);
			}
			return area;
		}

		// --- shared vertices and edges ---

		/** The EDGE_CURVE between two vertices, which every face using it shares. */
		private long edge(StepEmitter emitter, int a, int b) {
			long key = key(a, b);
			Long existing = edges.get(key);
			if (existing != null)
				return existing;

			int low = Math.min(a, b);
			int high = Math.max(a, b);
			long start = vertex(emitter, low);
			long end = vertex(emitter, high);
			long curve = line(emitter, point(low), point(high));

			long edge = emitter.add("EDGE_CURVE('',#" + start + ",#" + end + ",#" + curve + ",.T.)");
			edges.put(key, edge);
			return edge;
		}

		private long vertex(StepEmitter emitter, int vertex) {
			Long existing = vertices.get(vertex);
			if (existing != null)
				return existing;

			long cartesian = emitter.add("CARTESIAN_POINT(''," + coords(point(vertex)) + ")");
			long id = emitter.add("VERTEX_POINT('',#" + cartesian + ")");
			vertices.put(vertex, id);
			return id;
		}

		private long line(StepEmitter emitter, double[] a, double[] b) {
			double dx = b[0] - a[0];
			double dy = b[1] - a[1];
			double dz = b[2] - a[2];
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

			long origin = emitter.add("CARTESIAN_POINT(''," + coords(a) + ")");
			long direction = emitter.add(
					"DIRECTION('',(" + real(dx / length) + "," + real(dy / length) + "," + real(dz / length) + "))");
			long vector = emitter.add("VECTOR('',#" + direction + "," + real(length) + ")");
			return emitter.add("LINE('',#" + origin + ",#" + vector + ")");
		}

		// --- mesh facts ---

		private boolean isClosed() {
			Map<Long, Integer> counts = new HashMap<>();
			for (int[] triangle : triangles)
				for (int i = 0; i < 3; i++)
					counts.merge(key(triangle[i], triangle[(i + 1) % 3]), 1, Integer::sum);
			if (counts.isEmpty())
				return false;
			for (int count : counts.values())
				if (count != 2)
					return false;
			return true;
		}

		/** The triangle's unit normal, or null when it has no area to face. */
		private Vector3d normal(int[] triangle) {
			double[] a = point(triangle[0]);
			double[] b = point(triangle[1]);
			double[] c = point(triangle[2]);
			double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
			double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
			// only a triangle that collapses to a point or a line has no normal; a
			// thin triangle still faces somewhere, and dropping it would open the
			// shell around it
			if (length == 0)
				return null;
			return new Vector3d(nx / length, ny / length, nz / length);
		}
	}

	// --- keys and formatting ---

	/** The diagonal of the mesh's bounding box, its natural scale. */
	private static double diagonal(double[] points) {
		if (points.length < 3)
			return 1.0;
		double minX = points[0], maxX = points[0];
		double minY = points[1], maxY = points[1];
		double minZ = points[2], maxZ = points[2];
		for (int i = 3; i + 2 < points.length; i += 3) {
			minX = Math.min(minX, points[i]);
			maxX = Math.max(maxX, points[i]);
			minY = Math.min(minY, points[i + 1]);
			maxY = Math.max(maxY, points[i + 1]);
			minZ = Math.min(minZ, points[i + 2]);
			maxZ = Math.max(maxZ, points[i + 2]);
		}
		double dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
		double diagonal = Math.sqrt(dx * dx + dy * dy + dz * dz);
		return diagonal == 0 ? 1.0 : diagonal;
	}

	private static long key(int a, int b) {
		int low = Math.min(a, b);
		int high = Math.max(a, b);
		return ((long) low << 32) | (high & 0xffffffffL);
	}

	private static String references(List<Long> ids) {
		StringBuilder references = new StringBuilder("(");
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0)
				references.append(',');
			references.append('#').append(ids.get(i));
		}
		return references.append(')').toString();
	}

	private static String coords(double[] p) {
		return "(" + real(p[0]) + "," + real(p[1]) + "," + real(p[2]) + ")";
	}

	private static String vector(Vector3d v) {
		return "(" + real(v.x) + "," + real(v.y) + "," + real(v.z) + ")";
	}

	/** Any unit vector perpendicular to the given one, for a plane's reference axis. */
	private static Vector3d perpendicularTo(Vector3d normal) {
		Vector3d candidate = Math.abs(normal.x) < 0.9 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0);
		return normal.cross(candidate).normalized();
	}

	private static String real(double value) {
		if (!Double.isFinite(value))
			throw new IllegalArgumentException("Non-finite STEP value " + value);
		return Double.toString(value);
	}

	private static String quote(String value) {
		StringBuilder escaped = new StringBuilder("'");
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character == '\'')
				escaped.append("''");
			else if (character == '\\')
				escaped.append("\\\\");
			else if (character >= 0x20)
				escaped.append(character);
		}
		return escaped.append('\'').toString();
	}
}
