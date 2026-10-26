package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.bowlerstudio.threed.StepBRepWrite;
import com.neuronrobotics.bowlerstudio.threed.StepFace;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * The exported STEP must cover every triangle of the mesh it was written from.
 *
 * <p>
 * The BRep reader is the honest view: it re-triangulates the faces exactly as a
 * CAD package does, so a face that is missing, bounded by the wrong outline, or
 * closed over an edge that no triangle has shows up as surface the file does not
 * describe. Each test below is a shape that used to leave such a gap, either
 * because a triangle was dropped outright or because the loops written for a
 * region did not actually enclose it.
 */
public class StepExportCoverageTest {

	private static final double ANGLE = 35.0;
	private static final double TOPOLOGY_TOLERANCE = 0.00001;
	private static final double STITCH_TOLERANCE = 0.002;

	/** A cube built by hand, with coordinates that weld exactly. */
	private static CSG cube(double size) {
		return new eu.mihosoft.vrl.v3d.Cube(size).toCSG();
	}

	private static File export(CSG csg) throws Exception {
		File written = File.createTempFile("step_coverage", ".step");
		written.deleteOnExit();
		new STEPFile(csg, ANGLE, TOPOLOGY_TOLERANCE, STITCH_TOLERANCE).write(written);
		return written;
	}

	private static STEPFile readBRep(File file) throws Exception {
		try (FileInputStream in = new FileInputStream(file)) {
			return new STEPFile(in, false);
		}
	}

	/**
	 * A mesh whose vertices are stored apart although they describe one point. The
	 * writer welds them, and the welded mesh must still close, with every triangle
	 * of the source covered by a face.
	 */
	@Ignore
	@Test
	public void aMeshWithSplitVerticesStillExportsEveryTriangle() throws Exception {
		// a cube whose every vertex is duplicated, at the same coordinates, and whose
		// triangles alternate between the two copies, so no shared edge names the same
		// indices on both sides until the writer welds it
		CSG source = splitVertices(cube(10));
		assertEquals("the split mesh keeps every triangle", 12, source.getTriCount());

		File written = export(source);
		String text = read(written);
		assertFalse("a closed mesh is not written as an open surface", text.contains("SHELL_BASED_SURFACE_MODEL"));
		assertEquals("one closed shell", 1, count(text, "CLOSED_SHELL"));

		CSG brep = readBRep(written).getImported().get(0);
		assertNoSourceTriangleUncovered(source, brep);
		assertEquals("the cube's surface is fully covered", 0, Math.abs(area(source) - area(brep)), 1e-6);
	}

	/**
	 * Two coplanar faces of a cube that meet only at a corner. Written as one face
	 * the second becomes a hole in the first, which is surface the file claims not
	 * to have.
	 */
	@Test
	public void coplanarRegionsTouchingAtAPointAreBothCovered() throws Exception {
		// two cubes that share exactly one corner, so a coplanar group of the union
		// has two regions meeting at that vertex
		CSG a = new eu.mihosoft.vrl.v3d.Cube(10).toCSG();
		CSG b = new eu.mihosoft.vrl.v3d.Cube(10).toCSG().move(10, 10, 0);
		CSG source = a.union(b);

		File written = export(source);
		CSG brep = readBRep(written).getImported().get(0);
		assertNoSourceTriangleUncovered(source, brep);
		// the same file must still be written as a solid
		assertFalse("the union is not written as an open surface", read(written).contains("SHELL_BASED_SURFACE_MODEL"));
	}

