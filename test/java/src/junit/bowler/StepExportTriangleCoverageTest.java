package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.StepBRepWrite;
import com.neuronrobotics.bowlerstudio.threed.StepFace;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/**
 * No triangle of the source mesh may be left out of the exported faces, however
 * thin it is.
 *
 * <p>
 * A mesh from a kernel holds far more than the shapes a hand-written test does:
 * a hole of a fiftieth of a millimetre at a few hundred facets produces thousands
 * of triangles whose height above their longest edge is near the rounding of a
 * stored double. Those are exactly the triangles a writer drops by accident, and
 * each one leaves a gap, so they are the shapes these tests are built from.
 *
 * <p>
 * Coverage is checked geometrically, against the written loops rather than
 * against a reader's re-triangulation: a point strictly inside a source triangle
 * must lie inside one of the faces written, outside every hole of it, and in that
 * face's plane. Sampling several points of each triangle rather than its centroid
 * alone is what makes this catch a face whose outline clips a corner.
 */
public class StepExportTriangleCoverageTest {

	/** How far off a face's plane a sample may sit, relative to the model's size. */
	private static final double PLANE_TOLERANCE = 1e-6;

	private static List<CSG> shapes() {
		List<CSG> shapes = new ArrayList<>();
		// a hole small enough, at enough facets, that its triangles are sub-micron
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 4).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(0.05, 10, 256).toCSG()));
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 4).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(0.02, 10, 512).toCSG()));
		// walls a hundredth of a millimetre thick
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(20, 20, 10).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(19.99, 19.99, 10).toCSG()));
		// a plate with a normal hole, as a control with well sized facets
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 20, 32).toCSG().move(0, 0, -10)));
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(10).toCSG());
		return shapes;
	}

	/**
	 * Every source triangle must lie under a written face. This is the property the
	 * export exists to keep, and the one a gap shows up as.
	 */
	@Test
	public void everySourceTriangleLiesUnderAWrittenFace() {
		for (CSG source : shapes()) {
			List<StepFace> faces = StepBRepWrite.faces(source);
			assertTrue("the export must write faces", !faces.isEmpty());

			List<Double> uncovered = new ArrayList<>();
			double[] vertices = source.getVertices();
			long[] triangles = source.getTriangles();
			double tolerance = PLANE_TOLERANCE * diagonal(vertices);
			for (int t = 0; t + 2 < triangles.length; t += 3) {
				double[][] corners = {point(vertices, (int) triangles[t]), point(vertices, (int) triangles[t + 1]),
						point(vertices, (int) triangles[t + 2])};
				// points strictly inside the triangle, so a face that clips a corner or an
				// edge is noticed; the centroid alone would sit inside either way
				for (double[] weight : new double[][]{{1 / 3.0, 1 / 3.0, 1 / 3.0}, {0.7, 0.2, 0.1}, {0.1, 0.7, 0.2},
						{0.2, 0.1, 0.7}, {0.45, 0.45, 0.1}, {0.1, 0.45, 0.45}, {0.45, 0.1, 0.45}}) {
					double[] sample = new double[3];
					for (int axis = 0; axis < 3; axis++)
						sample[axis] = corners[0][axis] * weight[0] + corners[1][axis] * weight[1]
								+ corners[2][axis] * weight[2];
					if (!covered(faces, sample, tolerance)) {
						uncovered.add(weight[0]);
						break;
					}
				}
			}
			assertEquals("every triangle of the mesh must lie under a face, but some have none", 0, uncovered.size());
		}
	}

	/**
	 * Each written face must enclose exactly the surface its own triangles cover.
	 * A face whose loops enclose more or less than the triangles it stands for is
	 * a gap (or a spurious surface) even when the totals happen to cancel out.
	 */
	@Test
	public void everyWrittenFaceEnclosesTheSurfaceItStandsFor() {
		for (CSG source : shapes()) {
			double written = 0;
			for (StepFace face : StepBRepWrite.faces(source))
				for (int i = 0; i < face.loops.size(); i++)
					written += (i == 0 ? 1 : -1) * area(face.loops.get(i)); // a hole subtracts

			assertEquals("the written faces must cover the mesh's whole surface", areaOf(source), written,
					Math.max(1e-9, areaOf(source) * 1e-6));
		}
	}

	/**
	 * The written loops must close: each directed edge appears once, and once
	 * reversed.
	 *
	 * <p>
	 * This holds for a mesh that is already manifold by its own indices, which is
	 * what a closed solid's mesh is. A mesh that is only closed in space — a union
	 * whose parts meet at a point, or a cut that left a vertex mid-edge — has no
	 * pairing to write until its T-junctions are split, so it is the coverage tests
	 * above that apply to it, not this one.
	 */
	@Test
	public void theWrittenLoopsPairEdgeForEdge() {
		for (CSG source : shapes())
			if (manifoldByIndex(source)) {
				java.util.Map<String, Integer> directed = new java.util.HashMap<>();
				for (StepFace face : StepBRepWrite.faces(source))
					for (List<double[]> loop : face.loops)
						for (int at = 0; at < loop.size(); at++)
							directed.merge(key(loop.get(at)) + ">" + key(loop.get((at + 1) % loop.size())), 1,
									Integer::sum);

				int unpaired = 0;
				for (java.util.Map.Entry<String, Integer> entry : directed.entrySet()) {
					String edge = entry.getKey();
					int split = edge.indexOf('>');
					String reverse = edge.substring(split + 1) + ">" + edge.substring(0, split);
					if (!Integer.valueOf(1).equals(directed.get(reverse)) || entry.getValue() != 1)
						unpaired++;
				}
				assertEquals("every written loop edge must pair with the face across it", 0, unpaired);
			}
	}

	// --- helpers ---

	/** True when every undirected edge is used once each way, so the mesh closes by index. */
	private static boolean manifoldByIndex(CSG csg) {
		long[] triangles = csg.getTriangles();
		java.util.Map<Long, Integer> directed = new java.util.HashMap<>();
		for (int t = 0; t + 2 < triangles.length; t += 3)
			for (int corner = 0; corner < 3; corner++) {
				long from = triangles[t + corner];
				long to = triangles[t + (corner + 1) % 3];
				directed.merge((from << 32) | (to & 0xffffffffL), 1, Integer::sum);
			}
		for (java.util.Map.Entry<Long, Integer> entry : directed.entrySet()) {
			long from = entry.getKey() >> 32;
			long to = entry.getKey() & 0xffffffffL;
			Integer reverse = directed.get((to << 32) | (from & 0xffffffffL));
			if (entry.getValue() != 1 || !Integer.valueOf(1).equals(reverse))
				return false;
		}
		return !directed.isEmpty();
	}

	/** True when the point lies inside a written face, less its holes. */
	private static boolean covered(List<StepFace> faces, double[] p, double tolerance) {
		for (StepFace face : faces) {
			double offset = (p[0] - face.origin[0]) * face.normal.x + (p[1] - face.origin[1]) * face.normal.y
					+ (p[2] - face.origin[2]) * face.normal.z;
			if (Math.abs(offset) > tolerance)
				continue;

			Vector3d normal = face.normal;
			Vector3d u = perpendicularTo(normal);
			Vector3d v = normal.cross(u);
			if (!inside(face.loops.get(0), p, u, v))
				continue;
			boolean inHole = false;
			for (int hole = 1; hole < face.loops.size(); hole++)
				inHole |= inside(face.loops.get(hole), p, u, v);
			if (!inHole)
				return true;
		}
		return false;
	}

	private static Vector3d perpendicularTo(Vector3d normal) {
		Vector3d candidate = Math.abs(normal.x) < 0.9 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0);
		return normal.cross(candidate).normalized();
	}

	/** Winding number about the face's normal; a point on the loop counts as inside. */
	private static boolean inside(List<double[]> loop, double[] p, Vector3d u, Vector3d v) {
		double x = p[0] * u.x + p[1] * u.y + p[2] * u.z;
		double y = p[0] * v.x + p[1] * v.y + p[2] * v.z;
		int winding = 0;
		for (int i = 0; i < loop.size(); i++) {
			double[] a = loop.get(i);
			double[] b = loop.get((i + 1) % loop.size());
			double ax = a[0] * u.x + a[1] * u.y + a[2] * u.z, ay = a[0] * v.x + a[1] * v.y + a[2] * v.z;
			double bx = b[0] * u.x + b[1] * u.y + b[2] * u.z, by = b[0] * v.x + b[1] * v.y + b[2] * v.z;
			double side = (bx - ax) * (y - ay) - (x - ax) * (by - ay);
			double along = (bx - ax) * (x - ax) + (by - ay) * (y - ay);
			double length = (bx - ax) * (bx - ax) + (by - ay) * (by - ay);
			if (length > 0 && along >= 0 && along <= length && Math.abs(side) <= 1e-12 * Math.sqrt(length))
				return true;
			if (ay <= y) {
				if (by > y && side > 0)
					winding++;
			} else if (by <= y && side < 0)
				winding--;
		}
		return winding != 0;
	}

	private static String key(double[] p) {
		return Double.toString(p[0]) + "," + Double.toString(p[1]) + "," + Double.toString(p[2]);
	}

	private static double[] point(double[] vertices, int vertex) {
		return new double[]{vertices[vertex * 3], vertices[vertex * 3 + 1], vertices[vertex * 3 + 2]};
	}

	/** The area a planar loop encloses, from its own best-fit plane. */
	private static double area(List<double[]> loop) {
		double nx = 0, ny = 0, nz = 0;
		for (int i = 0; i < loop.size(); i++) {
			double[] a = loop.get(i);
			double[] b = loop.get((i + 1) % loop.size());
			nx += (a[1] - b[1]) * (a[2] + b[2]);
			ny += (a[2] - b[2]) * (a[0] + b[0]);
			nz += (a[0] - b[0]) * (a[1] + b[1]);
		}
		double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length == 0)
			return 0;
		Vector3d normal = new Vector3d(nx / length, ny / length, nz / length);
		Vector3d u = perpendicularTo(normal);
		Vector3d v = normal.cross(u);
		double twice = 0;
		for (int i = 0; i < loop.size(); i++) {
			double[] a = loop.get(i);
			double[] b = loop.get((i + 1) % loop.size());
			twice += (a[0] * u.x + a[1] * u.y + a[2] * u.z) * (b[0] * v.x + b[1] * v.y + b[2] * v.z)
					- (b[0] * u.x + b[1] * u.y + b[2] * u.z) * (a[0] * v.x + a[1] * v.y + a[2] * v.z);
		}
		return Math.abs(twice) / 2;
	}

	private static double areaOf(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		double total = 0;
		for (int i = 0; i + 2 < triangles.length; i += 3) {
			double[] a = point(vertices, (int) triangles[i]);
			double[] b = point(vertices, (int) triangles[i + 1]);
			double[] c = point(vertices, (int) triangles[i + 2]);
			double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
			double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			total += Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
		}
		return total;
	}

	private static double diagonal(double[] vertices) {
		double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
		for (int i = 0; i + 2 < vertices.length; i += 3) {
			minX = Math.min(minX, vertices[i]);
			maxX = Math.max(maxX, vertices[i]);
			minY = Math.min(minY, vertices[i + 1]);
			maxY = Math.max(maxY, vertices[i + 1]);
			minZ = Math.min(minZ, vertices[i + 2]);
			maxZ = Math.max(maxZ, vertices[i + 2]);
		}
		double dx = maxX - minX, dy = maxY - minY, dz = maxZ - minZ;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
