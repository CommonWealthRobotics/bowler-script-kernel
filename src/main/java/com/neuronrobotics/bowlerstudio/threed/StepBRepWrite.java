package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.TickToc;

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
 * Four properties keep the result a solid rather than a pile of slivers. A face
 * holds triangles that lie in one plane, not merely triangles with parallel
 * normals: two triangles with the same normal but different offsets make the face
 * they share curved, which a reader rejects. Every triangle is kept, however thin
 * it is: a dropped triangle leaves its neighbours' edges one sided, which opens
 * the shell, and a reader fills the gap with a face of no volume. Vertices that a
 * mesh stores apart but that describe one point are unified first, because the
 * edges between them are shorter than the file's own precision and a reader
 * cannot close a wire over them. And a vertex left in the middle of a neighbour's
 * edge is split into that edge, so the two faces across it name the same vertices
 * and can pair; without that the shell is open by index even though it is closed
 * in space.
 */
public final class StepBRepWrite {

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
	 * fraction of the model's size. This is deliberately tiny: merging vertices that
	 * are merely close would collapse the thin facets a mesh legitimately has. What
	 * a kernel's mesh actually stores is a vertex lying *on* another triangle's edge,
	 * which {@link #T_JUNCTION_TOLERANCE} handles; the edges between such a vertex
	 * and the edge's endpoints are the ones shorter than the file's own precision.
	 */
	private static final double WELD_TOLERANCE = 5e-9;

	/**
	 * How far from an edge a vertex may sit and still belong on it, as a fraction of
	 * the model's size, for T-junction repair. A kernel that cuts a solid out of its
	 * own geometry leaves a vertex a little off the neighbouring triangle, so the
	 * edge it faces is one long segment against two short ones and the two faces'
	 * wires never pair. This sits well below the shortest edge a mesh resolves and
	 * well above the rounding of a stored double.
	 */
	private static final double T_JUNCTION_TOLERANCE = 1e-6;

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

		Log.debug("StepBRepWrite.solid: start for " + name);
		TickToc.tic("build mesh " + name);
		Mesh mesh = new Mesh(vertices, sourceTriangles);
		if (mesh.faces.isEmpty())
			return 0;
		TickToc.tic("emit faces " + name);
		List<Long> faces = new ArrayList<>();
		/** The loops actually written, so the shell can be checked as emitted. */
		List<List<List<Integer>>> written = new ArrayList<>();
		int emitted = 0;
		int fellBack = 0;
		for (List<Integer> triangles : mesh.faces) {
			// a coplanar group can hold several disconnected regions, and a STEP face
			// can only bound one: its first loop is the outline and the rest are holes
			// cut out of it. So each region is written as a face of its own.
			List<List<Integer>> regions = mesh.regions(triangles);
			for (List<Integer> region : regions)
				if (mesh.representsExactly(region)) {
					List<List<Integer>> loops = mesh.loops(region);
					written.add(loops);
					faces.add(mesh.face(emitter, region, loops, name));
					emitted++;
				} else {
					// this region's outline cannot be triangulated back into the surface
					// it holds, so writing it would leave a hole there. A CAD package
					// shows the same shape either way, so the exact per-triangle faces
					// are written instead, which cover it by construction. Only this
					// region falls back, so the rest of the group stays stitched.
					for (int index : region) {
						written.add(List.of(mesh.triangleLoop(index)));
						faces.add(mesh.triangleFace(emitter, index, name));
					}
					fellBack++;
				}
		}
		if (fellBack > 0)
			Log.debug("StepBRepWrite.solid: " + fellBack + " region(s) of " + mesh.faces.size()
					+ " coplanar group(s) could not be bounded exactly, written per triangle");
		Log.debug("StepBRepWrite.solid: emitted " + emitted + " faces for " + name);
		if (faces.isEmpty())
			return 0;

		// the shell is a solid only when the topology actually written is watertight,
		// so ask the loops that were emitted rather than the mesh they came from: the
		// two differ when a region fell back to per-triangle faces
		boolean closed = isWatertight(written);
		Log.debug("StepBRepWrite.solid: " + name + " is " + (closed ? "closed" : "open") + " ("
				+ unpairedEdges(written) + " unpaired edge(s) in the written loops)");
		String shellKind = closed ? "CLOSED_SHELL" : "OPEN_SHELL";
		long shell = emitter.add(shellKind + "(''," + references(faces) + ")");
		if (closed)
			return emitter.add("MANIFOLD_SOLID_BREP(" + quote(name) + ",#" + shell + ")");
		// an open shell is a defect for a watertight mesh, so say what opened it
		System.err.println("StepBRepWrite: " + name + " is written as an open shell: " + mesh.describeEdges()
				+ " (source " + (sourceTriangles.length / 3) + " triangles, welded to " + mesh.triangles.size() + ")");
		return emitter.add("SHELL_BASED_SURFACE_MODEL(" + quote(name) + ",(#" + shell + "))");
	}

