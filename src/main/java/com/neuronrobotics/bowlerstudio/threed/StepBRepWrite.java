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
 * The mesh is the ground truth: it is manifold and closed, because that is what a
 * CSG object is. So no face is ever written for one triangle at a time as a
 * fallback, and no triangle is dropped for looking wrong. Every triangle is put in
 * exactly one face, and the face is formed by growing it over the triangles that
 * share an edge and lie in its plane; see {@link Mesh#group}. Fusion is decided by
 * how far a triangle's points sit from the face's plane, never by the triangle's
 * own normal, because the normal of a thin triangle is the subtraction of nearly
 * equal coordinates and is meaningless at exactly the sizes a mesh of a real part
 * holds.
 *
 * <p>
 * Every triangle is kept, however thin. The one thing unified is a point that the
 * mesh stores twice within the distance no reader can resolve; writing that twice
 * produces an edge of no width, which a reader refuses to face and which takes the
 * whole solid down with it. See {@link Mesh#prepare}.
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
	 * are merely close would collapse the thin facets a mesh legitimately has. It
	 * doubles as the T-junction distance, for the same reason: a vertex that belongs
	 * on a neighbour's edge lies *on* it, and only the stored doubles disagree. A
	 * looser bound would pull in a vertex that merely passes nearby and moving an
	 * edge to it changes the surface, which is the gap this writer must not leave.
	 */
	private static final double WELD_TOLERANCE = 5e-9;

	/**
	 * The finest distance a reader of the file resolves, in the file's own units.
	 *
	 * <p>
	 * A kernel works to its own confusion: an edge shorter than that is not an edge
	 * to it but a point it cannot decide, so a face bounded by one is rejected and
	 * the solid it belongs to is read as loose shells. This is a property of the
	 * readers rather than of the mesh, so it is absolute rather than a fraction of
	 * the model: a small part and a large one are read by the same code.
	 *
	 * <p>
	 * Two vertices closer than this are one point to every reader, and are written
	 * as one. That cannot move a feature the mesh resolves, because a feature closer
	 * than this is not one any reader can see.
	 */
	private static final double RESOLUTION = 1e-5;

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
		for (Mesh.Planned planned : mesh.settledPlan()) {
			written.add(planned.loops);
			faces.add(mesh.face(emitter, planned.region, planned.loops, name));
		}
		Log.debug("StepBRepWrite.solid: emitted " + faces.size() + " faces for " + name);
		if (faces.isEmpty())
			return 0;

		// the shell is a solid only when the topology actually written is watertight,
		// so ask the loops that were emitted rather than the mesh they came from
		boolean closed = isWatertight(written);
		Log.debug("StepBRepWrite.solid: " + name + " is " + (closed ? "closed" : "open") + " (" + unpairedEdges(written)
				+ " unpaired edge(s) in the written loops)");
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
	 * what the file holds so this can be checked against a reader.
	 */
	public static List<StepFace> faces(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] sourceTriangles = csg.getTriangles();
		List<StepFace> faces = new ArrayList<>();
		if (vertices == null || sourceTriangles == null || sourceTriangles.length < 3)
			return faces;

		Mesh mesh = new Mesh(vertices, sourceTriangles);
		// the same plan the file is written from, so this reports what a reader of the
		// written file sees rather than what the writer hoped for; see solid()
		for (Mesh.Planned planned : mesh.settledPlan())
			faces.add(mesh.describe(planned.region));
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
		/** A mesh vertex to the vertex that represents it. */
		/** Triangles the source had and this mesh could not keep, for diagnostics. */
		private int droppedCollapsed;

		private Mesh(double[] points, long[] sourceTriangles) {
			this.points = points;
			this.planeTolerance = PLANE_TOLERANCE * diagonal(points);
			Log.debug("StepBRepWrite: mesh of " + (points.length / 3) + " vertices, "
					+ (sourceTriangles == null ? 0 : sourceTriangles.length / 3) + " triangles, plane tolerance "
					+ planeTolerance);
			TickToc.tic("prepare mesh");
			prepare(sourceTriangles);
			TickToc.tic("index");
			index();
			TickToc.tic("group");
			group();
			Log.debug("StepBRepWrite: grouped into " + faces.size() + " faces");
		}

		/**
		 * Takes the source triangles and names each point once.
		 *
		 * <p>
		 * The mesh is manifold, so its triangles are used as they are. The one thing
		 * that is unified is a point the mesh stores more than once, which is how a
		 * kernel that cut the geometry leaves it; see the body.
		 */
		private void prepare(long[] sourceTriangles) {
			if (sourceTriangles == null)
				return;
			// The mesh is written as it is stored. Every triangle the source holds is
			// written, and none is invented: the source is the ground truth, and a writer
			// that split edges or snapped vertices would be exporting a different shape.
			//
			// A triangle naming one vertex twice has no edge to pair and no area to face,
			// so no face can describe it; it is the one thing that has to be left out.
			List<int[]> raw = new ArrayList<>(sourceTriangles.length / 3);
			for (int t = 0; t + 2 < sourceTriangles.length; t += 3) {
				int a = (int) sourceTriangles[t];
				int b = (int) sourceTriangles[t + 1];
				int c = (int) sourceTriangles[t + 2];
				if (a == b || b == c || c == a) {
					droppedCollapsed++;
					continue;
				}
				raw.add(new int[]{a, b, c});
			}
			if (droppedCollapsed > 0)
				Log.debug(
						"StepBRepWrite: " + droppedCollapsed + " triangle(s) name a vertex twice and cannot be faced");

			// A mesh stores one point as several vertices wherever a kernel cut its
			// geometry: the copies are the same point, and no reader that meets them can
			// tell them apart either. Naming them twice writes an edge of no width, and a
			// reader facing a shell bounded by one rejects the whole solid, however sound
			// the rest of it is. So each point is named once, at the finest distinction
			// any reader still makes.
			//
			// The distance is taken as wide as it can be without changing the mesh's own
			// topology: see weld. Unifying further can pinch two sheets of a part that
			// genuinely pass close together into one, which would be exporting a different
			// shape, so the widest that stays faithful is the one used.
			int[] parent = weld(raw);
			triangles.addAll(flattened(raw, parent));
			Log.debug("StepBRepWrite: unification leaves " + triangles.size() + " of " + raw.size()
					+ " triangles with a surface");
		}

		/**
		 * Names each point once, unifying the vertices the mesh stores for it.
		 *
		 * <p>
		 * A kernel leaves one point stored as several vertices wherever it cut the
		 * geometry. Those copies are one point, and no reader can tell them apart: a
		 * file naming them twice writes an edge of no width, and a reader facing a shell
		 * bounded by one rejects the whole solid, however sound the rest of it is. So
		 * they are named once.
		 *
		 * <p>
		 * Which vertices are copies of one point cannot be decided by distance alone.
		 * Nearer than a reader's resolution, two vertices are one point to it, so they
		 * must be unified; but a part can have two sheets that genuinely pass that close
		 * together, and unifying those would be exporting a different shape. What
		 * separates the two is what welding does to the mesh: joining copies leaves
		 * every edge with one triangle on each side, exactly as before, while joining two
		 * sheets puts a third triangle on an edge. So the pairs are joined in order of
		 * distance, and a join is kept only while the mesh still has no edge with more
		 * than two triangles on it. See {@link #weld}.
		 */
		private int[] weld(List<int[]> raw) {
			int count = points.length / 3;
			int[] parent = new int[count];
			for (int vertex = 0; vertex < count; vertex++)
				parent[vertex] = vertex;

			List<int[]> pairs = closePairs(parent);
			// nearest first, so the pairs that are most plainly one point are joined before
			// any pair that is only near by accident
			pairs.sort(
					(a, b) -> Double.compare(distance(point(a[0]), point(a[1])), distance(point(b[0]), point(b[1]))));

			int joined = 0;
			// Joins are ordered nearest first, but a join rejected early can become sound once
			// its neighbours are joined, since what blocks it is a triangle around it that is
			// itself still a sliver. So the list is swept again until a sweep changes nothing.
			boolean progress = true;
			while (progress) {
				progress = false;
				for (int[] pair : pairs) {
					int first = find(parent, pair[0]);
					int second = find(parent, pair[1]);
					if (first == second)
						continue;
					// try the join on a copy: find compresses paths, so undoing the join on the
					// live array would leave other vertices pointing at a root that no longer
					// joins them
					int[] trial = parent.clone();
					trial[find(trial, second)] = find(trial, first);
					if (!staysManifold(raw, trial)) {
						continue;
					}
					parent = trial;
					joined++;
					progress = true;
				}
			}
			Log.debug("StepBRepWrite: joined " + joined + " of " + pairs.size() + " coincident vertex pairs");
			return parent;
		}

		/**
		 * The vertex pairs closer together than a reader can resolve, which are the only
		 * ones that could be copies of one point.
		 *
		 * <p>
		 * The search is through a grid of cells one such distance across, so a vertex is
		 * only compared with those in the cells touching it rather than with all of them.
		 */
		private List<int[]> closePairs(int[] parent) {
			int count = points.length / 3;
			List<int[]> pairs = new ArrayList<>();
			Map<Long, List<Integer>> grid = new HashMap<>();
			for (int vertex = 0; vertex < count; vertex++) {
				double[] p = point(vertex);
				long x = (long) Math.floor(p[0] / RESOLUTION);
				long y = (long) Math.floor(p[1] / RESOLUTION);
				long z = (long) Math.floor(p[2] / RESOLUTION);
				for (long dx = -1; dx <= 1; dx++)
					for (long dy = -1; dy <= 1; dy++)
						for (long dz = -1; dz <= 1; dz++)
							for (int candidate : grid.getOrDefault(cell(x + dx, y + dy, z + dz), List.of()))
								if (distance(point(candidate), p) <= RESOLUTION)
									pairs.add(new int[]{candidate, vertex});
				grid.computeIfAbsent(cell(x, y, z), unused -> new ArrayList<>()).add(vertex);
			}
			return pairs;
		}

		/**
		 * True when the triangles, as the given unification makes them, still close a
		 * surface with one triangle on each side of every edge.
		 *
		 * <p>
		 * That is the statement that two separate sheets were not fused: welding copies
		 * of one point leaves each edge with the same two triangles as before, while
		 * welding two sheets puts a third triangle on the edge where they met.
		 */
		private boolean staysManifold(List<int[]> raw, int[] parent) {
			return isManifold(flattened(raw, parent));
		}

		/**
		 * The triangles as the given unification makes them, less those that describe no
		 * surface at all.
		 *
		 * <p>
		 * Unifying a point can leave a triangle naming one vertex twice, or three
		 * collinear; either way it spans nothing, and no face can be written for it. It
		 * can also leave two triangles on the same three points facing opposite ways,
		 * which is a pocket of no volume: each is the other's exact negative, and the
		 * surface is unchanged whether both or neither is there. Both go, so the sheet
		 * closes across where they were instead of being pinched by them.
		 *
		 * <p>
		 * Where the surface is not completely flat the two opposite triangles are the
		 * only thing closing the pinch. Removing them lets their neighbours, which ran
		 * along the same edges, pair with each other, which is what makes a mesh with a
		 * feature too small to resolve export at all.
		 */
		private List<int[]> flattened(List<int[]> raw, int[] parent) {
			List<int[]> welded = new ArrayList<>(raw.size());
			Map<String, Integer> facing = new LinkedHashMap<>();
			for (int[] triangle : raw) {
				int a = find(parent, triangle[0]);
				int b = find(parent, triangle[1]);
				int c = find(parent, triangle[2]);
				if (a == b || b == c || c == a)
					continue;
				if (spansNoArea(a, b, c))
					continue;
				// a triangle and its exact opposite on the same three points cancel
				String points = canonical(a, b, c);
				String winding = a + "," + b + "," + c;
				String opposite = a + "," + c + "," + b;
				Integer other = facing.get(opposite);
				if (other != null) {
					facing.remove(opposite);
					welded.set(other, null);
					continue;
				}
				facing.put(points, welded.size());
				facing.put(winding, welded.size());
				welded.add(new int[]{a, b, c});
			}
			welded.removeIf(triangle -> triangle == null);
			return welded;
		}

		/** True when the three points lie on one line, so the triangle encloses nothing. */
		private boolean spansNoArea(int a, int b, int c) {
			double[] p = point(a);
			double[] q = point(b);
			double[] r = point(c);
			double ux = q[0] - p[0], uy = q[1] - p[1], uz = q[2] - p[2];
			double vx = r[0] - p[0], vy = r[1] - p[1], vz = r[2] - p[2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			return nx == 0 && ny == 0 && nz == 0;
		}

		/** Three vertices in a fixed order, so a triangle's points can be compared. */
		private static String canonical(int a, int b, int c) {
			int[] sorted = {a, b, c};
			java.util.Arrays.sort(sorted);
			return sorted[0] + "," + sorted[1] + "," + sorted[2];
		}

		/** True when every undirected edge is used by exactly two triangles, once each way. */
		private static boolean isManifold(List<int[]> welded) {
			Map<Long, Integer> directions = new HashMap<>();
			Set<Long> edges = new LinkedHashSet<>();
			for (int[] triangle : welded)
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

		private static int find(int[] parent, int vertex) {
			int root = vertex;
			while (parent[root] != root)
				root = parent[root];
			int at = vertex;
			while (parent[at] != root) {
				int next = parent[at];
				parent[at] = root;
				at = next;
			}
			return root;
		}

		private static void union(int[] parent, int a, int b) {
			parent[find(parent, a)] = find(parent, b);
		}

		private static long cell(long x, long y, long z) {
			return (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
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

		private static double distance(double[] a, double[] b) {
			double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		// --- grouping into faces ---

		/**
		 * Grows one face from every triangle, absorbing the neighbours that lie in its
		 * plane.
		 *
		 * <p>
		 * A face is a plane and the triangles on it. Its plane comes from the seed
		 * triangle, whose corners are never collinear by construction, and each
		 * candidate is judged only by how far its three points sit from that plane: a
		 * triangle belonging to the same flat surface has all three within the rounding
		 * of the stored doubles, and one on any other surface, however slightly turned,
		 * has at least one point off it.
		 *
		 * <p>
		 * The candidate's own normal is deliberately not consulted. A triangle of three
		 * points a fraction of a micron apart has a normal computed by subtracting
		 * nearly equal coordinates, so it is mostly rounding error; testing that
		 * against the face's normal rejects triangles that are exactly on the plane, and
		 * leaves each of them as a face of its own. What matters is where the points
		 * are, and that is what is measured.
		 *
		 * <p>
		 * Where two surfaces are parallel and closer than the plane tolerance, such as
		 * the two sides of a thin wall, lying in the plane is not enough to tell them
		 * apart. What does tell them apart is how they wind, and that needs no tolerance
		 * at all: the mesh is manifold, so the two triangles across any edge run that
		 * edge in opposite directions. A face grows only into neighbours that share its
		 * winding, which is exact integer arithmetic on the mesh's own indices, so a
		 * triangle on the far side of a wall is never taken for a triangle on this one.
		 *
		 * <p>
		 * The seeds are taken largest first, so the plane a face is built on always
		 * comes from a triangle with area to spare. A triangle of no area then always
		 * has a grouped neighbour to join, and joins that neighbour's face; there is no
		 * triangle that no face takes.
		 */
		private void group() {
			boolean[] used = new boolean[triangles.size()];
			Map<Integer, Integer> owner = new HashMap<>();
			for (int start : byDescendingArea()) {
				if (used[start])
					continue;

				Vector3d normal = normal(triangles.get(start));
				// a triangle whose points are collinear spans no area and so has no plane;
				// it joins a face further down, once a neighbour has one
				if (normal == null)
					continue;

				double[] origin = point(triangles.get(start)[0]);
				List<Integer> face = new ArrayList<>();
				face.add(start);
				used[start] = true;
				owner.put(start, faces.size());

				List<Integer> frontier = new ArrayList<>();
				frontier.add(start);
				while (!frontier.isEmpty()) {
					int current = frontier.remove(frontier.size() - 1);
					int[] corners = triangles.get(current);
					for (int i = 0; i < 3; i++) {
						int from = corners[i];
						int to = corners[(i + 1) % 3];
						for (int neighbour : byEdge.get(key(from, to))) {
							if (used[neighbour] || !windsOpposite(neighbour, to, from))
								continue;
							if (!liesOn(triangles.get(neighbour), origin, normal))
								continue;
							used[neighbour] = true;
							face.add(neighbour);
							owner.put(neighbour, faces.size());
							frontier.add(neighbour);
						}
					}
				}
				faces.add(face);
			}

			// Whatever is left spans no area. It is still one of the mesh's triangles, so
			// it belongs to a face like any other: the neighbours across its edges are
			// grouped already, and the points it shares with them are on that face's
			// plane by definition, since they are that face's own points.
			for (int index = 0; index < triangles.size(); index++) {
				if (used[index])
					continue;
				Integer face = null;
				for (int neighbour : neighbours(index)) {
					face = owner.get(neighbour);
					if (face != null)
						break;
				}
				if (face == null) {
					// the mesh is manifold, so this cannot happen; a face of its own keeps
					// the triangle from being lost rather than failing the export outright
					faces.add(new ArrayList<>(List.of(index)));
				} else {
					faces.get(face).add(index);
					owner.put(index, face);
				}
				used[index] = true;
			}
		}

		/** The triangles' indices, largest area first, so a face is seeded from a real plane. */
		private List<Integer> byDescendingArea() {
			List<Integer> order = new ArrayList<>(triangles.size());
			for (int index = 0; index < triangles.size(); index++)
				order.add(index);
			order.sort((a, b) -> Double.compare(area2(triangles.get(b)), area2(triangles.get(a))));
			return order;
		}

		/** Twice a triangle's area, without taking a root, for ordering. */
		private double area2(int[] triangle) {
			double[] a = point(triangle[0]);
			double[] b = point(triangle[1]);
			double[] c = point(triangle[2]);
			double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
			double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			return nx * nx + ny * ny + nz * nz;
		}

		/** The triangles sharing an edge with the given one, whichever way they wind. */
		private List<Integer> neighbours(int triangle) {
			int[] corners = triangles.get(triangle);
			List<Integer> neighbours = new ArrayList<>(3);
			for (int i = 0; i < 3; i++)
				for (int other : byEdge.get(key(corners[i], corners[(i + 1) % 3])))
					if (other != triangle)
						neighbours.add(other);
			return neighbours;
		}

		/**
		 * True when the triangle walks the edge in a given direction.
		 *
		 * <p>
		 * This is how two triangles are known to be on the same side of a surface, and
		 * it is exact: it compares the mesh's own indices, so no tolerance enters. The
		 * mesh is manifold, so across any edge exactly one triangle runs it one way and
		 * one the other, and the pair on the far side of a surface run it the opposite
		 * way to this side's.
		 *
		 * <p>
		 * This is what keeps the two sides of a thin sheet apart. Their planes are
		 * closer than the plane tolerance, so lying in the plane cannot tell them apart,
		 * and a candidate's own normal cannot either: at that thickness the normal is
		 * rounding error. Where the winding disagrees, the triangle faces the other way,
		 * whatever its normal works out to.
		 */
		private boolean windsOpposite(int triangle, int from, int to) {
			int[] corners = triangles.get(triangle);
			for (int i = 0; i < 3; i++)
				if (corners[i] == from && corners[(i + 1) % 3] == to)
					return true;
			return false;
		}

		/** True when every point of the triangle sits in the given plane. */
		private boolean liesOn(int[] triangle, double[] origin, Vector3d normal) {
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
		 * How the mesh's triangles are to be written: one entry per written face, each
		 * holding the boundary loops of one coplanar region.
		 *
		 * <p>
		 * Every triangle is in exactly one face, and every face is one region of
		 * triangles that lie in one plane and share edges. There is no fallback: a face
		 * is never written for a single triangle, because a mesh that reached a CSG is
		 * manifold, so its regions always stitch. Writing one face per triangle would
		 * cover the same surface while making the part unusable to select or edit, and
		 * is the shape this writer exists to avoid.
		 */
		private List<Planned> plan() {
			List<Planned> plan = new ArrayList<>();
			for (List<Integer> group : faces)
				// a coplanar group can hold several disconnected regions, and a STEP face
				// can only bound one: its first loop is the outline and the rest are holes
				// cut out of it. So each region is written as a face of its own.
				for (List<Integer> region : regions(group))
					plan.add(new Planned(region, loops(region)));
			return plan;
		}

		/**
		 * The plan, held to the one property that matters: every triangle of the mesh is
		 * under exactly one face.
		 *
		 * <p>
		 * Not a fallback and not a repair. The mesh is manifold, so the grouping and the
		 * stitch always succeed, and this only states that they did. A triangle that no
		 * face took would be a triangle the file does not describe, and there is no
		 * second shape to write instead.
		 */
		private List<Planned> settledPlan() {
			List<Planned> plan = plan();
			boolean[] covered = new boolean[triangles.size()];
			for (Planned planned : plan)
				for (int index : planned.region)
					covered[index] = true;
			for (int index = 0; index < covered.length; index++)
				if (!covered[index])
					throw new IllegalStateException(
							"STEP export left triangle " + index + " out of every face: " + describeEdges());
			return plan;
		}

		/** One written face: the triangles it stands for and the loops that bound them. */
		private static final class Planned {
			private final List<Integer> region;
			private final List<List<Integer>> loops;

			private Planned(List<Integer> region, List<List<Integer>> loops) {
				this.region = region;
				this.loops = loops;
			}
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
			List<int[]> boundary = boundary(face);
			if (boundary.isEmpty())
				return new ArrayList<>();

			// The boundary is a set of directed edges. Group them by the vertex they leave
			// from and the vertex they arrive at, and sort each group by the angle it
			// makes with the face's reference direction. The angles give every vertex's
			// edges a fixed order, which is what the walk needs: see pair.
			Map<Integer, List<int[]>> leaving = new LinkedHashMap<>();
			Map<Integer, List<int[]>> arriving = new LinkedHashMap<>();
			for (int[] edge : boundary) {
				leaving.computeIfAbsent(edge[0], unused -> new ArrayList<>()).add(edge);
				arriving.computeIfAbsent(edge[1], unused -> new ArrayList<>()).add(edge);
			}
			Map<Long, int[]> successor = new HashMap<>();
			for (Integer vertex : leaving.keySet()) {
				if (!arriving.containsKey(vertex))
					continue;
				List<int[]> out = leaving.get(vertex);
				List<int[]> in = arriving.get(vertex);
				// Both groups are ranked by direction as seen looking out from the vertex.
				// An arriving edge points the other way, so it is ranked by where it came
				// from, which is the direction the walk would leave along if it went back.
				sortByAngle(out, vertex, normal, false);
				sortByAngle(in, vertex, normal, true);
				// Take the tightest turn: from an arriving edge, the next edge round the
				// ring in the direction that keeps the face on one side. Pairing the ranks
				// in step makes this a bijection, so the walk can never arrive at a vertex
				// and find its way out already used. Choosing each edge's tightest turn
				// independently would not be: two could pick the same successor, and the
				// walk that lost the race would stop in mid-air, its edges in no loop.
				for (int i = 0; i < in.size(); i++) {
					int[] from = in.get(i);
					int[] to = out.get((i + out.size() - 1) % out.size());
					successor.put(key(from[0], from[1]), to);
				}
			}

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
					int[] next = successor.get(key(at[0], at[1]));
					if (next == null)
						throw new IllegalStateException("STEP export: a face's boundary has a vertex with more edges"
								+ " leaving than arriving, so it cannot be walked: " + describeEdges());
					at = next;
				}
				// the area a cycle encloses only decides which bound is the outer one
				if (distinct(loop) >= 3)
					loops.add(loop);
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
		 * Sorts boundary edges by the angle their far end makes about the face, so the
		 * edges meeting a vertex have one fixed cyclic order.
		 *
		 * <p>
		 * The angle is measured in the face's plane about its normal, and it is only
		 * ever used to order edges relative to each other, never as a magnitude.
		 */
		private void sortByAngle(List<int[]> edges, int vertex, Vector3d normal, boolean arriving) {
			Vector3d u = perpendicularTo(normal);
			Vector3d v = normal.cross(u);
			double[] origin = point(vertex);
			Map<Long, Double> angle = new HashMap<>();
			for (int[] edge : edges) {
				int far = edge[0] == vertex ? edge[1] : edge[0];
				double[] p = point(far);
				double sign = arriving ? -1 : 1;
				double dx = sign * (p[0] - origin[0]);
				double dy = sign * (p[1] - origin[1]);
				double dz = sign * (p[2] - origin[2]);
				double along = dx * u.x + dy * u.y + dz * u.z;
				double across = dx * v.x + dy * v.y + dz * v.z;
				angle.put(key(edge[0], edge[1]), Math.atan2(across, along));
			}
			edges.sort((a, b) -> Double.compare(angle.get(key(a[0], a[1])), angle.get(key(b[0], b[1]))));
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
					+ " used more than once each way";
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