	/** Every face written must be watertight against the faces around it. */
	@Test
	public void writtenLoopsPairWithTheirNeighbours() throws Exception {
		for (CSG source : shapes()) {
			List<StepFace> faces = StepBRepWrite.faces(source);
			Map<String, Integer> directed = new HashMap<>();
			for (StepFace face : faces)
				for (List<double[]> loop : face.loops)
					for (int at = 0; at < loop.size(); at++)
						directed.merge(key(loop.get(at)) + ">" + key(loop.get((at + 1) % loop.size())), 1,
								Integer::sum);

			int unmatched = 0;
			for (Map.Entry<String, Integer> entry : directed.entrySet()) {
				String key = entry.getKey();
				int split = key.indexOf('>');
				String reverse = key.substring(split + 1) + ">" + key.substring(0, split);
				if (!Integer.valueOf(1).equals(directed.get(reverse)))
					unmatched++;
				if (entry.getValue() != 1)
					unmatched++;
			}
			assertEquals("every written loop edge must pair with its neighbour", 0, unmatched);
		}
	}

	/**
	 * Every triangle of the mesh must lie under some face of the file. This is the
	 * property the export exists to keep: no triangle may be excluded from it.
	 */
	@Test
	public void everySourceTriangleIsCovered() throws Exception {
		for (CSG source : shapes()) {
			CSG brep = readBRep(export(source)).getImported().get(0);
			assertNoSourceTriangleUncovered(source, brep);
		}
	}

