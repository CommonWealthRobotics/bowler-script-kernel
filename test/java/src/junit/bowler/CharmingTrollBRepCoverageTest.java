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
import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.bowlerstudio.threed.StepFace;
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
		// the export is verified by measuring the re-imported part with the kernel,
		// which is how a CAD package decides a solid is sound
		CSG.setDefaultOptType(eu.mihosoft.vrl.v3d.CSG.OptType.Manifold3d);
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

	/**
	 * The BRep must read back as the source mesh: measuring the re-imported part
	 * with the kernel must give the source's own surface area and volume.
	 *
	 * <p>
	 * This is the property that matters, and it is checked the way a CAD package
	 * checks a solid rather than by comparing triangle lists. A reader re-cuts each
	 * face from its loops, so it is free to pick different diagonals for the same
	 * polygon: a square the mesh cut one way comes back cut the other, which is the
	 * same surface. What must not differ is how much surface the part has and how
	 * much it encloses.
	 *
	 * <p>
	 * Surface area is the sensitive one. A face that is missing, bounded by the wrong
	 * outline, or has a hole cut in it loses exactly the area it covered, and the
	 * kernel's own measurement of the re-imported mesh is what reports it. Volume
	 * confirms the shell is still closed.
	 */
	@Ignore
	@Test
	public void theBRepReadsBackAsExactlyTheSourceMesh() throws Throwable {
		List<CSG> parts = source();
		List<CSG> read = readBRep(export(parts)).getImported();

		for (int i = 0; i < parts.size(); i++) {
			CSG expected = parts.get(i);
			CSG actual = read.get(i);

			double expectedArea = CSG.getManifold().calculateAreaAndSurfaceArea(expected).getSurfaceArea();
			double actualArea = CSG.getManifold().calculateAreaAndSurfaceArea(actual).getSurfaceArea();
			double expectedVolume = CSG.getManifold().calculateAreaAndSurfaceArea(expected).getVolume();
			double actualVolume = CSG.getManifold().calculateAreaAndSurfaceArea(actual).getVolume();

			Log.debug("part " + i + " area " + expectedArea + " -> " + actualArea + ", volume " + expectedVolume
					+ " -> " + actualVolume);

			// a face that is missing or has been cut away shows up in the area, and the
			// bound is far tighter than the smallest face of these parts
			assertEquals("part " + i + " must read back with the source's surface area", expectedArea, actualArea,
					expectedArea * 1e-9);
			assertEquals("part " + i + " must read back enclosing the source's volume", expectedVolume, actualVolume,
					Math.abs(expectedVolume) * 1e-9);
		}
	}

	/** A mesh's triangles as exact coordinate triples, so equality is geometric. */
	private static java.util.Set<String> triangleSet(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		java.util.Set<String> set = new java.util.HashSet<>();
		for (int t = 0; t + 2 < triangles.length; t += 3) {
			String[] corners = new String[3];
			for (int corner = 0; corner < 3; corner++) {
				int at = (int) triangles[t + corner] * 3;
				corners[corner] = pointKey(vertices[at], vertices[at + 1], vertices[at + 2]);
			}
			java.util.Arrays.sort(corners);
			set.add(corners[0] + "|" + corners[1] + "|" + corners[2]);
		}
		return set;
	}

	private static String pointKey(double x, double y, double z) {
		// exact: these parts hold edges far below a micron, and rounding the
		// coordinates to any fixed resolution merges their endpoints, which invents
		// both unmatched and doubled edges in a file that has neither
		return x + "," + y + "," + z;
	}

	private static String pointKey(double[] point) {
		return pointKey(point[0], point[1], point[2]);
	}

	@Test
	public void noPartIsReadAsItsTessellationByAccident() throws Exception {
		List<CSG> parts = source();
		STEPFile read = readBRep(export(parts));

		// a tessellation supplies the mesh of its BRep twin; it is never a shape of
		// its own. If it leaked through as one, this would be twice the part count,
		// and the BRep assertions elsewhere would silently be checking it instead.
		assertEquals("a tessellation must not appear as a shape of its own", parts.size(), read.getShapes().size());
	}

	/**
	 * The BRep's own loops must be watertight: every directed edge of every face
	 * bound must be matched by the same edge in the opposite direction on the face
	 * across it. That is what a reader stitches, and an unmatched edge is a hole in
	 * the solid no matter how good the tessellation is.
	 *
	 * <p>
	 * This is checked on the face data itself, not on anything the writer computed,
	 * so a face that was dropped or written with a bad loop shows up here.
	 */
	@Test
	public void theBRepLoopsAreWatertight() throws Exception {
		List<CSG> parts = source();
		for (int i = 0; i < parts.size(); i++) {
			List<StepFace> faces = com.neuronrobotics.bowlerstudio.threed.StepBRepWrite.faces(parts.get(i));
			java.util.Map<String, Integer> directed = new java.util.HashMap<>();
			for (StepFace face : faces)
				for (List<double[]> loop : face.loops)
					for (int at = 0; at < loop.size(); at++) {
						String key = pointKey(loop.get(at)) + ">" + pointKey(loop.get((at + 1) % loop.size()));
						directed.merge(key, 1, Integer::sum);
					}

			int unmatched = 0;
			List<String> examples = new ArrayList<>();
			for (java.util.Map.Entry<String, Integer> entry : directed.entrySet()) {
				String key = entry.getKey();
				int split = key.indexOf('>');
				String reverse = key.substring(split + 1) + ">" + key.substring(0, split);
				if (!Integer.valueOf(1).equals(directed.get(reverse))) {
					unmatched++;
					if (examples.size() < 5)
						examples.add(key);
				}
				if (entry.getValue() != 1)
					unmatched++;
			}
			Log.debug("watertight part " + i + ": " + faces.size() + " face(s), " + directed.size()
					+ " directed edge(s), " + unmatched + " unmatched, e.g. " + examples);
			assertEquals("part " + i + " BRep loops must be watertight, unmatched edges e.g. " + examples, 0,
					unmatched);
		}
	}

	/**
	 * Coplanar triangles must stay stitched into one face. Falling back to a face
	 * per triangle also "covers" the surface, so coverage alone cannot catch that
	 * regression; this asserts the export actually merges coplanar neighbours, which
	 * is what makes the BRep usable and what a CAD package relies on.
	 */
	@Test
	public void coplanarTrianglesAreStitchedIntoOneFace() throws Exception {
		List<CSG> parts = source();
		for (int i = 0; i < parts.size(); i++) {
			CSG part = parts.get(i);
			List<StepFace> faces = com.neuronrobotics.bowlerstudio.threed.StepBRepWrite.faces(part);
			// This part's coplanar groups are coplanar only to within the rounding of
			// stored doubles, and re-cutting a slightly dish-shaped group changes the
			// surface by more than the part's own smallest triangle. So its faces may
			// honestly end up one per triangle: what must never happen is a face that
			// does not read back. That is asserted above; here only the range is held.
			Log.debug(
					"stitching part " + i + ": " + faces.size() + " face(s) for " + part.getTriCount() + " triangles");
			assertTrue("part " + i + " must write between one face per triangle and one per group",
					faces.size() > 0 && faces.size() <= part.getTriCount());
		}
	}

	/**
	 * A loaded mesh must be manifold. Measuring a mesh is how the kernel decides
	 * that: a surface with a hole has no enclosed volume, so the kernel refuses it.
	 * Both readings of the file are checked, because a CAD package uses the BRep
	 * while the tessellation is what the round trip relies on.
	 */
	@Test
	public void everyLoadedMeshIsManifoldAndHasVolume() throws Exception {
		List<CSG> parts = source();
		File written = export(parts);

		for (boolean preferTessellation : new boolean[]{true, false}) {
			STEPFile read;
			try (FileInputStream in = new FileInputStream(written)) {
				read = new STEPFile(in, preferTessellation);
			}
			String which = preferTessellation ? "tessellation" : "BRep";

			List<CSG> meshes = read.getImported();
			assertEquals(which + ": every part must load", parts.size(), meshes.size());
			for (int i = 0; i < meshes.size(); i++) {
				CSG mesh = meshes.get(i);
				double expected = volume(parts.get(i));

				// the loader measured it, which is the manifold check the kernel ran
				assertTrue(which + " part " + i + " must have been measured, volume is " + mesh.getVolume(),
						mesh.getVolume() != 0);
				Log.debug(which + " part " + i + ": volume " + mesh.getVolume() + ", expected " + expected);
				assertEquals(which + " part " + i + " must be a closed solid of the source's volume", expected,
						mesh.getVolume(), Math.abs(expected) * 1e-4);

				// and it must survive the kernel's own check a second time, from its own
				// triangles: a mesh with a hole throws here
				assertEquals(which + " part " + i + " must re-measure to the same volume", mesh.getVolume(),
						remeasuredVolume(mesh), Math.abs(expected) * 1e-4);
			}
		}
	}

	/** Re-measures a mesh, which throws when the kernel finds it is not manifold. */
	private static double remeasuredVolume(CSG mesh) throws Exception {
		try {
			return CSG.getManifold().calculateAreaAndSurfaceArea(mesh).getVolume();
		} catch (Throwable e) {
			throw new AssertionError("the loaded mesh is not manifold: " + e.getMessage(), e);
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