	/**
	 * True when the loops written close a surface: every directed edge appears once
	 * and the same edge reversed appears once, so each is shared by exactly two
	 * faces.
	 */
	private static boolean isWatertight(List<List<List<Integer>>> faces) {
		return unpairedEdges(faces) == 0 && !faces.isEmpty();
	}

	/** How many directed edges of the written loops lack their opposite. */
	private static int unpairedEdges(List<List<List<Integer>>> faces) {
		Map<Long, Integer> directions = new HashMap<>();
		for (List<List<Integer>> loops : faces)
			for (List<Integer> loop : loops)
				for (int i = 0; i < loop.size(); i++)
					directions.merge(directed(loop.get(i), loop.get((i + 1) % loop.size())), 1, Integer::sum);

		if (directions.isEmpty())
			return 1;
		int unpaired = 0;
		for (Map.Entry<Long, Integer> entry : directions.entrySet()) {
			if (entry.getValue() != 1) {
				unpaired++;
				continue;
			}
			int from = (int) (entry.getKey() >> 32);
			int to = (int) (entry.getKey() & 0xffffffffL);
			if (!Integer.valueOf(1).equals(directions.get(directed(to, from))))
				unpaired++;
		}
		return unpaired;
	}

	/**
	 * The planar faces this writer derives from the mesh: their plane and the
	 * boundary loops they are written with, largest loop first. A coplanar group is
	 * split into its connected regions and each becomes a face of its own, mirroring
	 * what the file holds so this can be checked against a reader. A region whose
	 * loops cannot reproduce its surface is written as one face per triangle, and
	 * this reports it that way too.
	 */
	public static List<StepFace> faces(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] sourceTriangles = csg.getTriangles();
		List<StepFace> faces = new ArrayList<>();
		if (vertices == null || sourceTriangles == null || sourceTriangles.length < 3)
			return faces;

		Mesh mesh = new Mesh(vertices, sourceTriangles);
		for (List<Integer> triangles : mesh.faces) {
			List<List<Integer>> regions = mesh.regions(triangles);
			for (List<Integer> region : regions)
				if (mesh.representsExactly(region))
					faces.add(mesh.describe(region));
				else
					for (int index : region)
						faces.add(mesh.describeTriangle(index));
		}
		return faces;
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
		private final double tJunctionTolerance;
		/** A mesh vertex to the vertex that represents it. */
		private final Map<Integer, Integer> weldMap = new HashMap<>();
		private final Map<Long, List<Integer>> weldGrid = new HashMap<>();
		/** Triangles the source had and this mesh could not keep, for diagnostics. */
		private int droppedCollapsed;
		private int droppedFlat;

