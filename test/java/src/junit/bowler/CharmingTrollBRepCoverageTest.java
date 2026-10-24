package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.TickToc;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Checks the BRep the STEP export writes, on its own: the surface a CAD package
 * shows must cover every triangle of the source mesh, with no hole and no gap.
 *
 * <p>
 * The round trip test reads a file's tessellation when one is present, so it only
 * ever proves the tessellation is right. These tests read with tessellation
 * preference off, which makes the reader triangulate the BRep faces, exactly as a
 * CAD package does. What comes back must enclose the same volume and cover the
 * same surface as the mesh it was written from.
 */
public class CharmingTrollBRepCoverageTest {

	private static final File CORPUS = new File("testStepFiles");
	private static final String SOURCE = "Charming_Troll_Group_of_3.3mf";

	@BeforeClass
	public static void enableProfiling() {
		Log.enableDebugPrint();
		TickToc.setEnabled(true);
	}

	@Before
	public void startTiming() {
		TickToc.clear();
		TickToc.tic("test start");
	}

	private static List<CSG> source() throws Exception {
		File file = new File(CORPUS, SOURCE);
		assertTrue("the 3MF fixture must exist at " + file.getAbsolutePath(), file.isFile());
		List<CSG> parts = CSG.fromThreeMF(file.toPath());
		assertFalse("the 3MF should hold parts", parts.isEmpty());
		return parts;
	}

	private static File export(List<CSG> parts) throws Exception {
		File written = File.createTempFile("charming_troll_brep", ".step");
		written.deleteOnExit();
		new STEPFile(parts).write(written);
		return written;
	}

	/** Reads the file's BRep faces, not its tessellation. */
	private static STEPFile readBRep(File file) throws Exception {
		try (FileInputStream in = new FileInputStream(file)) {
			return new STEPFile(in, false);
		}
	}

	@Test
	public void theBRepIsReadableWithoutTheTessellation() throws Exception {
		List<CSG> parts = source();
		STEPFile read = readBRep(export(parts));

		assertEquals("every part must come back from its BRep alone", parts.size(), read.getImported().size());
		for (int i = 0; i < parts.size(); i++) {
			CSG mesh = read.getImported().get(i);
			assertTrue("part " + i + " must have triangles from its BRep", mesh.getTriCount() > 0);
			Log.debug("BRep part " + i + ": " + mesh.getVertCount() + "v/" + mesh.getTriCount() + "t");
		}
	}

	@Test
	public void theBRepEnclosesTheSameVolumeAsTheSourceMesh() throws Exception {
		List<CSG> parts = source();
		List<CSG> read = readBRep(export(parts)).getImported();

		for (int i = 0; i < parts.size(); i++) {
			double expected = volume(parts.get(i));
			double actual = volume(read.get(i));
			Log.debug("volume part " + i + ": " + expected + " source vs " + actual + " BRep");
			// a face that is missing or has a hole loses volume, so this is the check
			// that the BRep really closes the solid rather than merely approximating it
			assertEquals("part " + i + " BRep must enclose the source's volume", expected, actual,
					Math.abs(expected) * 1e-4);
		}
	}

	@Test
	public void theBRepCoversTheSameSurfaceAreaAsTheSourceMesh() throws Exception {
		List<CSG> parts = source();
		List<CSG> read = readBRep(export(parts)).getImported();

		for (int i = 0; i < parts.size(); i++) {
			double expected = area(parts.get(i));
			double actual = area(read.get(i));
			Log.debug("area part " + i + ": " + expected + " source vs " + actual + " BRep");
			// a face left unbounded would cover far more than the source, and a dropped
			// face would cover less; either way the totals stop matching
			assertEquals("part " + i + " BRep must cover the source's surface", expected, actual, expected * 1e-4);
		}
	}

	@Test
	public void everySourceTriangleIsCoveredByABRepFace() throws Exception {
		List<CSG> parts = source();
		List<CSG> read = readBRep(export(parts)).getImported();

		for (int i = 0; i < parts.size(); i++) {
			CSG sourceMesh = parts.get(i);
			CSG brep = read.get(i);

			// every triangle centre of the source must lie on the BRep, and every
			// triangle centre of the BRep must lie on the source: neither may claim
			// surface the other does not have
			int uncovered = centresMissingFrom(sourceMesh, brep);
			int spurious = centresMissingFrom(brep, sourceMesh);
			Log.debug("coverage part " + i + ": " + uncovered + " source centres uncovered, " + spurious
					+ " BRep centres not on the source, out of " + sourceMesh.getTriCount() + "/" + brep.getTriCount());
			assertEquals("part " + i + " must leave no source triangle uncovered", 0, uncovered);
			assertEquals("part " + i + " must not add surface the source does not have", 0, spurious);
		}
	}

