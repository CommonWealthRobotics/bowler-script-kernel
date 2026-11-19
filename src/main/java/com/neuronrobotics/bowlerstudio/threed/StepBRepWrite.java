package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
 * The mesh is the ground truth and is written as it is stored: no triangle is
 * added, and none is moved or dropped. A face holds triangles that lie in one
 * plane, not merely triangles with parallel normals, because two triangles with
 * the same normal but different offsets make the face between them curved. Every
 * triangle is kept, however thin: a dropped one leaves its neighbours' edges one
 * sided, which opens the shell, and a reader fills that gap with a face of no
 * volume.
 *
 * <p>
 * The one thing that is unified is a point the mesh stores as several vertices.
 * Those are the same point, so naming them separately would only invite a reader
 * to weld them itself; see {@link Mesh#prepare}. Nothing else about the mesh is
 * repaired — a mesh whose own indices do not pair is written as it is, and a
 * kernel that cannot stitch it is reporting a limit of its own resolution.
 */
public final class StepBRepWrite {

	/**
	 * How far outside a face's plane a triangle may reach, as a fraction of the
	 * model's own size. A mesh stores rounded doubles, so triangles that are
	 * coplanar by design differ by a few ulps, and the model's extent scales that
	 * error; a ten millionth of the extent is far below any feature a mesh
	 * resolves, yet well above the rounding.
	 */
	private static final double PLANE_TOLERANCE = 1e-6;

	/**
	 * How far a fused face may sit outside the plane it is written on, in model units.
	 * A mesh that was cut flat still stores its flat faces at the precision of its own
	 * coordinates, so the triangles of one flat face do not agree to the last bit. The
	 * bound here is the resolution a STEP reader itself works to, so a face within it
	 * is one a reader treats as flat; fusing above it would move the surface a reader
	 * can measure.
	 */
	private static final double PLANE_RESOLUTION = 1e-7;


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
		Mesh mesh = new Mesh(vertices, sourceTriangles).settledMesh();
		if (mesh.faces.isEmpty())
			return 0;
		TickToc.tic("emit faces " + name);
		List<Long> faces = new ArrayList<>();
		/** The loops actually written, so the shell can be checked as emitted. */
		List<List<List<Integer>>> written = new ArrayList<>();
		int emitted = 0;
		int fellBack = 0;
		for (Mesh.Planned planned : mesh.verifiedPlan(sourceTriangles, name)) {
			if (planned.loops != null) {
				written.add(planned.loops);
				faces.add(mesh.face(emitter, planned.region, planned.loops, name));
				emitted++;
			} else {
				// this triangle is written as a face of its own; where the fold pass
				// spliced a folded neighbour's tip into its walk, the planned loop is
				// that walk, and its edges pair with the neighbours across the fold
				List<Integer> loop = planned.loops != null
						? planned.loops.get(0)
						: mesh.triangleLoop(planned.region.get(0));
				written.add(List.of(loop));
				faces.add(mesh.triangleFace(emitter, planned.region.get(0), loop, name));
				fellBack++;
			}
		}
		if (fellBack > 0)
			Log.debug("StepBRepWrite.solid: " + fellBack + " triangle(s) of " + mesh.triangles.size()
					+ " written as their own face, the rest stitched");
		Log.debug("StepBRepWrite.solid: emitted " + emitted + " stitched faces for " + name);
		if (faces.isEmpty())
			return 0;

		// the shell is a solid only when the topology actually written is watertight,
		// so ask the loops that were emitted rather than the mesh they came from: the
		// two differ when a region fell back to per-triangle faces
		boolean closed = isWatertight(written);
		Log.debug("StepBRepWrite.solid: " + name + " is " + (closed ? "closed" : "open") + " (" + unpairedEdges(written)
				+ " unpaired edge(s) in the written loops)");
		String shellKind = closed ? "CLOSED_SHELL" : "OPEN_SHELL";
		long shell = emitter.add(shellKind + "(''," + references(faces) + ")");
		if (closed)
			return emitter.add("MANIFOLD_SOLID_BREP(" + quote(name) + ",#" + shell + ")");
		// an open shell is a defect for a watertight mesh, so say what opened it
		System.err.println("StepBRepWrite: " + name + " is written as an open shell: " + mesh.describeEdges()
				+ " (source " + (sourceTriangles.length / 3) + " triangles, kept " + mesh.triangles.size() + ")");
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
			int from = (int) (entry.getKey() >> 32);
			int to = (int) (entry.getKey() & 0xffffffffL);
			// a degenerate edge, running from a point to itself, is its own opposite
			if (from == to)
				continue;
			// An edge can also carry more than one pairing when two sheets of the part
			// meet along it: as many walks one way as the other, each pair closing. That
			// closes the surface too, so only an imbalance marks the shell open.
			int forward = entry.getValue();
			int backward = directions.getOrDefault(directed(to, from), 0);
			if (forward != backward)
				unpaired++;
		}
		return unpaired;
	}


	public static List<StepFace> faces(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] sourceTriangles = csg.getTriangles();
		List<StepFace> faces = new ArrayList<>();
		if (vertices == null || sourceTriangles == null || sourceTriangles.length < 3)
			return faces;

		Mesh mesh = new Mesh(vertices, sourceTriangles).settledMesh();
		// the same plan the file is written from, after the same whole-part check, so
		// this reports what a reader of the written file sees rather than what the
		// writer hoped for; see solid()
		for (Mesh.Planned planned : mesh.settledPlan()) {
			if (planned.loops != null)
				faces.add(mesh.describe(planned.region));
			else
				faces.add(mesh.describeTriangle(planned.region.get(0)));
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
		/**
		 * The vertex each point is named by: a point the mesh stores under several
		 * indices is one point, and a loop, an edge and the triangles across it
		 * must name it one way or they cannot pair.
		 */
		private int[] canonical;
		/** How many references named a point another index already named. */
		private int duplicateReferences;

		/**
		 * The scale at which a STEP reader merges two points into one, as a fraction
		 * of the model's extent. It is the yardstick the file is written to: two
		 * entities closer than this are one point to the reader that sews them.
		 */
		private static final double READER_TOLERANCE = 1e-6;

		private final double readerTolerance;

		private final double planeTolerance;
		/** A mesh vertex to the vertex that represents it. */
		/** Triangles the source had and this mesh could not keep, for diagnostics. */
		private int droppedCollapsed;

		private Mesh(double[] points, long[] sourceTriangles) {
			this.points = points;
			this.planeTolerance = PLANE_TOLERANCE * diagonal(points);
			this.readerTolerance = READER_TOLERANCE * diagonal(points);
			Log.debug("StepBRepWrite: mesh of " + (points.length / 3) + " vertices, "
					+ (sourceTriangles == null ? 0 : sourceTriangles.length / 3) + " triangles, plane tolerance "
					+ planeTolerance);
			TickToc.tic("prepare mesh");
			prepare(sourceTriangles);
			canonicalize();
			TickToc.tic("index");
			index();
			indexVertices();
			TickToc.tic("group");
			group();
			Log.debug("StepBRepWrite: grouped into " + faces.size() + " faces");
		}

		/**
		 * Renames every reference to a point the mesh stores under several indices
		 * onto the lowest of them, so a loop, an edge and the triangles across it
		 * name a point one way. Distinct points that a junction repair may yet
		 * treat as one keep their own indices; the repair works on names, not on
		 * the points, and re-links them itself.
		 */
		private void canonicalize() {
			int count = points.length / 3;
			canonical = new int[count];
			for (int vertex = 0; vertex < count; vertex++)
				canonical[vertex] = vertex;
			Map<Long, Integer> byPoint = new HashMap<>();
			for (int vertex = 0; vertex < count; vertex++) {
				double[] p = point(vertex);
				// exact equality is the test: a weld that rounds first can collapse
				// two distinct points of the design, and the BRep is written from
				// the coordinates as stored
				long boxed = Double.valueOf(p[0]).hashCode() * 31 + Double.valueOf(p[1]).hashCode();
				boxed = boxed * 31 + Double.valueOf(p[2]).hashCode();
				Integer same = byPoint.get(boxed);
				if (same != null && point(same)[0] == p[0] && point(same)[1] == p[1] && point(same)[2] == p[2]) {
					canonical[vertex] = same;
					duplicateReferences++;
				} else
					byPoint.put(boxed, vertex);
			}
			if (duplicateReferences == 0)
				return;
			for (int t = 0; t < triangles.size(); t++) {
				int[] triangle = triangles.get(t);
				triangles.set(t, new int[]{canonical[triangle[0]], canonical[triangle[1]], canonical[triangle[2]]});
			}
			Log.debug("StepBRepWrite: renamed " + duplicateReferences + " exact-duplicate point reference(s)");
		}

		/**
		 * The vertices whose other faces need them as corners, readable as lying on
		 * a loop segment of this one: a spatial hash of every vertex of the mesh,
		 * so a segment can ask which points lie along it without scanning them all.
		 */
		private final Map<Long, List<Integer>> byCell = new HashMap<>();

		/** The junction index's cell size: a hundredth of the model's extent, far
		 * coarser than the tolerances, so a segment query walks a few dozen cells
		 * rather than a few million. */
		private double junctionCellSize;

		private void indexVertices() {
			junctionCellSize = Math.max(planeTolerance * 2, diagonal(points) / 100);
			int count = points.length / 3;
			for (int vertex = 0; vertex < count; vertex++) {
				double[] p = point(vertex);
				byCell.computeIfAbsent(cell(Math.round(p[0] / junctionCellSize), Math.round(p[1] / junctionCellSize),
						Math.round(p[2] / junctionCellSize)), unused -> new ArrayList<>()).add(vertex);
			}
		}

		/**
		 * The loop's segments expanded through the vertices other faces need on them:
		 * a T-junction. A neighbouring face's outline can meet this face's along a
		 * segment of it in several of the mesh's own edges, where this face's
		 * triangles were merged and the neighbour's were not. The written loop names
		 * the segment as one edge, the neighbour names its pieces, and a reader sews
		 * the shell by matching edges one to one — which the two never do. So every
		 * loop is written through the points the neighbours carry, and the edges
		 * pair one to one again.
		 */
		private List<Integer> throughJunctions(List<Integer> loop, Set<Integer> ownBoundaryVertices) {
			List<Integer> out = new ArrayList<>(loop.size());
			for (int at = 0; at < loop.size(); at++) {
				int a = loop.get(at);
				int b = loop.get((at + 1) % loop.size());
				// the pieces list already begins with the segment's first end, so it is
				// appended whole: adding the end here as well repeated every vertex
				List<Integer> pieces = edgePieces(a, b, ownBoundaryVertices);
				out.addAll(pieces.subList(0, pieces.size() - 1));
			}
			// consecutive inserts that landed on the same point collapse to one
			// A point that appears twice in a row is one point. Only equal INDICES are
			// merged: two different indices are two different corners of the surface,
			// and merging them by distance dropped one off the loop. The face then owed
			// a vertex it did not name, was refused, and its whole coplanar region
			// shattered into single triangles
			List<Integer> cleaned = new ArrayList<>(out.size());
			for (int vertex : out)
				if (cleaned.isEmpty() || cleaned.get(cleaned.size() - 1) != vertex)
					cleaned.add(vertex);
			if (cleaned.size() > 1 && cleaned.get(0).equals(cleaned.get(cleaned.size() - 1)))
				cleaned.remove(cleaned.size() - 1);
			return cleaned;
		}

		/** True when two vertices name the same point within the plane tolerance. */
		private boolean coincides(int a, int b) {
			return distance(point(a), point(b)) <= planeTolerance;
		}

		/** A loop's directed edges as written, through the loop's own points. */
		private static Set<Long> walkedEdges(List<Integer> loop) {
			Set<Long> edges = new LinkedHashSet<>();
			for (int i = 0; i < loop.size(); i++)
				edges.add(directed(loop.get(i), loop.get((i + 1) % loop.size())));
			return edges;
		}

		/**
		 * The undirected boundary edges of every group: the edges a written face can
		 * name. An edge two triangles of one group share cancels to interior, so it
		 * is on no face's boundary and no written loop will ever carry it; the set
		 * is what decides whether a junction point's piece segments will actually be
		 * named by the faces across them.
		 */
		private Set<Long> regionBoundaryEdges;

		private boolean isPieceEdge(int a, int b) {
			if (regionBoundaryEdges == null) {
				Set<Long> edges = new HashSet<>();
				for (List<Integer> group : faces)
					for (int[] edge : boundary(group))
						edges.add(key(edge[0], edge[1]));
				regionBoundaryEdges = edges;
			}
			return regionBoundaryEdges.contains(key(a, b));
		}

		/**
		 * The junction points a segment splits through, cached: the answer depends
		 * on the segment alone, and a long segment is asked twice — once when a
		 * face's loop is written, once when the same edge is checked for it.
		 */
		private Map<Long, List<Integer>> splitMiddlesCache;

		private List<Integer> splitMiddles(int a, int b) {
			long segment = directed(a, b);
			List<Integer> cached = splitMiddlesCache == null ? null : splitMiddlesCache.get(segment);
			if (cached != null)
				return cached;
			List<Integer> middles = new ArrayList<>();
			for (int middle : onSegment(a, b))
				if (isPieceEdge(a, middle) && isPieceEdge(middle, b))
					middles.add(middle);
			if (splitMiddlesCache == null)
				splitMiddlesCache = new HashMap<>();
			splitMiddlesCache.put(segment, middles);
			return middles;
		}

		/**
		 * The vertices that lie on the segment between two points, in order from the
		 * first towards the second. A vertex counts when it is within the plane
		 * tolerance of the segment's line and between its ends; the query walks the
		 * hash cells the segment's box covers, so only nearby points are measured.
		 */
		private List<Integer> onSegment(int a, int b) {
			double[] pa = point(a);
			double[] pb = point(b);
			double dx = pb[0] - pa[0], dy = pb[1] - pa[1], dz = pb[2] - pa[2];
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (length == 0)
				return List.of();
			double tolerance = junctionCellSize;
			// walk the cells the segment passes through, one per step of the index's
			// cell size: a query bounded by the segment's own length in cells
			long steps = (long) Math.ceil(length / tolerance) + 1;
			List<Integer> on = new ArrayList<>();
			Set<Integer> seen = new HashSet<>();
			for (long step = 0; step <= steps; step++) {
				double t = (double) step / steps;
				double px = pa[0] + t * dx, py = pa[1] + t * dy, pz = pa[2] + t * dz;
				long cx = Math.round(px / tolerance);
				long cy = Math.round(py / tolerance);
				long cz = Math.round(pz / tolerance);
				// a junction lies on the line, so its own cell catches it; the ring
				// only matters at the two ends, where rounding can push the point
				// one cell over
				int ring = step == 0 || step == steps ? 1 : 0;
				for (long ox = -ring; ox <= ring; ox++)
					for (long oy = -ring; oy <= ring; oy++)
						for (long oz = -ring; oz <= ring; oz++) {
							List<Integer> cellList = byCell.get(cell(cx + ox, cy + oy, cz + oz));
							if (cellList == null)
								continue;
							for (int vertex : cellList) {
								if (vertex == a || vertex == b || !seen.add(vertex))
									continue;
								double[] q = point(vertex);
								double u = ((q[0] - pa[0]) * dx + (q[1] - pa[1]) * dy + (q[2] - pa[2]) * dz)
										/ (length * length);
								if (u <= 0 || u >= 1)
									continue;
								double qx = pa[0] + u * dx, qy = pa[1] + u * dy, qz = pa[2] + u * dz;
								double off = Math.sqrt((q[0] - qx) * (q[0] - qx) + (q[1] - qy) * (q[1] - qy)
										+ (q[2] - qz) * (q[2] - qz));
								if (off <= planeTolerance)
									on.add(vertex);
							}
						}
			}
			on.sort((x, y) -> {
				double[] px = point(x), py = point(y);
				double dxa = px[0] - pa[0], dya = px[1] - pa[1], dza = px[2] - pa[2];
				double dxb = py[0] - pa[0], dyb = py[1] - pa[1], dzb = py[2] - pa[2];
				return Double.compare(dxa * dxa + dya * dya + dza * dza, dxb * dxb + dyb * dyb + dzb * dzb);
			});
			return on;
		}

		/**
		 * Takes the source triangles exactly as the CSG object holds them. The mesh's
		 * own cleanup is Manifold simplify's work, done on the CSG before this writer
		 * sees it; the writer neither welds vertices nor moves nor drops triangles.
		 *
		 * <p>
		 * A triangle naming one vertex twice has no edge to pair and no area to face,
		 * so no face can describe it; it is the one thing that has to be left out.
		 */
		private void prepare(long[] sourceTriangles) {
			if (sourceTriangles == null)
				return;
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
			triangles.addAll(raw);
		}

		/**
		 * The mesh as the CSG object holds it.
		 */

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

		/** The triangles that use a vertex as a corner. */
		private Map<Integer, List<Integer>> byCornerIndex;

		private List<Integer> byCorner(int vertex) {
			if (byCornerIndex == null) {
				byCornerIndex = new HashMap<>();
				for (int index = 0; index < triangles.size(); index++)
					for (int corner : triangles.get(index))
						byCornerIndex.computeIfAbsent(corner, unused -> new ArrayList<>()).add(index);
			}
			return byCornerIndex.getOrDefault(vertex, List.of());
		}

		private static double distance(double[] a, double[] b) {
			double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		/** The total area the given triangles cover. */
		private double areaOf(List<int[]> pieces) {
			double total = 0;
			for (int[] piece : pieces)
				total += triangleArea(point(piece[0]), point(piece[1]), point(piece[2]));
			return total;
		}

		/** The surface the given triangles cover, so a repair can be held to it. */
		private double surfaceArea(List<int[]> some) {
			return areaOf(some);
		}


		/** The area of one triangle given by three points. */
		private static double triangleArea(double[] a, double[] b, double[] c) {
			double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
			double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			return Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
		}

		/** A point's coordinates as text, so points and triangles compare as sets. */
		private static String coordinates(double[] p) {
			return p[0] + "," + p[1] + "," + p[2];
		}

		// --- grouping into faces ---

		/**
		 * A point's junction links, to the vertices naming the same point of the
		 * surface under the junction tolerance. Built once from the whole mesh:
		 * this is the difference a weld under the stricter snap tolerance cannot
		 * see, and the difference that splits a coplanar face at a seam into a
		 * strip of per-triangle faces.
		 */
		private Map<Integer, List<Integer>> junctionLinks;

		private Map<Integer, List<Integer>> junctionLinks() {
			if (junctionLinks != null)
				return junctionLinks;
			junctionLinks = new HashMap<>();
			Map<Long, Integer> owner = new HashMap<>();
			for (int vertex = 0; vertex < points.length / 3; vertex++) {
				double[] p = point(vertex);
				long cx = Math.round(p[0] / junctionCellSize);
				long cy = Math.round(p[1] / junctionCellSize);
				long cz = Math.round(p[2] / junctionCellSize);
				for (int dx = -1; dx <= 1; dx++)
					for (int dy = -1; dy <= 1; dy++)
						for (int dz = -1; dz <= 1; dz++) {
							Integer other = owner.get(cell(cx + dx, cy + dy, cz + dz));
							if (other == null || other == vertex)
								continue;
							// the gate is the point tolerance, not the cell size: the
							// cell is coarse so the hash is cheap, and linking every
							// vertex of a cell would weld points the design keeps apart
							if (distance(p, point(other)) > planeTolerance)
								continue;
							junctionLinks.computeIfAbsent(other, unused -> new ArrayList<>()).add(vertex);
							junctionLinks.computeIfAbsent(vertex, unused -> new ArrayList<>()).add(other);
						}
				owner.putIfAbsent(cell(cx, cy, cz), vertex);
			}
			int links = 0;
			for (List<Integer> list : junctionLinks.values())
				links += list.size();
			Log.debug("StepBRepWrite: " + links / 2 + " junction link(s) across the weld tolerance");
			return junctionLinks;
		}

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
				List<Integer> face = new ArrayList<>();
				face.add(start);
				used[start] = true;

				// The face's plane is the plane its own triangles define, re-derived as
				// the face grows. Measuring a candidate against the seed triangle alone
				// lets its rounding accumulate along the face until the far end falls
				// outside the tolerance and the face stops after a handful of triangles;
				// measuring against the region's own fitted plane keeps every part of the
				// face equally close to it, which is exactly what a reader's planarity
				// test asks
				double[] origin = point(triangles.get(start)[0]);
				Vector3d plane = normal;
				List<Integer> frontier = new ArrayList<>();
				frontier.add(start);
				while (!frontier.isEmpty()) {
					int current = frontier.remove(frontier.size() - 1);
					int[] triangle = triangles.get(current);
					for (int i = 0; i < 3; i++)
						for (int index : byEdge.get(key(triangle[i], triangle[(i + 1) % 3]))) {
							if (used[index])
								continue;
							Vector3d candidate = normal(triangles.get(index));
							if (candidate == null || candidate.dot(plane) <= 0)
								continue;
							if (!coplanar(triangles.get(index), origin, plane))
								continue;
							used[index] = true;
							face.add(index);
							frontier.add(index);
							// refit the plane as the face grows. The fit is a running sum
							// over small triangles, so it is re-derived from the region
							// only when the plane has moved enough to matter — which is
							// rare, because adding one triangle to a face moves its fitted
							// plane by a fraction of that triangle's own share
							Vector3d fitted = regionNormal(face);
							if (fitted != null && fitted.dot(plane) < 1 - 1e-9) {
								plane = fitted;
								origin = regionCentre(face);
							}
						}
					// a seam welds this face's surface to itself: the same point is
					// stored as two vertices within the junction tolerance, one naming
					// each sheet's strip. The thin tile through either naming is this
					// face's triangle — splitting at the weld leaves the tile's
					// outline unable to pair with the sheet on either side of it.
					// A triangle joins here when it touches a junction twin of one of
					// this face's corners, and lies in this face's plane facing the
					// same way: the same test an edge-adjacent neighbour passes.
					for (int corner : triangle)
						for (int twin : junctionLinks().getOrDefault(corner, List.of()))
							for (int index : byCorner(twin)) {
								if (used[index])
									continue;
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
				if (!face.isEmpty())
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

		/** The region's area-weighted normal, so one sliver cannot tilt the fused face. */
		private Vector3d regionNormal(List<Integer> region) {
			Vector3d sum = new Vector3d(0, 0, 0);
			for (int index : region) {
				int[] triangle = triangles.get(index);
				Vector3d normal = normal(triangle);
				if (normal == null)
					continue;
				double area = triangleArea(point(triangle[0]), point(triangle[1]), point(triangle[2]));
				sum = sum.plus(normal.times(area));
			}
			return sum.magnitude() == 0 ? null : sum.normalized();
		}

		/** The region's area-weighted centroid, the point its plane passes through. */
		private double[] regionCentre(List<Integer> region) {
			double weight = 0;
			double x = 0, y = 0, z = 0;
			for (int index : region) {
				int[] triangle = triangles.get(index);
				double[] a = point(triangle[0]), b = point(triangle[1]), c = point(triangle[2]);
				double area = triangleArea(a, b, c);
				if (area == 0)
					continue;
				weight += area;
				x += area * (a[0] + b[0] + c[0]) / 3;
				y += area * (a[1] + b[1] + c[1]) / 3;
				z += area * (a[2] + b[2] + c[2]) / 3;
			}
			if (weight == 0) {
				int[] first = triangles.get(region.get(0));
				return point(first[0]);
			}
			return new double[]{x / weight, y / weight, z / weight};
		}

		private long face(StepEmitter emitter, List<Integer> region, List<List<Integer>> loops, String name) {
			Vector3d normal = regionNormal(region);
			// a face of no area still needs a plane, or it cannot be written; any
			// direction will do, since the loop encloses nothing
			if (normal == null)
				normal = new Vector3d(0, 0, 1);
			// the plane must pass through the face, not the origin, or the face does
			// not lie on it and the model has no volume. Its position is the region's
			// area-weighted centroid: the fused triangles are planar only to the
			// precision the mesh stores, so the plane that keeps the enclosed volume is
			// the one the deviations are centred on, not one triangle's own.
			long plane = plane(emitter, regionCentre(region), normal, name);

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
		private boolean representsExactly(List<Integer> region, List<List<Integer>> loops) {
			Vector3d normal = normal(triangles.get(region.get(0)));
			if (normal == null || loops.isEmpty())
				return false;
			double trianglesArea = 0;
			for (int index : region)
				trianglesArea += Math.abs(area(asLoop(triangles.get(index)), normal)) / 2;

			// the outer loop, less every hole inside it
			double loopsArea = Math.abs(area(loops.get(0), normal)) / 2;
			for (int i = 1; i < loops.size(); i++)
				loopsArea -= Math.abs(area(loops.get(i), normal)) / 2;

			// Any real difference is a hole the file would not describe, or surface it
			// would add. The tolerance must sit above the arithmetic's own noise: a
			// polygon's area is a sum of products of coordinates, so its relative error
			// grows with the coordinate magnitudes, and a model a hundred millimetres
			// across loses about ten parts in a million of a sliver's own area — which
			// is exactly the scale these two measures are compared at.
			double magnitude = Math.max(1, Math.abs(trianglesArea));
			double tolerance = Math.max(1e-9, magnitude * 1e-6);
			return Math.abs(trianglesArea - loopsArea) <= tolerance;
		}

		/**
		 * The loops a region may be written with, or null when it must fall back to one
		 * face per triangle.
		 *
		 * <p>
		 * A region can be written as one face only when its loops enclose exactly the
		 * area its triangles cover, and when every one of those triangles lies inside
		 * the outer loop and outside the holes. Either test failing means the face would
		 * describe a surface the mesh does not have, and a reader would show a gap where
		 * the triangles it did not cover used to be.
		 */
		/**
		 * How the mesh's triangles are to be written: one entry per written face.
		 *
		 * <p>
		 * A face either stitches a coplanar region, in which case {@code loops} is its
		 * boundary, or covers a single triangle, in which case {@code loops} is null and
		 * {@code region} holds just that triangle. The writer and {@link #faces} both
		 * take their plan from here, so neither can describe a shape the other does not.
		 *
		 * <p>
		 * A region is stitched only when its boundary is exactly correct and a reader
		 * triangulating that boundary gets the region's own triangles back. If the part
		 * as a whole still would not read back as its source mesh, {@link #solid}
		 * rewrites it one triangle per face instead, which is exact by construction.
		 */
		private List<Planned> planCache;

		private List<Planned> plan() {
			if (planCache != null)
				return planCache;
			List<Planned> plan = new ArrayList<>();
			for (List<Integer> group : faces) {
				// a coplanar group can hold several disconnected regions, and a STEP face
				// can only bound one: its first loop is the outline and the rest are holes
				// cut out of it. So each region is written as a face of its own.
				for (List<Integer> region : regions(group)) {
					List<List<Integer>> loops = stitchable(region);
					if (loops != null) {
						plan.add(new Planned(region, loops));
					} else {
						for (int index : region) {
							// one face per triangle, exact by construction; a triangle of
							// no area is the mesh's own, and its loop still pairs with its
							// neighbours' loops, so the shell stays closed around it
							plan.add(new Planned(List.of(index), null));
						}
					}
				}
			}
			try {
				java.nio.file.Files.writeString(java.nio.file.Path.of("build/g2.txt"),
						"regions=" + DBG_REGIONS + " multi=" + DBG_MULTI + " emptyLoops=" + DBG_EMPTY_LOOPS
								+ " emptyLoopsMulti=" + DBG_EMPTY_LOOPS_MULTI + " planarNull=" + DBG_PLANAR_NULL
								+ " planned=" + plan.size() + System.lineSeparator(),
						java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			} catch (Exception ignored) {
			}
			Log.debug("StepBRepWrite: stitch failures " + stitchFailedNoNormal + " no-normal, " + stitchFailedMisses
					+ " misses-vertices, " + stitchFailedBounds + " bounds-off, " + stitchFailedRepresents
					+ " representation-off");
			planCache = plan;
			return plan;
		}


		/**
		 * The plan, held to the source: a plan that would not read back as the mesh's
		 * own surface is replaced by one face per triangle, which reproduces it by
		 * construction.
		 *
		 * <p>
		 * Stitching is an optimisation — it makes the BRep pleasant to select and edit —
		 * but a stitched face is only correct if a reader builds the mesh's surface from
		 * it. So the plan is measured against the source before it is used, and the
		 * exact-but-plain plan is the fallback. A gap in the output is never accepted.
		 */
		private List<Planned> settledPlan() {
			List<Planned> plan = plan();
			plan = pairedPlan(plan);
			Log.debug("StepBRepWrite: plan has " + plan.size() + " faces");
			return plan;
		}

		/**
		 * The plan, with the faces whose loops pair worse than their own triangles
		 * written as their triangles instead.
		 *
		 * <p>
		 * A reader closes the shell by matching each written edge with the same edge
		 * reversed on the face across it. A fused face can fail that where the
		 * neighbour names a segment in pieces and this face names it whole, or where
		 * a fold's two sheets meet along an edge a third face also carries. Its own
		 * triangles pair by index where the mesh is edge-manifold, so writing them
		 * is the exact alternative.
		 *
		 * <p>
		 * A face is judged against the rest of the plan, which does not depend on
		 * it: the loops are kept when, over the edges they and their triangles
		 * touch, they leave fewer unpaired edges than the triangles would. A mesh
		 * whose own indices do not pair is not carved into triangles for nothing.
		 */
		private List<Planned> pairedPlan(List<Planned> plan) {
			Map<Long, Integer> rest = new HashMap<>();
			for (Planned planned : plan) {
				if (planned.loops == null)
					for (int index : planned.region)
						countEdges(triangleLoop(index), rest);
				else
					for (List<Integer> loop : planned.loops)
						countEdges(loop, rest);
			}

			List<Planned> settled = new ArrayList<>(plan.size());
			int split = 0;
			for (Planned planned : plan) {
				if (planned.loops == null || !pairsBetterAsTriangles(planned, rest)) {
					settled.add(planned);
					continue;
				}
				for (int index : planned.region)
					settled.add(new Planned(List.of(index), null));
				split++;
			}
			if (split > 0)
				Log.debug("StepBRepWrite: " + split + " fused face(s) of " + plan.size()
						+ " written as their triangles, which pair with the faces around them");
			return settled;
		}

		/** Counts the loop's directed edges into the map. */
		private static void countEdges(List<Integer> loop, Map<Long, Integer> into) {
			for (int i = 0; i < loop.size(); i++)
				into.merge(directed(loop.get(i), loop.get((i + 1) % loop.size())), 1, Integer::sum);
		}

		/**
		 * True when the region's triangles, written one per face, leave fewer
		 * unpaired edges against the rest of the plan than this face's loops do.
		 * Only the edges the two writings differ on can move the count, so the
		 * comparison walks those alone.
		 */
		private boolean pairsBetterAsTriangles(Planned planned, Map<Long, Integer> rest) {
			Map<Long, Integer> loops = new HashMap<>();
			for (List<Integer> loop : planned.loops)
				countEdges(loop, loops);
			Map<Long, Integer> triangles = new HashMap<>();
			for (int index : planned.region)
				countEdges(triangleLoop(index), triangles);

			Set<Long> touched = new LinkedHashSet<>(loops.keySet());
			touched.addAll(triangles.keySet());
			int before = 0;
			int after = 0;
			for (long edge : touched) {
				long reverse = directed((int) (edge & 0xffffffffL), (int) (edge >> 32));
				int loopsForward = rest.getOrDefault(edge, 0) + loops.getOrDefault(edge, 0);
				int loopsBackward = rest.getOrDefault(reverse, 0) + loops.getOrDefault(reverse, 0);
				int trianglesForward = rest.getOrDefault(edge, 0) + triangles.getOrDefault(edge, 0);
				int trianglesBackward = rest.getOrDefault(reverse, 0) + triangles.getOrDefault(reverse, 0);
				if (loopsForward != loopsBackward)
					before++;
				if (trianglesForward != trianglesBackward)
					after++;
			}
			return after < before;
		}

		/**
		 * The mesh whose plan to write from: this one when its plan stitches, or a
		 * snapped copy when the first repair attempt failed.
		 *
		 * <p>
		 * The first stage repairs a region by stitching its coplanar triangles into
		 * one face, and falls back to one face per triangle for a region the stitches
		 * cannot bound. That fallback is exact but hostile to a BRep reader, which
		 * must build thousands of single-triangle faces and sew them. So a plan with
		 * fallbacks moves to the next stage: the mesh's own point index is walked and
		 * every reference to a point within the snap tolerance of another is collapsed
		 * onto that point's index, and every triangle carrying the snapped index is
		 * updated to it. A shared edge the two triangles of a face named by two
		 * different indices then has one name, the loop walk sees it pair, and the
		 * region stitches.
		 *
		 * <p>
		 * The snapped plan is used only when it stitches strictly more faces than the
		 * first stage did; a snap that stitched nothing new repaired nothing, and the
		 * mesh goes out as the exact plan left it.
		 */
		private Mesh settledMesh() {
			List<Planned> plan = plan();
			int fellBack = 0;
			for (Planned planned : plan)
				if (planned.loops == null)
					fellBack++;
			if (Boolean.getBoolean("step.snap.disabled"))
				return this;
			if (fellBack == 0)
				return this;

			Log.debug("StepBRepWrite: " + fellBack + " of " + plan.size()
					+ " planned face(s) fell back to single triangles; snapping near-duplicate points");
			Mesh snapped = snappedMesh();
			if (snapped == null)
				return this;

			List<Planned> snappedPlan = snapped.plan();
			int stitchedHere = plan.size() - fellBack;
			int stitchedThere = 0;
			for (Planned planned : snappedPlan)
				if (planned.loops != null)
					stitchedThere++;
			if (stitchedThere <= stitchedHere) {
				Log.debug("StepBRepWrite: the snap stitched " + stitchedThere + " of " + snappedPlan.size()
						+ " face(s), no better than the first stage's " + stitchedHere + "; keeping this mesh");
				return this;
			}
			Log.debug("StepBRepWrite: the snapped mesh stitches " + stitchedThere + " of " + snappedPlan.size()
					+ " face(s) over this mesh's " + stitchedHere + "; writing from the snapped mesh");
			return snapped;
		}

		/**
		 * The mesh with its near-duplicate points collapsed, or null when there is
		 * nothing within the snap tolerance to collapse.
		 *
		 * <p>
		 * The snap walks the point index array, unions every point within the snap
		 * tolerance of an already-seen point onto it through a union-find over the
		 * indices, and then rewrites every triangle through the representatives: a
		 * point that was stored under several indices ends referenced by one of them,
		 * and each triangle's corner is updated to the index that represents it. A
		 * triangle naming one index twice after the snap has no edge to pair and is
		 * dropped, exactly as {@link #prepare} drops one from the source.
		 */
		private Mesh snappedMesh() {
			int vertexCount = points.length / 3;
			if (vertexCount == 0)
				return null;

			// the snap tolerance is the plane tolerance, the same scale the grouping
			// holds coplanarity to: close enough that a reader merges the pair, far
			// enough above the doubles' own rounding that designed points stay apart
			double snapTolerance = planeTolerance;
			int[] parent = new int[vertexCount];
			for (int vertex = 0; vertex < vertexCount; vertex++)
				parent[vertex] = vertex;

			// walk the points once, hashing each cell and unioning with any earlier
			// point of the same or a neighbouring cell that the tolerance covers
			Map<Long, Integer> byCell = new HashMap<>();
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				double[] p = point(vertex);
				long cx = Math.round(p[0] / snapTolerance);
				long cy = Math.round(p[1] / snapTolerance);
				long cz = Math.round(p[2] / snapTolerance);
				for (int dx = -1; dx <= 1; dx++)
					for (int dy = -1; dy <= 1; dy++)
						for (int dz = -1; dz <= 1; dz++) {
							Integer other = byCell.get(cell(cx + dx, cy + dy, cz + dz));
							if (other == null || distance(p, point(other)) > snapTolerance)
								continue;
							parent[find(parent, other)] = find(parent, vertex);
						}
				byCell.putIfAbsent(cell(cx, cy, cz), vertex);
			}

			// each cluster is represented by its lowest index, so the representative
			// is stable no matter what order the unions ran in
			int[] smallest = new int[vertexCount];
			java.util.Arrays.fill(smallest, -1);
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				int root = find(parent, vertex);
				if (smallest[root] == -1 || vertex < smallest[root])
					smallest[root] = vertex;
			}
			int[] snapped = new int[vertexCount];
			int merged = 0;
			for (int vertex = 0; vertex < vertexCount; vertex++) {
				snapped[vertex] = smallest[find(parent, vertex)];
				if (snapped[vertex] != vertex)
					merged++;
			}
			if (merged == 0)
				return null;

			// every triangle is updated through the representatives: each corner that
			// named a snapped-away index now names the point it was snapped onto
			List<int[]> snappedTriangles = new ArrayList<>(triangles.size());
			int dropped = 0;
			for (int[] triangle : triangles) {
				int a = snapped[triangle[0]];
				int b = snapped[triangle[1]];
				int c = snapped[triangle[2]];
				if (a == b || b == c || c == a) {
					dropped++;
					continue;
				}
				snappedTriangles.add(new int[]{a, b, c});
			}
			Log.debug("StepBRepWrite: snapped " + merged + " point reference(s) onto " + (vertexCount - merged)
					+ " point(s); " + dropped + " triangle(s) collapsed to a line and dropped");
			if (snappedTriangles.isEmpty())
				return null;

			long[] flat = new long[snappedTriangles.size() * 3];
			for (int i = 0; i < snappedTriangles.size(); i++) {
				int[] triangle = snappedTriangles.get(i);
				flat[i * 3] = triangle[0];
				flat[i * 3 + 1] = triangle[1];
				flat[i * 3 + 2] = triangle[2];
			}
			// the point array is carried whole: the dropped indices are simply no
			// longer referenced, and their coordinates cost nothing
			return new Mesh(points, flat);
		}


		/**
		 * The plan, checked against the source mesh the file is written from.
		 *
		 * <p>
		 * The source is the ground truth: every triangle of it must be under a face, and
		 * no surface may be invented. {@link #settledPlan} already proves the faces read
		 * back as the mesh's own surface; this holds the mesh itself to the source, which
		 * is what catches a repair that quietly dropped a triangle before any face was
		 * built. The tolerance is on the mesh's own area, so a single dropped sliver of
		 * a mesh this size is still far outside it.
		 */
		private List<Planned> verifiedPlan(long[] sourceTriangles, String name) {
			List<Planned> plan = settledPlan();
			double sourceArea = 0;
			for (int t = 0; t + 2 < sourceTriangles.length; t += 3) {
				int[] triangle = {(int) sourceTriangles[t], (int) sourceTriangles[t + 1], (int) sourceTriangles[t + 2]};
				if (triangle[0] == triangle[1] || triangle[1] == triangle[2] || triangle[2] == triangle[0])
					continue;
				Vector3d normal = normal(triangle);
				if (normal != null)
					sourceArea += Math.abs(area(asLoop(triangle), normal)) / 2;
			}
			double keptArea = surfaceArea(triangles);
			if (keptArea < sourceArea - Math.max(1e-9, sourceArea * 1e-6))
				throw new IllegalStateException("STEP export of " + name + " covers " + keptArea + " of the source's "
						+ sourceArea + " of surface, so some of its " + (sourceTriangles.length / 3)
						+ " triangles have no face: " + describeEdges());
			return plan;
		}

		/** The loops the given plan would write, in the form the reader check takes. */
		private List<List<List<Integer>>> loopsOf(List<Planned> plan) {
			List<List<List<Integer>>> written = new ArrayList<>();
			for (Planned planned : plan)
				written.add(planned.loops != null ? planned.loops : List.of(triangleLoop(planned.region.get(0))));
			return written;
		}

		/** One written face: the triangles it stands for and its loops, or null for one triangle. */
		private static final class Planned {
			private final List<Integer> region;
			private final List<List<Integer>> loops;

			private Planned(List<Integer> region, List<List<Integer>> loops) {
				this.region = region;
				this.loops = loops;
			}
		}

		private int stitchFailedNoNormal;
		private int stitchFailedMisses;
		private int stitchFailedBounds;
		private int stitchFailedRepresents;
	private int DBG_REGIONS, DBG_MULTI, DBG_EMPTY_LOOPS, DBG_EMPTY_LOOPS_MULTI, DBG_PLANAR_NULL;


		private List<List<Integer>> stitchable(List<Integer> region) {
			DBG_REGIONS++;
			if (region.size() > 1)
				DBG_MULTI++;
			if (region.isEmpty()) {
				stitchFailedNoNormal++;
				return null;
			}
			if (normal(triangles.get(region.get(0))) == null) {
				stitchFailedNoNormal++;
				return null;
			}
			List<List<Integer>> loops = loops(region);
			// a face's first loop bounds its outline and the rest are holes, so the
			// written edges must all pair with the region's neighbours: an edge the
			// expansion added or lost is not the mesh's own boundary. Checking the raw
			// walk against the raw boundary keeps the test and the repair consistent;
			// checking the expanded loops here would reject every region the
			// junction repair served, which is the majority near a welded seam.
			Set<Long> walked = new LinkedHashSet<>();
			Set<Long> repeated = new LinkedHashSet<>();
			for (List<Integer> loop : loops)
				for (long edge : walkedEdges(loop))
					if (!walked.add(edge))
						repeated.add(edge);
			// How exactly the loops pair with the faces around them is a measurement,
			// not a reason to break the face up: a fused face covers its region's
			// surface exactly, where that region's triangles cover the same surface in
			// thousands of pieces. The one test that must hold is that the loops really
			// enclose the region's area — a loop enclosing other area describes a
			// surface the mesh does not have, and a reader refuses it.
			if (!repeated.isEmpty()) {
				stitchFailedBounds++;
			}
			if (!carriesEveryVertex(region, loops)) {
				stitchFailedMisses++;
			}
			if (!boundsExactly(region, loops))
				stitchFailedBounds++;
			if (!representsExactly(region, loops)) {
				stitchFailedRepresents++;
			}
			// a face is a plane: every point of its loops must lie on it, or a reader
			// holding the face to its own plane reads a different shape than the mesh
			if (loops.isEmpty()) {
				DBG_EMPTY_LOOPS++;
				if (region.size() > 1)
					DBG_EMPTY_LOOPS_MULTI++;
			}
			if (!loopsArePlanar(loops, region)) {
				stitchFailedBounds++;
				DBG_PLANAR_NULL++;
				return null;
			}
			return loops;
		}


		/** A triangle's three vertices in a fixed order, so triangles compare as sets. */
		private static String canonical(int a, int b, int c) {
			int[] sorted = {a, b, c};
			java.util.Arrays.sort(sorted);
			return sorted[0] + "," + sorted[1] + "," + sorted[2];
		}

		/**
		* True when the loops name every vertex of the region that a face outside the
		* region also names.
		 *
		 * <p>
		 * A face is read back from its loops alone: a reader re-triangulates the
		* outline and can only ever emit the points the loops name. A vertex that only
		* the region's own triangles use is interior to the fused face — losing it
		* changes nothing, because no neighbour pairs across it. A vertex a
		* neighbouring face also names must survive on the outline, or that face has
		* nothing to pair with there and the shell stops being manifold.
		 */
		/**
		 * True when every point of the loops lies on the plane the region's own
		 * triangles define. The plane is the area-weighted one {@code face} writes,
		 * and the budget is the writer's plane tolerance: a face off its plane by
		 * more than that is not the surface the mesh has.
		 */
		private boolean loopsArePlanar(List<List<Integer>> loops, List<Integer> region) {
			Vector3d normal = regionNormal(region);
			if (normal == null)
				return false;
			double[] centre = regionCentre(region);
			for (List<Integer> loop : loops)
				for (int vertex : loop) {
					double[] p = point(vertex);
					double offset = (p[0] - centre[0]) * normal.x + (p[1] - centre[1]) * normal.y
							+ (p[2] - centre[2]) * normal.z;
					if (Math.abs(offset) > planeTolerance)
						return false;
				}
			return true;
		}

		private boolean carriesEveryVertex(List<Integer> region, List<List<Integer>> loops) {
			Set<Integer> outside = new LinkedHashSet<>();
			for (int[] edge : boundary(region)) {
				outside.add(edge[0]);
				outside.add(edge[1]);
			}
			Set<Integer> onLoops = new LinkedHashSet<>();
			for (List<Integer> loop : loops)
				onLoops.addAll(loop);
			for (int index : region)
				for (int corner : triangles.get(index))
					// a corner the outside names is a shared point the face must carry;
					// a corner only this region's triangles name is fusable interior
					if (outside.contains(corner) && !onLoops.contains(corner))
						return false;
			return true;
		}

		/**
		 * True when the loops walk exactly the region's own boundary edges, each once
		 * and in the direction a triangle uses it.
		 *
		 * <p>
		 * This is the exact statement of "the face covers the region": a region's
		 * surface is what its triangles enclose, and its outline is the mesh edges used
		 * by one of those triangles. If the loops are that same set of directed edges,
		 * they bound the same surface, with no tolerance involved. A loop is emitted as
		 * consecutive vertices, so its closing edge is implied and is compared too.
		 */
		private boolean boundsExactly(List<Integer> region, List<List<Integer>> loops) {
			if (loops.isEmpty())
				return false;
			// the wanted edges are the boundary as written: a boundary edge whose
			// segment the junction repair splits is wanted as its pieces, because
			// that is how this face's loop names it and how the face across it names
			// it back. The walk and the boundary are then read the same way.
			Map<Long, Integer> wanted = new HashMap<>();
			List<int[]> boundary = boundary(region);
			Set<Integer> ownBoundaryVertices = new HashSet<>();
			for (int[] e : boundary) {
				ownBoundaryVertices.add(e[0]);
				ownBoundaryVertices.add(e[1]);
			}
			for (int[] edge : boundary) {
				List<Integer> pieces = edgePieces(edge[0], edge[1], ownBoundaryVertices);
				for (int i = 0; i + 1 < pieces.size(); i++)
					wanted.merge(directed(pieces.get(i), pieces.get(i + 1)), 1, Integer::sum);
			}
			Map<Long, Integer> walked = new HashMap<>();
			for (List<Integer> loop : loops)
				for (int i = 0; i < loop.size(); i++) {
					long edge = directed(loop.get(i), loop.get((i + 1) % loop.size()));
					walked.merge(edge, 1, Integer::sum);
				}
			return walked.equals(wanted);
		}

		/**
		 * A segment as the written loop names it: the first end, then every junction
		 * point the segment splits through, then the second end.
		 *
		 * <p>
		 * A candidate only lands between the ends when the piece segments it would
		 * create are edges a face boundary actually holds — the boundaries the
		 * faces across this loop will name. A T-junction's pieces qualify; a point
		 * whose tile edges were fused away does not, and expanding there would split
		 * a segment whose neighbour names it whole, unpairing the two. A vertex this
		 * region's own walk already knows needs no insert; a point that coincides
		 * with either end is already the end under another index, and inserting it
		 * would write an edge of no length, which no neighbour can pair.
		 *
		 * <p>
		 * {@link #throughJunctions} applies this to a whole loop and
		 * {@link #boundsExactly} to one boundary edge, so the walk and the check that
		 * judges it read the boundary the same way.
		 */
		private List<Integer> edgePieces(int a, int b, Set<Integer> ownBoundaryVertices) {
			List<Integer> pieces = new ArrayList<>();
			pieces.add(a);
			for (int middle : splitMiddles(a, b))
				if (middle != a && middle != b && !ownBoundaryVertices.contains(middle) && !coincides(middle, a)
						&& !coincides(middle, b) && isPieceEdge(a, middle) && isPieceEdge(middle, b))
					pieces.add(middle);
			pieces.add(b);
			return pieces;
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
		private long triangleFace(StepEmitter emitter, int index, List<Integer> loop, String name) {
			int[] triangle = triangles.get(index);
			Vector3d normal = normal(triangle);
			// a triangle whose points are collinear encloses nothing, so its loop is
			// still writable and still pairs with its neighbours; any direction planes it
			if (normal == null)
				normal = new Vector3d(0, 0, 1);
			long plane = plane(emitter, point(triangle[0]), normal, name);
			// the loop as planned: the triangle's own walk, or the folded walk that
			// runs through a folded neighbour's tip — whose edges pair with the
			// neighbours across them
			long loopId = edgeLoop(emitter, loop);
			long bound = emitter.add("FACE_OUTER_BOUND('',#" + loopId + ",.T.)");
			return emitter.add("ADVANCED_FACE(" + quote(name) + ",(#" + bound + "),#" + plane + ",.T.)");
		}


		/** The face as data: its plane and its boundary loops of points. */
		private StepFace describe(List<Integer> region) {
			Vector3d normal = regionNormal(region);
			if (normal == null)
				normal = new Vector3d(0, 0, 1);
			List<List<double[]>> points = new ArrayList<>();
			for (List<Integer> loop : loops(region, normal)) {
				List<double[]> loopPoints = new ArrayList<>();
				for (int vertex : loop)
					loopPoints.add(point(vertex));
				points.add(loopPoints);
			}
			return new StepFace(regionCentre(region), normal, points);
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
			Set<Integer> ownBoundaryVertices = new HashSet<>();
			for (int[] edge : boundary(face)) {
				ownBoundaryVertices.add(edge[0]);
				ownBoundaryVertices.add(edge[1]);
			}
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
				int[] walked = null;
				while (at != null && !Boolean.TRUE.equals(visited.get(key(at[0], at[1])))) {
					visited.put(key(at[0], at[1]), Boolean.TRUE);
					loop.add(at[0]);
					walked = at;
					at = next(byStart, visited, at, normal);
				}
				// Only a walk that comes back to the edge it started from is a cycle; the
				// loop is emitted as consecutive vertices, so its last edge is implied.
				// A walk that dead-ends would be closed by that implied edge, which no
				// triangle of the face has: the file would name an edge that bounds
				// nothing and leave the edges it did walk unmatched, which is a gap.
				// Dropping such a walk instead makes the region fall back to a face per
				// triangle, so its surface is still covered.
				boolean closed = walked != null && walked[1] == edge[0];
				// The area a cycle encloses only decides which bound is the outer one.
				if (closed && distinct(loop) >= 3)
					loops.add(loop);
			}

			loops.sort((a, b) -> Double.compare(Math.abs(area(b, normal)), Math.abs(area(a, normal))));

			// A face's first loop is its outer bound and every other loop is a hole cut
			// out of it. That is only true when all the cycles lie inside one another.
			// A coplanar group can also hold two separate regions, which meet at a
			// vertex or not at all; writing the second as a hole would subtract it, and
			// that is a missing face. So the cycles are sorted into regions first, and
			// each region becomes a face of its own.
			List<List<Integer>> expanded = new ArrayList<>(loops.size());
			for (List<Integer> loop : loops)
				expanded.add(throughJunctions(loop, ownBoundaryVertices));
			return expanded;
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

		/** The root of a vertex in the weld's own array, with its paths compressed. */
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

		/**
		 * The directed edges that are the face's outline: those not cancelled by their
		 * opposite within the face.
		 *
		 * <p>
		 * An edge seen twice within one face in OPPOSITE directions is interior to the
		 * face and off its boundary. An edge seen twice in the SAME direction is the
		 * fold line where two sheets of the part meet inside the face: both walks are
		 * real surface, and dropping the pair would leave the walk no way to pair the
		 * sheets' other edges across it, so both stay.
		 */
		private List<int[]> boundary(List<Integer> face) {
			Map<Long, List<int[]>> once = new LinkedHashMap<>();
			Map<Long, Boolean> repeated = new HashMap<>();
			for (int index : face)
				collectEdges(triangles.get(index), once, repeated);

			List<int[]> boundary = new ArrayList<>();
			// every remaining entry is on the boundary: a single walk, or a fold's
			// two same-direction walks that each pair with its own neighbour
			for (List<int[]> walks : once.values())
				boundary.addAll(walks);
			return boundary;
		}

		/**
		 * Records the triangle's directed edges. An undirected pair seen a second
		 * time is resolved by direction: opposite sightings cancel — the edge is
		 * interior to the face and off its boundary, so its first sighting is taken
		 * back out — while same-direction sightings are a fold inside the face, and
		 * both walks stay on the boundary, each pairing with its own neighbour
		 * across the fold.
		 */
		private void collectEdges(int[] triangle, Map<Long, List<int[]>> once, Map<Long, Boolean> repeated) {
			int[][] pairs = {{triangle[0], triangle[1]}, {triangle[1], triangle[2]}, {triangle[2], triangle[0]}};
			for (int[] pair : pairs) {
				long key = key(pair[0], pair[1]);
				if (Boolean.TRUE.equals(repeated.get(key)))
					continue;
				List<int[]> seen = once.get(key);
				if (seen == null) {
					once.put(key, new ArrayList<>(List.of(pair)));
				} else if (seen.get(0)[0] == pair[0] && seen.get(0)[1] == pair[1]) {
					// same direction: a fold, both walks stay
					repeated.put(key, Boolean.TRUE);
					seen.add(pair);
				} else {
					// opposite directions: interior, off the boundary
					repeated.put(key, Boolean.TRUE);
					once.remove(key);
				}
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
			// An edge shorter than a reader's own resolution is one it cannot sew: the
			// two vertices it joins are the same point to the reader, and the face
			// bounded by it is refused — which opens the shell around the refusal.
			// The edge is written anyway, but with both its vertices at ONE position,
			// the midpoint: the reader merges the coincident vertices, the faces on
			// both sides sew across where the edge was, and the surface moves by less
			// than the reader measures. The edge entity itself stays well formed.
			// the vertices are written at their own (welded) positions; merging the
			// sub-resolution pairs is the weld's job, done on the indices before any
			// of this file is written
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

			// A point is written under the name the reader will give it. Two vertices
			// closer than a reader's own merge scale are one point to that reader, so
			// writing them as two entities leaves every edge that names one of them
			// with nothing to pair on the face that names the other — the shell then
			// reads as open even though the file's indices pair. The reader's scale is
			// the yardstick because the file is for a reader.
			int shared = sharedVertexName(vertex);
			Long same = vertices.get(shared);
			if (same != null) {
				vertices.put(vertex, same);
				return same;
			}
			long cartesian = emitter.add("CARTESIAN_POINT(''," + coords(point(shared)) + ")");
			long id = emitter.add("VERTEX_POINT('',#" + cartesian + ")");
			vertices.put(shared, id);
			vertices.put(vertex, id);
			return id;
		}

		/** The lowest index of the vertices a reader's tolerance makes one point. */
		private int sharedVertexName(int vertex) {
			if (readerNames == null) {
				int count = points.length / 3;
				int[] name = new int[count];
				for (int v = 0; v < count; v++)
					name[v] = v;
				Map<Long, List<Integer>> byCell = new HashMap<>();
				for (int v = 0; v < count; v++) {
					double[] p = point(v);
					long cx = Math.round(p[0] / readerTolerance);
					long cy = Math.round(p[1] / readerTolerance);
					long cz = Math.round(p[2] / readerTolerance);
					int lowest = v;
					for (int dx = -1; dx <= 1; dx++)
						for (int dy = -1; dy <= 1; dy++)
							for (int dz = -1; dz <= 1; dz++) {
								List<Integer> cell = byCell.get(cell(cx + dx, cy + dy, cz + dz));
								if (cell == null)
									continue;
								for (int other : cell)
									if (distance(p, point(other)) <= readerTolerance)
										lowest = Math.min(lowest, name[other]);
							}
					name[v] = lowest;
					byCell.computeIfAbsent(cell(cx, cy, cz), unused -> new ArrayList<>()).add(v);
				}
				readerNames = name;
			}
			return readerNames[vertex];
		}

		/** Each vertex to the name the reader will give its point. */
		private int[] readerNames;

		private long line(StepEmitter emitter, double[] a, double[] b) {
			double dx = b[0] - a[0];
			double dy = b[1] - a[1];
			double dz = b[2] - a[2];
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			// a zero-length line has no direction to write; the arbitrary axis keeps the
			// entity well formed and the vector of no length carries no direction anyway
			if (length == 0)
				return emitter.add("LINE('',#" + emitter.add("CARTESIAN_POINT(''," + coords(a) + ")") + ",#"
						+ emitter.add("VECTOR('',#" + emitter.add("DIRECTION('',(1.,0.,0.))") + ",0.)") + ")");

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