		private Mesh(double[] points, long[] sourceTriangles) {
			this.points = points;
			this.planeTolerance = PLANE_TOLERANCE * diagonal(points);
			this.weldTolerance = WELD_TOLERANCE * diagonal(points);
			this.tJunctionTolerance = T_JUNCTION_TOLERANCE * diagonal(points);
			Log.debug("StepBRepWrite: mesh of " + (points.length / 3) + " vertices, "
					+ (sourceTriangles == null ? 0 : sourceTriangles.length / 3) + " triangles, tolerances weld="
					+ weldTolerance + " tJunction=" + tJunctionTolerance + " plane=" + planeTolerance);
			TickToc.tic("prepare mesh");
			prepare(sourceTriangles);
			TickToc.tic("index");
			index();
			TickToc.tic("group");
			group();
			Log.debug("StepBRepWrite: grouped into " + faces.size() + " faces");
		}

		/**
		 * Takes the source triangles, interfering with them as little as possible.
		 *
		 * <p>
		 * A mesh a kernel produced is already edge-manifold by its own indices, so it
		 * is used exactly as it stands: welding or splitting it could only turn a
		 * closed surface into an open one. Only a mesh that is not already manifold is
		 * repaired, first by unifying the vertices that describe one point, then by
		 * splitting a vertex that lies on a neighbour's edge into that edge. Each step
		 * is kept only if it actually closes the surface.
		 */
		private void prepare(long[] sourceTriangles) {
			if (sourceTriangles == null)
				return;

			// as it stands, every triangle kept: dropping a degenerate one would leave
			// its neighbours' edges one sided and open the surface it is part of
			List<int[]> raw = new ArrayList<>(sourceTriangles.length / 3);
			for (int t = 0; t + 2 < sourceTriangles.length; t += 3)
				raw.add(new int[]{(int) sourceTriangles[t], (int) sourceTriangles[t + 1], (int) sourceTriangles[t + 2]});
			triangles.addAll(raw);
			if (isEdgeManifold()) {
				Log.debug("StepBRepWrite: source mesh is already edge-manifold, used as it stands");
				return;
			}

			// welding the vertices that describe one point may close it
			Log.debug("StepBRepWrite: source mesh is not edge-manifold, trying to weld it");
			triangles.clear();
			droppedCollapsed = 0;
			droppedFlat = 0;
			for (int[] triangle : raw)
				addTriangle(triangle[0], triangle[1], triangle[2]);
			Log.debug("StepBRepWrite: welded " + triangles.size() + " triangles (" + droppedCollapsed + " collapsed, "
					+ droppedFlat + " flat dropped)");
			if (isEdgeManifold())
				return;

			// and finally splitting the vertices that lie on a neighbour's edge
			Log.debug("StepBRepWrite: welding did not close it, repairing T-junctions");
			repairTJunctions();
			Log.debug("StepBRepWrite: repaired to " + triangles.size() + " triangles");
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
			if (a == b || b == c || c == a) {
				droppedCollapsed++;
				return;
			}
			int[] triangle = new int[]{a, b, c};
			if (normal(triangle) == null) {
				droppedFlat++;
				return; // a triangle of no area has no plane
			}
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

		/**
		 * Splits every triangle edge that another vertex lies on, so the mesh becomes
		 * edge-manifold by its own indices.
		 *
		 * <p>
		 * A mesh a kernel cut out of its own geometry stores a point that lies on
		 * another triangle's edge as a vertex of its own. Geometrically the surface is
		 * closed there, but by index the long edge and the two short ones it faces do
		 * not pair, so a reader cannot stitch the shell. Splitting the long edge at
		 * that vertex puts the same vertex in both faces' wires.
		 *
		 * <p>
		 * This follows {@link FeatureEdgeExtractor}'s approach. Only an edge used by
		 * one triangle can be a T-junction: an edge used twice already meets another
		 * triangle along its whole length. So the mesh is indexed in one pass, and the
		 * candidate vertices for each of those few boundary edges are found through
		 * three pre-sorted coordinate arrays and a binary search on the tightest axis,
		 * which keeps this near linear instead of comparing every edge to every
		 * vertex.
		 */
		private void repairTJunctions() {
			// a split can expose a vertex that lay beyond the first, so repeat; each
			// round only ever reuses existing vertices, so this settles quickly
			for (int round = 0; round < 8; round++) {
				if (!repairRound())
					return;
			}
			Log.debug("StepBRepWrite: T-junction repair stopped at its round limit");
		}

		/** One pass of the repair. True when it split anything. */
		private boolean repairRound() {
			// count how many triangles use each edge, in one pass
			Map<Long, Integer> uses = new HashMap<>();
			for (int[] triangle : triangles)
				for (int i = 0; i < 3; i++)
					uses.merge(key(triangle[i], triangle[(i + 1) % 3]), 1, Integer::sum);

			// only the vertices that end an edge used once can be a T-junction, and
			// only an edge used once can receive one
			List<Integer> endpoints = new ArrayList<>();
			for (Map.Entry<Long, Integer> entry : uses.entrySet()) {
				if (entry.getValue() != 1)
					continue;
				endpoints.add((int) (entry.getKey() >> 32));
				endpoints.add((int) (entry.getKey() & 0xffffffffL));
			}
			if (endpoints.isEmpty())
				return false;

			List<Integer> unique = new ArrayList<>(new LinkedHashSet<>(endpoints));
			int[] byX = sortedBy(unique, 0);
			int[] byY = sortedBy(unique, 1);
			int[] byZ = sortedBy(unique, 2);

			List<int[]> repaired = new ArrayList<>(triangles.size());
			boolean changed = false;
			for (int[] triangle : triangles)
				// an edge used twice already meets its neighbour along its whole length,
				// so only a boundary edge can carry a T-junction; splitting a shared one
				// would tear the surface instead
				for (int[] piece : splitTriangle(triangle, uses, byX, byY, byZ)) {
					repaired.add(piece);
					changed |= piece != triangle;
				}
			if (!changed)
				return false;

			triangles.clear();
			triangles.addAll(repaired);
			return true;
		}

		/** Vertex ids ordered along one axis, for binary searching a box. */
		private int[] sortedBy(List<Integer> vertices, int axis) {
			List<Integer> order = new ArrayList<>(vertices);
			order.sort((a, b) -> Double.compare(coordinate(a, axis), coordinate(b, axis)));
			int[] result = new int[order.size()];
			for (int i = 0; i < result.length; i++)
				result[i] = order.get(i);
			return result;
		}

		private double coordinate(int vertex, int axis) {
			return points[vertex * 3 + axis];
		}

		/**
		 * The vertices lying on the segment between two vertices, other than its
		 * endpoints, in order from {@code from} to {@code to}.
		 */
		private List<Integer> verticesOn(int from, int to, int[] byX, int[] byY, int[] byZ) {
			double[] a = point(from);
			double[] b = point(to);
			double minX = Math.min(a[0], b[0]) - tJunctionTolerance;
			double maxX = Math.max(a[0], b[0]) + tJunctionTolerance;
			double minY = Math.min(a[1], b[1]) - tJunctionTolerance;
			double maxY = Math.max(a[1], b[1]) + tJunctionTolerance;
			double minZ = Math.min(a[2], b[2]) - tJunctionTolerance;
			double maxZ = Math.max(a[2], b[2]) + tJunctionTolerance;

			// search along whichever axis the segment spans least, so few candidates
			// are scanned
			int[] order = byX;
			double low = minX;
			double high = maxX;
			double spanX = maxX - minX, spanY = maxY - minY, spanZ = maxZ - minZ;
			if (spanY < spanX && spanY <= spanZ) {
				order = byY;
				low = minY;
				high = maxY;
			} else if (spanZ < spanX) {
				order = byZ;
				low = minZ;
				high = maxZ;
			}

			int axis = order == byX ? 0 : order == byY ? 1 : 2;
			int start = lowerBound(order, axis, low);
			int end = upperBound(order, axis, high);

			double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
			double lengthSquared = dx * dx + dy * dy + dz * dz;
			if (lengthSquared == 0)
				return List.of();

			List<double[]> cuts = new ArrayList<>();
			for (int i = start; i < end; i++) {
				int vertex = order[i];
				if (vertex == from || vertex == to)
					continue;
				double[] p = point(vertex);
				if (p[0] < minX || p[0] > maxX || p[1] < minY || p[1] > maxY || p[2] < minZ || p[2] > maxZ)
					continue;
				double t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy + (p[2] - a[2]) * dz) / lengthSquared;
				if (t <= 1e-9 || t >= 1 - 1e-9)
					continue;
				double x = a[0] + dx * t, y = a[1] + dy * t, z = a[2] + dz * t;
				double off = (p[0] - x) * (p[0] - x) + (p[1] - y) * (p[1] - y) + (p[2] - z) * (p[2] - z);
				if (off > tJunctionTolerance * tJunctionTolerance)
					continue;
				cuts.add(new double[]{t, vertex});
			}

			cuts.sort((left, right) -> Double.compare(left[0], right[0]));
			List<Integer> vertices = new ArrayList<>(cuts.size());
			for (double[] cut : cuts)
				vertices.add((int) cut[1]);
			return vertices;
		}