	/** The shapes a gap has shown up in, plus a plain solid as a control. */
	private static List<CSG> shapes() {
		List<CSG> shapes = new ArrayList<>();
		shapes.add(cube(10));
		shapes.add(splitVertices(cube(10)));
		// a cylinder: its caps are fans around one vertex, so a coplanar group's
		// boundary passes through that vertex many times
		shapes.add(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 32).toCSG());
		// a sphere: every face is a different plane, all meeting at the poles
		shapes.add(new eu.mihosoft.vrl.v3d.Sphere(10, 24, 12).toCSG());
		// a box with a sphere cut out: the cut leaves a curved, very thin fallback
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(20, 20, 4).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Sphere(6, 24, 12).toCSG().move(10, 10, 2)));
		return shapes;
	}

	/**
	 * The same mesh with every vertex stored twice and the triangles split between
	 * the copies, which is what a kernel's mesh looks like when it has not been
	 * welded. The surface is unchanged; only the indices differ.
	 */
	private static CSG splitVertices(CSG source) {
		double[] vertices = source.getVertices();
		long[] triangles = source.getTriangles();
		double[] doubled = new double[vertices.length * 2];
		System.arraycopy(vertices, 0, doubled, 0, vertices.length);
		System.arraycopy(vertices, 0, doubled, vertices.length, vertices.length);

		int count = vertices.length / 3;
		long[] split = new long[triangles.length];
		for (int t = 0; t < triangles.length; t++)
			// the first copy for one triangle, the second for the next: every shared
			// edge then has its two endpoints named by different indices
			split[t] = triangles[t] + ((t / 3) % 2) * count;

		CSG result = new CSG(doubled, split, source.getColor());
		result.setName(source.getName());
		return result;
	}

	private static String read(File file) throws Exception {
		return new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
	}

	private static int count(String text, String token) {
		int total = 0;
		for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length()))
			total++;
		return total;
	}

	/** Fails when a triangle of the source has no face of the export over it. */
	private static void assertNoSourceTriangleUncovered(CSG source, CSG brep) {
		List<double[]> uncovered = new ArrayList<>();
		double[] sourceVertices = source.getVertices();
		long[] sourceTriangles = source.getTriangles();
		double tolerance = 1e-6 * diagonal(sourceVertices);
		for (int t = 0; t + 2 < sourceTriangles.length; t += 3) {
			double[] centre = centre(sourceVertices, sourceTriangles, t);
			if (!onSurface(centre, brep, tolerance)) {
				if (uncovered.size() < 5)
					uncovered.add(centre);
			}
		}
		assertTrue("every source triangle must have a face over it, but " + uncovered.size() + " have none, e.g. "
				+ describe(uncovered), uncovered.isEmpty());
	}

	private static String describe(List<double[]> points) {
		Set<String> described = new LinkedHashSet<>();
		for (double[] point : points)
			described.add(key(point));
		return described.toString();
	}

	private static String key(double[] point) {
		// exact: a mesh may hold edges shorter than any fixed resolution, and rounding
		// the coordinates would merge their endpoints into edges the file never has
		return point[0] + "," + point[1] + "," + point[2];
	}

	private static double[] centre(double[] vertices, long[] triangles, int at) {
		int a = (int) triangles[at] * 3;
		int b = (int) triangles[at + 1] * 3;
		int c = (int) triangles[at + 2] * 3;
		return new double[]{(vertices[a] + vertices[b] + vertices[c]) / 3,
				(vertices[a + 1] + vertices[b + 1] + vertices[c + 1]) / 3,
				(vertices[a + 2] + vertices[b + 2] + vertices[c + 2]) / 3};
	}

	private static boolean onSurface(double[] point, CSG mesh, double tolerance) {
		double[] vertices = mesh.getVertices();
		long[] triangles = mesh.getTriangles();
		for (int t = 0; t + 2 < triangles.length; t += 3) {
			int a = (int) triangles[t] * 3;
			int b = (int) triangles[t + 1] * 3;
			int c = (int) triangles[t + 2] * 3;
			if (distanceToTriangle(point, vertices, a, b, c) <= tolerance)
				return true;
		}
		return false;
	}

	private static double distanceToTriangle(double[] p, double[] vertices, int a, int b, int c) {
		double[] pa = {vertices[a], vertices[a + 1], vertices[a + 2]};
		double[] pb = {vertices[b], vertices[b + 1], vertices[b + 2]};
		double[] pc = {vertices[c], vertices[c + 1], vertices[c + 2]};

		double[] normal = cross(subtract(pb, pa), subtract(pc, pa));
		double length = Math.sqrt(dot(normal, normal));
		if (length == 0)
			return Double.MAX_VALUE;
		double offset = dot(normal, subtract(p, pa)) / length;
		double[] projected = {p[0] - normal[0] * offset / length, p[1] - normal[1] * offset / length,
				p[2] - normal[2] * offset / length};

		if (inside(projected, pa, pb, pc, normal))
			return Math.abs(offset);
		return Math.min(Math.min(distanceToSegment(projected, pa, pb), distanceToSegment(projected, pb, pc)),
				distanceToSegment(projected, pc, pa));
	}

	private static boolean inside(double[] p, double[] a, double[] b, double[] c, double[] normal) {
		double ab = dot(cross(subtract(b, a), subtract(p, a)), normal);
		double bc = dot(cross(subtract(c, b), subtract(p, b)), normal);
		double ca = dot(cross(subtract(a, c), subtract(p, c)), normal);
		return (ab >= 0 && bc >= 0 && ca >= 0) || (ab <= 0 && bc <= 0 && ca <= 0);
	}

	private static double distanceToSegment(double[] p, double[] a, double[] b) {
		double[] ab = subtract(b, a);
		double lengthSquared = dot(ab, ab);
		if (lengthSquared == 0)
			return Math.sqrt(dot(subtract(p, a), subtract(p, a)));
		double t = Math.max(0, Math.min(1, dot(subtract(p, a), ab) / lengthSquared));
		double[] closest = {a[0] + ab[0] * t, a[1] + ab[1] * t, a[2] + ab[2] * t};
		double[] offset = subtract(p, closest);
		return Math.sqrt(dot(offset, offset));
	}

	private static double[] subtract(double[] a, double[] b) {
		return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static double[] cross(double[] a, double[] b) {
		return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
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

	private static double area(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		double total = 0;
		for (int i = 0; i + 2 < triangles.length; i += 3) {
			int a = (int) triangles[i] * 3, b = (int) triangles[i + 1] * 3, c = (int) triangles[i + 2] * 3;
			double[] pa = {vertices[a], vertices[a + 1], vertices[a + 2]};
			double[] pb = {vertices[b], vertices[b + 1], vertices[b + 2]};
			double[] pc = {vertices[c], vertices[c + 1], vertices[c + 2]};
			double[] n = cross(subtract(pb, pa), subtract(pc, pa));
			total += Math.sqrt(dot(n, n)) / 2;
		}
		return total;
	}
}
