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
		for (Mesh.Planned planned : mesh.verifiedPlan(sourceTriangles, name)) {
			if (planned.loops != null) {
				written.add(planned.loops);
				faces.add(mesh.face(emitter, planned.region, planned.loops, name));
				emitted++;
			} else {
				// this triangle is written as a face of its own, which reproduces it by
				// construction; see Mesh#plan
				written.add(List.of(mesh.triangleLoop(planned.region.get(0))));
				faces.add(mesh.triangleFace(emitter, planned.region.get(0), name));
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
		private final double planeTolerance;
		private final double weldTolerance;
		/** A mesh vertex to the vertex that represents it. */
		/** Triangles the source had and this mesh could not keep, for diagnostics. */
		private int droppedCollapsed;

		private Mesh(double[] points, long[] sourceTriangles) {
			this.points = points;
			this.planeTolerance = PLANE_TOLERANCE * diagonal(points);
			this.weldTolerance = WELD_TOLERANCE * diagonal(points);
			Log.debug("StepBRepWrite: mesh of " + (points.length / 3) + " vertices, "
					+ (sourceTriangles == null ? 0 : sourceTriangles.length / 3) + " triangles, tolerances weld="
					+ weldTolerance + " plane=" + planeTolerance);
			TickToc.tic("prepare mesh");
			prepare(sourceTriangles);
			TickToc.tic("index");
			index();
			TickToc.tic("group");
			group();
			Log.debug("StepBRepWrite: grouped into " + faces.size() + " faces");
		}

		/**
		 * Takes the source triangles exactly as stored.
		 *
		 * <p>
		 * The only interference is unifying a point the mesh stores as several
		 * vertices, and only when that is provably exact. See the body.
		 */
		private void prepare(long[] sourceTriangles) {
			if (sourceTriangles == null)
				return;
			// The mesh is written as it is stored. Every triangle the source holds is
			// written, and none is invented: the source is the ground truth, and a writer
			// that split edges or snapped vertices would be exporting a different shape.
			// Repairing a mesh is its owner's business, not the file writer's.
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
			triangles.addAll(raw);
			if (isEdgeManifold())
				return;

			// A mesh can also close in space while its own indices say otherwise, because
			// one point is stored as several vertices. Unifying those is the one thing
			// that is not a change of shape: the points are the same point, and the file
			// would otherwise name the same position twice, which a reader welds anyway.
			//
			// It is kept only when it is provably exact: the mesh must close, the same
			// triangles must survive, and none may collapse. Welding that moved a vertex
			// or lost a sliver would be exporting a different mesh, so it is dropped.
			triangles.clear();
			Map<Integer, Integer> representative = representatives();
			for (int[] triangle : raw)
				triangles.add(new int[]{representative.getOrDefault(triangle[0], triangle[0]),
						representative.getOrDefault(triangle[1], triangle[1]),
						representative.getOrDefault(triangle[2], triangle[2])});
			if (isEdgeManifold() && triangles.size() == raw.size())
				return;
			triangles.clear();
			triangles.addAll(raw);
		}

		/**
		 * A vertex to the vertex that represents it, for every vertex that describes a
		 * point another vertex already describes.
		 *
		 * <p>
		 * "Describes the same point" means within the weld distance, which is a fraction
		 * of the model's own size rather than any modelling tolerance: it is the
		 * precision below which the file itself cannot tell two positions apart. The
		 * lookup is through a grid of cells one weld distance across, so a vertex only
		 * has to be compared with those in the cells touching it.
		 */
		private Map<Integer, Integer> representatives() {
			Map<Integer, Integer> representative = new HashMap<>();
			Map<Long, List<Integer>> grid = new HashMap<>();
			for (int vertex = 0; vertex < points.length / 3; vertex++) {
				double[] p = point(vertex);
				long x = (long) Math.floor(p[0] / weldTolerance);
				long y = (long) Math.floor(p[1] / weldTolerance);
				long z = (long) Math.floor(p[2] / weldTolerance);
				Integer found = null;
				for (long dx = -1; dx <= 1 && found == null; dx++)
					for (long dy = -1; dy <= 1 && found == null; dy++)
						for (long dz = -1; dz <= 1 && found == null; dz++)
							for (int candidate : grid.getOrDefault(cell(x + dx, y + dy, z + dz), List.of()))
								if (distance(point(candidate), p) <= weldTolerance) {
									found = candidate;
									break;
								}
				if (found != null)
					representative.put(vertex, found);
				else
					grid.computeIfAbsent(cell(x, y, z), unused -> new ArrayList<>()).add(vertex);
			}
			return representative;
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

		/** Twice the total area the given triangles cover. */
		private double areaOf(List<int[]> pieces) {
			double total = 0;
			for (int[] piece : pieces)
				total += Math.abs(area(asLoop(piece), normal(piece)));
			return total;
		}

		/** The surface the given triangles cover, so a repair can be held to it. */
		private double surfaceArea(List<int[]> some) {
			return areaOf(some) / 2;
		}

		/**
		 * True when a reader triangulating the written faces gets the mesh's own
		 * triangles back: the same points, the same triangles, nothing added or lost.
		 *
		 * <p>
		 * This is the export's contract, and it is checked on the faces as emitted rather
		 * than on what the writer meant them to be. A reader sees only a face's plane and
		 * its loops, so each face is re-triangulated here exactly as {@code StepBRep}
		 * does, with the same ear clipper, and the result is compared to the mesh.
		 *
		 * <p>
		 * Points are compared by coordinate, because a reader welds by position, and
		 * triangles as sorted triples of coordinates, because it may order and wind them
		 * its own way. Comparing the triangles themselves rather than an area or a count
		 * is what makes this exact: a fold, a piece of no area, a clipper that lost part
		 * of its outline and a face that covers the wrong surface all change the set,
		 * while a total can stay the same. The mesh's own topology is not required to be
		 * manifold — a source that is not is reproduced just as faithfully.
		 */
		private boolean readsBackAsSource(List<List<List<Integer>>> written) {
			Map<String, Integer> byPoint = new HashMap<>();
			List<double[]> positions = new ArrayList<>();
			List<int[]> read = new ArrayList<>();
			for (List<List<Integer>> loops : written) {
				if (loops.isEmpty() || loops.get(0).size() < 3)
					return false;
				List<List<Integer>> indices = new ArrayList<>();
				for (List<Integer> loop : loops) {
					List<Integer> index = new ArrayList<>();
					for (int vertex : loop) {
						double[] p = point(vertex);
						String key = coordinates(p);
						Integer id = byPoint.get(key);
						if (id == null) {
							id = positions.size();
							byPoint.put(key, id);
							positions.add(p);
						}
						index.add(id);
					}
					indices.add(index);
				}
				List<List<Integer>> holes = new ArrayList<>(indices.subList(1, indices.size()));
				read.addAll(Triangulator.triangulate(indices.get(0), holes, positions));
			}

			double readArea = 0;
			for (int[] triangle : read) {
				if (triangle[0] == triangle[1] || triangle[1] == triangle[2] || triangle[2] == triangle[0])
					continue;
				readArea += triangleArea(positions.get(triangle[0]), positions.get(triangle[1]),
						positions.get(triangle[2]));
			}
			// the reader is free to cut a planar polygon its own way, so its triangles are
			// allowed to differ; what must not differ is the surface
			double meshArea = surfaceArea(triangles);
			double rounding = 1e-12 * diagonal(points) * diagonal(points) * Math.max(1, read.size());
			if (Math.abs(readArea - meshArea) <= Math.max(meshArea * 1e-12, rounding))
				return true;
			Log.debug("StepBRepWrite: whole part read area " + readArea + " vs mesh " + meshArea + " delta "
					+ (readArea - meshArea) + " bound " + Math.max(meshArea * 1e-12, rounding));
			return false;
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

			// any real difference is a hole the file would not describe, or surface it
			// would add
			double tolerance = Math.max(1e-12, trianglesArea * 1e-9);
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
		private List<Planned> plan() {
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
						for (int index : region)
							plan.add(new Planned(List.of(index), null));
					}
				}
			}
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
			boolean ok = readsBackAsSource(loopsOf(plan));
			Log.debug("StepBRepWrite: plan has " + plan.size() + " faces, readsBack=" + ok);
			if (ok)
				return plan;

			Log.debug("StepBRepWrite: the stitched faces would not read back as the mesh, so one face is written"
					+ " per triangle");
			plan = new ArrayList<>();
			for (int index = 0; index < triangles.size(); index++)
				plan.add(new Planned(List.of(index), null));
			if (!readsBackAsSource(loopsOf(plan)))
				throw new IllegalStateException(
						"STEP export does not read back as its own mesh even per triangle: " + describeEdges());
			return plan;
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

		private List<List<Integer>> stitchable(List<Integer> region) {
			if (normal(triangles.get(region.get(0))) == null)
				return null;
			List<List<Integer>> loops = loops(region);
			if (!carriesEveryVertex(region, loops))
				return null;
			if (!readerReproduces(region, loops))
				return null;
			if (!boundsExactly(region, loops) || !representsExactly(region, loops))
				return null;
			return loops;
		}

		/**
		 * True when a reader re-triangulating the loops gets the region's own triangles
		 * back, exactly.
		 *
		 * <p>
		 * A reader builds a face from its loops alone, so the triangles it picks are the
		 * surface the file says the part has. Re-running the same ear clipping here,
		 * just as {@code StepBRep} does, and requiring the same set of triangles is
		 * therefore the honest test that the face describes the mesh: the part reads
		 * back as the mesh it was written from, with nothing added, lost or folded.
		 *
		 * <p>
		 * Anything less will not do. Equal area admits a face that folds back on itself,
		 * which adds and removes the same amount and leaves an edge doubled where a
		 * neighbour needs to pair with it; equal outline admits the same fold, since the
		 * outline is unchanged. The triangles are compared as sorted vertex triples, so
		 * the reader may emit them in any order or winding.
		 *
		 * <p>
		 * A region that fails is written as one face per triangle instead, which
		 * reproduces the mesh by construction. If the part still does not read back as
		 * its own mesh, the whole part is rewritten that way; see
		 * {@link #settledPlan}.
		 */
		private boolean readerReproduces(List<Integer> region, List<List<Integer>> loops) {
			if (loops.isEmpty() || loops.get(0).size() < 3)
				return false;

			int max = 0;
			for (List<Integer> loop : loops)
				for (int vertex : loop)
					max = Math.max(max, vertex);
			List<double[]> positions = new ArrayList<>();
			while (positions.size() <= max)
				positions.add(null);
			for (List<Integer> loop : loops)
				for (int vertex : loop)
					positions.set(vertex, point(vertex));

			List<List<Integer>> holes = new ArrayList<>(loops.subList(1, loops.size()));
			List<int[]> read = Triangulator.triangulate(loops.get(0), holes, positions);
			// the reader must spend one triangle per triangle of the region: fewer means
			// it lost surface, more means its clipper stalled and fanned
			if (read.size() != region.size())
				return false;

			Map<Long, Integer> edges = new HashMap<>();
			for (int[] triangle : read) {
				// a piece of no area is one the reader could not build a face from
				if (normal(triangle) == null)
					return false;
				for (int i = 0; i < 3; i++)
					edges.merge(directed(triangle[i], triangle[(i + 1) % 3]), 1, Integer::sum);
			}
			// an edge the reader uses twice is a fold, and the face across it can never
			// pair: that is the non-manifold seam this check exists to keep out
			for (int uses : edges.values())
				if (uses != 1)
					return false;

			// Now the cut. A reader picks its own diagonals, so its triangles are the
			// mesh's only when the region is exactly planar: there, any cut encloses the
			// same surface and the difference is invisible. A mesh's "coplanar" triangles
			// are usually coplanar only to within the rounding of stored doubles, and
			// re-cutting a region that is slightly dish-shaped changes the surface by an
			// amount far too small for any area tolerance to separate from the slivers
			// these parts hold. So an exactly planar region is accepted here, and any
			// other is accepted only if the reader's triangles are the mesh's own.
			if (exactlyPlanar(region)) {
				// every piece must still have an area, or the face loses surface
				for (int[] piece : read)
					if (normal(piece) == null)
						return false;
				return true;
			}

			// Otherwise the cut has to be the mesh's own. An area comparison cannot decide
			// this: the regions here reach 4e-9 of surface, and the rounding that any
			// shoelace sum on coordinates of this size carries is thousands of times
			// larger. A region like that can be read back as five triangles covering
			// 0.27 more than it does and still look equal by area. Comparing the
			// triangles as sets has no tolerance to tune.
			Set<String> wanted = new LinkedHashSet<>();
			for (int index : region) {
				int[] triangle = triangles.get(index);
				wanted.add(canonical(triangle[0], triangle[1], triangle[2]));
			}
			Set<String> got = new LinkedHashSet<>();
			for (int[] triangle : read)
				if (!got.add(canonical(triangle[0], triangle[1], triangle[2])))
					return false; // the same triangle twice is a fold
			return wanted.equals(got);
		}

		/**
		 * True when every vertex the region uses lies in one plane, to within the
		 * rounding a plane test on coordinates of this size carries.
		 *
		 * <p>
		 * This is not a modelling tolerance. The bound is the error of one subtraction
		 * and three multiplications, so nothing a mesh stores as a distinct feature can
		 * fall inside it. Where it holds, every triangulation of the region's outline
		 * encloses the same surface, so a reader may cut it its own way; where it does
		 * not, the region is slight dish-shaped and the cut has to be the mesh's own.
		 */
		private boolean exactlyPlanar(List<Integer> region) {
			int[] first = triangles.get(region.get(0));
			Vector3d normal = normal(first);
			if (normal == null)
				return false;
			double[] origin = point(first[0]);
			double bound = 1e-15 * diagonal(points);
			for (int index : region)
				for (int vertex : triangles.get(index)) {
					double[] p = point(vertex);
					double offset = Math.abs((p[0] - origin[0]) * normal.x + (p[1] - origin[1]) * normal.y
							+ (p[2] - origin[2]) * normal.z);
					if (offset > bound)
						return false;
				}
			return true;
		}

		/** A triangle's three vertices in a fixed order, so triangles compare as sets. */
		private static String canonical(int a, int b, int c) {
			int[] sorted = {a, b, c};
			java.util.Arrays.sort(sorted);
			return sorted[0] + "," + sorted[1] + "," + sorted[2];
		}

		/**
		 * True when the loops name every vertex the region's triangles use.
		 *
		 * <p>
		 * A face is read back from its loops alone: a reader re-triangulates the
		 * outline and can only ever emit the points the loops name. A vertex inside a
		 * coplanar region is therefore lost the moment that region is written as one
		 * face, and any neighbouring face still naming it has nothing to pair with, so
		 * the shell stops being manifold. Such a region is written as one face per
		 * triangle instead, which keeps every vertex and reproduces the mesh exactly.
		 */
		private boolean carriesEveryVertex(List<Integer> region, List<List<Integer>> loops) {
			Set<Integer> onLoops = new LinkedHashSet<>();
			for (List<Integer> loop : loops)
				onLoops.addAll(loop);
			for (int index : region)
				for (int corner : triangles.get(index))
					if (!onLoops.contains(corner))
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
			Map<Long, Integer> wanted = new HashMap<>();
			for (int[] edge : boundary(region))
				wanted.merge(directed(edge[0], edge[1]), 1, Integer::sum);

			Map<Long, Integer> walked = new HashMap<>();
			for (List<Integer> loop : loops)
				for (int i = 0; i < loop.size(); i++) {
					long edge = directed(loop.get(i), loop.get((i + 1) % loop.size()));
					if (!wanted.containsKey(edge))
						return false; // a segment no triangle of the region has
					walked.merge(edge, 1, Integer::sum);
				}
			return walked.equals(wanted);
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
			// a triangle whose points are collinear encloses nothing, so its loop is
			// still writable and still pairs with its neighbours; any direction planes it
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
					+ " used more than once each way, weld tolerance " + weldTolerance + " flat triangles";
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