		private int lowerBound(int[] order, int axis, double value) {
			int low = 0, high = order.length;
			while (low < high) {
				int mid = (low + high) >>> 1;
				if (coordinate(order[mid], axis) < value)
					low = mid + 1;
				else
					high = mid;
			}
			return low;
		}

		private int upperBound(int[] order, int axis, double value) {
			int low = 0, high = order.length;
			while (low < high) {
				int mid = (low + high) >>> 1;
				if (coordinate(order[mid], axis) <= value)
					low = mid + 1;
				else
					high = mid;
			}
			return low;
		}

		/**
		 * The triangles one triangle becomes once each edge is cut at the vertices
		 * lying on it. The cuts turn it into a polygon with the same outline, which is
		 * re-triangulated; a plain fan would put triangles of no area along the cut
		 * edge when the cut vertex sits beside the fan origin.
		 */
		private List<int[]> splitTriangle(int[] triangle, Map<Long, Integer> uses, int[] byX, int[] byY, int[] byZ) {
			List<Integer> outline = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				int from = triangle[i];
				int to = triangle[(i + 1) % 3];
				add(outline, from);
				if (uses.get(key(from, to)) == 1)
					for (int vertex : verticesOn(from, to, byX, byY, byZ))
						add(outline, vertex);
			}
			if (outline.size() == 3 && outline.get(0) == triangle[0] && outline.get(1) == triangle[1]
					&& outline.get(2) == triangle[2])
				return List.of(triangle);