	@Test
	public void theBRepAndTheTessellationDescribeTheSameShape() throws Exception {
		List<CSG> parts = source();
		File written = export(parts);

		STEPFile fromTessellation;
		STEPFile fromBRep;
		try (FileInputStream a = new FileInputStream(written); FileInputStream b = new FileInputStream(written)) {
			fromTessellation = new STEPFile(a, true);
			fromBRep = new STEPFile(b, false);
		}

		for (int i = 0; i < parts.size(); i++) {
			CSG tessellated = fromTessellation.getImported().get(i);
			CSG brep = fromBRep.getImported().get(i);
			double expected = volume(parts.get(i));

			Log.debug("volume part " + i + ": source " + expected + ", tessellation " + volume(tessellated) + ", BRep "
					+ volume(brep));
			assertEquals("the tessellation must match the source", expected, volume(tessellated),
					Math.abs(expected) * 1e-4);
			assertEquals("the BRep must match the tessellation", volume(tessellated), volume(brep),
					Math.abs(expected) * 1e-4);
		}
	}

	// --- helpers ---

	/**
	 * How many of a mesh's triangle centres are more than a hair away from every
	 * triangle of another mesh. Sampling centres is enough to catch a missing face:
	 * a triangle with no face over it has its centre on nothing.
	 */
	private static int centresMissingFrom(CSG mesh, CSG other) {
		long[] triangles = mesh.getTriangles();
		double[] vertices = mesh.getVertices();
		double[] otherVertices = other.getVertices();
		long[] otherTriangles = other.getTriangles();

		double tolerance = 1e-6 * diagonal(vertices);
		int missing = 0;
		for (int t = 0; t + 2 < triangles.length; t += 3) {
			double[] centre = centre(vertices, triangles, t);
			if (!onSurface(centre, otherVertices, otherTriangles, tolerance))
				missing++;
		}
		return missing;
	}

	private static double[] centre(double[] vertices, long[] triangles, int at) {
		int a = (int) triangles[at] * 3;
		int b = (int) triangles[at + 1] * 3;
		int c = (int) triangles[at + 2] * 3;
		return new double[]{(vertices[a] + vertices[b] + vertices[c]) / 3,
				(vertices[a + 1] + vertices[b + 1] + vertices[c + 1]) / 3,
				(vertices[a + 2] + vertices[b + 2] + vertices[c + 2]) / 3};
	}

	/** True when the point lies on one of the mesh's triangles. */
	private static boolean onSurface(double[] point, double[] vertices, long[] triangles, double tolerance) {
		for (int t = 0; t + 2 < triangles.length; t += 3) {
			int a = (int) triangles[t] * 3;
			int b = (int) triangles[t + 1] * 3;
			int c = (int) triangles[t + 2] * 3;
			if (distanceToTriangle(point, vertices, a, b, c) <= tolerance)
				return true;
		}
		return false;
	}

	/** Distance from a point to a triangle, zero when it lies inside it. */
	private static double distanceToTriangle(double[] p, double[] vertices, int a, int b, int c) {
		double[] pa = {vertices[a], vertices[a + 1], vertices[a + 2]};
		double[] pb = {vertices[b], vertices[b + 1], vertices[b + 2]};
		double[] pc = {vertices[c], vertices[c + 1], vertices[c + 2]};

		// the plane first: a point off the plane is as far away as its projection
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

	/** True when a point already in the triangle's plane lies inside it. */
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

	/** The surface area a mesh's triangles describe. */
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

	/** The volume a mesh encloses, as the sum of its tetrahedra about the origin. */
	private static double volume(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		double total = 0;
		for (int i = 0; i + 2 < triangles.length; i += 3) {
			int a = (int) triangles[i] * 3, b = (int) triangles[i + 1] * 3, c = (int) triangles[i + 2] * 3;
			double[] pa = {vertices[a], vertices[a + 1], vertices[a + 2]};
			double[] pb = {vertices[b], vertices[b + 1], vertices[b + 2]};
			double[] pc = {vertices[c], vertices[c + 1], vertices[c + 2]};
			total += (pa[0] * (pb[1] * pc[2] - pb[2] * pc[1]) - pa[1] * (pb[0] * pc[2] - pb[2] * pc[0])
					+ pa[2] * (pb[0] * pc[1] - pb[1] * pc[0])) / 6;
		}
		return Math.abs(total);
	}
}