			// the triangulator indexes its point list by the loop's own entries, so the
			// list has to be addressed by the mesh's vertex numbers
			List<double[]> positions = new ArrayList<>();
			int max = 0;
			for (int vertex : outline)
				max = Math.max(max, vertex);
			while (positions.size() <= max)
				positions.add(null);
			for (int vertex : outline)
				positions.set(vertex, point(vertex));

			List<int[]> pieces = new ArrayList<>();
			for (int[] piece : Triangulator.triangulate(outline, positions))
				if (normal(piece) != null)
					pieces.add(piece);
			return pieces.isEmpty() ? List.of(triangle) : pieces;
		}

		/** Appends a vertex unless the previous one is the same point. */
		private void add(List<Integer> outline, int vertex) {
			if (!outline.isEmpty() && distance(point(outline.get(outline.size() - 1)), point(vertex)) <= weldTolerance)
				return;
			outline.add(vertex);
		}

		// --- grouping into faces ---

		/** Grows one face from every triangle, absorbing the coplanar neighbours. */
		private void group() {
			boolean[] used = new boolean[triangles.size()];
			for (int start = 0; start < triangles.size(); start++) {
				if (used[start])
					continue;

				Vector3d normal = normal(triangles.get(start));
				// a triangle of no area has no plane of its own, so it becomes a face
				// alone; the loops still bound it, so it keeps the shell closed
				if (normal == null) {
					used[start] = true;
					faces.add(List.of(start));
					continue;
				}
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

		private long face(StepEmitter emitter, List<Integer> region, List<List<Integer>> loops, String name) {
			int first = triangles.get(region.get(0))[0];
			Vector3d normal = normal(triangles.get(region.get(0)));
			// a face of no area still needs a plane, or it cannot be written; any
			// direction will do, since the loop encloses nothing
			if (normal == null)
				normal = new Vector3d(0, 0, 1);
			// the plane must pass through the face, not the origin, or the face does
			// not lie on it and the model has no volume
			long plane = plane(emitter, point(first), normal, name);

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

		/**
		 * True when the loops written for a region enclose the same area the region's
		 * triangles cover.
		 *
		 * <p>
		 * Equal area is the right test, not equal triangles: a reader re-triangulates a
		 * face from its loops, and the triangles it picks are generally not the ones
		 * the mesh held. A square cut along one diagonal reads back cut along the
		 * other, which is the same surface. What must never differ is how much area the
		 * face covers: the outer loop less its holes has to match the triangles
		 * exactly, or the file describes a surface the mesh does not have.
		 */
		private boolean representsExactly(List<Integer> region) {
			Vector3d normal = normal(triangles.get(region.get(0)));
			if (normal == null)
				return false;

			List<List<Integer>> loops = loops(region, normal);
			if (loops.isEmpty())
				return false;

			double trianglesArea = 0;
			for (int index : region)
				trianglesArea += Math.abs(area(asLoop(triangles.get(index)), normal)) / 2;

			// the outer loop, less every hole inside it
			double loopsArea = Math.abs(area(loops.get(0), normal)) / 2;
			for (int i = 1; i < loops.size(); i++)
				loopsArea -= Math.abs(area(loops.get(i), normal)) / 2;

			// any real difference is a hole the file would not describe, or surface it
			// would add
			double tolerance = Math.max(1e-12, trianglesArea * 1e-9);
			return Math.abs(trianglesArea - loopsArea) <= tolerance;
		}

		private static List<Integer> asLoop(int[] triangle) {
			List<Integer> loop = new ArrayList<>(3);
			loop.add(triangle[0]);
			loop.add(triangle[1]);
			loop.add(triangle[2]);
			return loop;
		}

		/** A single triangle as the one loop that bounds it exactly. */
		private List<Integer> triangleLoop(int index) {
			return asLoop(triangles.get(index));
		}

		/** One ADVANCED_FACE over a single triangle, which covers it exactly. */
		private long triangleFace(StepEmitter emitter, int index, String name) {
			int[] triangle = triangles.get(index);
			Vector3d normal = normal(triangle);
			if (normal == null)
				normal = new Vector3d(0, 0, 1);
			long plane = plane(emitter, point(triangle[0]), normal, name);
			List<Integer> loop = new ArrayList<>(3);
			loop.add(triangle[0]);
			loop.add(triangle[1]);
			loop.add(triangle[2]);
			long loopId = edgeLoop(emitter, loop);
			long bound = emitter.add("FACE_OUTER_BOUND('',#" + loopId + ",.T.)");
			return emitter.add("ADVANCED_FACE(" + quote(name) + ",(#" + bound + "),#" + plane + ",.T.)");
		}

		/** The face as data: its plane and its loops of points. */
		private StepFace describe(List<Integer> face) {
			int first = triangles.get(face.get(0))[0];
			Vector3d normal = normal(triangles.get(face.get(0)));
			if (normal == null)
				normal = new Vector3d(0, 0, 1);
			List<List<double[]>> points = new ArrayList<>();
			// the same normal the face is written with, so the loops described here are
			// exactly the loops in the file
			for (List<Integer> loop : loops(face, normal)) {
				List<double[]> loopPoints = new ArrayList<>();
				for (int vertex : loop)
					loopPoints.add(point(vertex));
				points.add(loopPoints);
			}
			return new StepFace(point(first), normal, points);
		}

		/** A single triangle as data, the shape written when a group cannot be. */
		private StepFace describeTriangle(int index) {
			int[] triangle = triangles.get(index);
			Vector3d normal = normal(triangle);
			if (normal == null)
				normal = new Vector3d(0, 0, 1);
			List<double[]> loop = new ArrayList<>(3);
			loop.add(point(triangle[0]));
			loop.add(point(triangle[1]));
			loop.add(point(triangle[2]));
			return new StepFace(point(triangle[0]), normal, List.of(loop));
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
		 * cancel and what remains is the outline. The mesh's T-junctions were split
		 * before the faces were grouped, so an edge that ends at a vertex on the
		 * neighbouring face's outline matches it exactly in the opposite direction,
		 * which is what lets the shell be recognised as closed. At a vertex the
		 * outline passes through twice, such as the tip of a facet that meets the rest
		 * of the face only at that point, the walk takes the tightest turn, so the two
		 * passes become two loops rather than one that crosses itself.
		 */
		private List<List<Integer>> loops(List<Integer> face, Vector3d normal) {
			// the mesh is edge-manifold by index once T-junctions are repaired, so the
			// boundary edges already pair with the neighbouring face's and need no
			// further cutting
			List<int[]> boundary = boundary(face);
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
				// Every cycle is kept. Dropping one takes its edges out of the topology,
				// which leaves the face across them one sided and opens the shell, and a
				// face with no bound at all is worse still: it covers its entire plane.
				// The area a cycle encloses only decides which bound is the outer one.
				if (distinct(loop) >= 3)
					loops.add(loop);
			}

			// last resort, so a face is never written without a bound: one of its own
			// triangles bounds it
			if (loops.isEmpty() && !face.isEmpty()) {
				int[] triangle = triangles.get(face.get(0));
				loops.add(List.of(triangle[0], triangle[1], triangle[2]));
			}

			loops.sort((a, b) -> Double.compare(Math.abs(area(b, normal)), Math.abs(area(a, normal))));

			// A face's first loop is its outer bound and every other loop is a hole cut
			// out of it. That is only true when all the cycles lie inside one another.
			// A coplanar group can also hold two separate regions, which meet at a
			// vertex or not at all; writing the second as a hole would subtract it, and
			// that is a missing face. So the cycles are sorted into regions first, and
			// each region becomes a face of its own.
			return loops;
		}

		/**
		 * A coplanar group split into its connected regions: sets of triangles that
		 * share edges with one another.
		 *
		 * <p>
		 * A STEP face has exactly one outer bound, and every other bound it names is a
		 * hole cut out of that outline. A group of coplanar triangles can cover two
		 * areas that touch only at a vertex, or not at all. Writing such a group as one
		 * face makes the second area a hole, which subtracts it: that is the missing
		 * face. Splitting the group at the edges it does not share keeps each region's
		 * outline whole.
		 */
		private List<List<Integer>> regions(List<Integer> face) {
			Map<Integer, Integer> parent = new LinkedHashMap<>();
			for (int index : face)
				parent.put(index, index);

			// union any two of the group's triangles that share an edge
			Map<Long, Integer> owner = new HashMap<>();
			for (int index : face)
				for (int i = 0; i < 3; i++) {
					int[] triangle = triangles.get(index);
					long key = key(triangle[i], triangle[(i + 1) % 3]);
					Integer other = owner.putIfAbsent(key, index);
					if (other != null && other != index)
						union(parent, other, index);
				}

			Map<Integer, List<Integer>> byRoot = new LinkedHashMap<>();
			for (int index : face)
				byRoot.computeIfAbsent(find(parent, index), unused -> new ArrayList<>()).add(index);
			return new ArrayList<>(byRoot.values());
		}

		private static int find(Map<Integer, Integer> parent, int index) {
			int root = index;
			while (parent.get(root) != root)
				root = parent.get(root);
			// flatten, so repeated queries stay cheap
			int at = index;
			while (parent.get(at) != root) {
				int next = parent.get(at);
				parent.put(at, root);
				at = next;
			}
			return root;
		}

		private static void union(Map<Integer, Integer> parent, int a, int b) {
			parent.put(find(parent, a), find(parent, b));
		}

		/** How many different vertices a loop visits. */
		private static int distinct(List<Integer> loop) {
			return new LinkedHashSet<>(loop).size();
		}

		/** The boundary loops of a region, from its first triangle's normal. */
		private List<List<Integer>> loops(List<Integer> region) {
			return loops(region, normal(triangles.get(region.get(0))));
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

		/**
		 * True when the repaired mesh is edge-manifold and closed: every undirected
		 * edge is used by exactly two triangles, one running each way.
		 *
		 * <p>
		 * This holds by index, because the T-junctions were split first. It is the
		 * same condition a reader checks when it stitches the wires of two faces, so
		 * it is the honest test of whether the shell is a solid.
		 */
		private boolean isEdgeManifold() {
			Map<Long, Integer> directions = new HashMap<>();
			Set<Long> edges = new LinkedHashSet<>();
			for (int[] triangle : triangles)
				for (int i = 0; i < 3; i++) {
					int from = triangle[i];
					int to = triangle[(i + 1) % 3];
					directions.merge(directed(from, to), 1, Integer::sum);
					edges.add(key(from, to));
				}
			if (edges.isEmpty())
				return false;
			for (long edge : edges) {
				int from = (int) (edge >> 32);
				int to = (int) (edge & 0xffffffffL);
				if (!Integer.valueOf(1).equals(directions.get(directed(from, to)))
						|| !Integer.valueOf(1).equals(directions.get(directed(to, from))))
					return false;
			}
			return true;
		}

		/** The edges that stop the mesh being closed, for a diagnostic message. */
		private String describeEdges() {
			Map<Long, Integer> directions = new HashMap<>();
			Set<Long> edges = new LinkedHashSet<>();
			for (int[] triangle : triangles)
				for (int i = 0; i < 3; i++) {
					int from = triangle[i];
					int to = triangle[(i + 1) % 3];
					directions.merge(directed(from, to), 1, Integer::sum);
					edges.add(key(from, to));
				}

			int unpaired = 0;
			int doubled = 0;
			double shortest = Double.MAX_VALUE;
			for (long edge : edges) {
				int from = (int) (edge >> 32);
				int to = (int) (edge & 0xffffffffL);
				Integer forward = directions.get(directed(from, to));
				Integer backward = directions.get(directed(to, from));
				if (forward == null || backward == null) {
					unpaired++;
					shortest = Math.min(shortest, distance(point(from), point(to)));
				} else if (forward.intValue() != 1 || backward.intValue() != 1)
					doubled++;
			}
			return edges.size() + " edges, " + unpaired + " with no opposite side (shortest "
					+ (unpaired == 0 ? "-" : Double.toString(shortest)) + "), " + doubled
					+ " used more than once each way, weld tolerance " + weldTolerance + ", dropped "
					+ droppedCollapsed + " collapsed and " + droppedFlat + " flat triangles";
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

	/** The ordered edge from one vertex to another, for tracing a winding. */
	private static long directed(int from, int to) {
		return ((long) from << 32) | (to & 0xffffffffL);
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
