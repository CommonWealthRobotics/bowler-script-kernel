package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.FeatureEdgeExtractor;
import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.bowlerstudio.threed.StepImport;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Round trips geometry through {@link STEPFile} and loads a corpus of STEP files
 * written by other tools.
 *
 * <p>
 * The fixtures live in {@code testStepFiles} at the top of the module, which is
 * the working directory Gradle runs tests from.
 */
@Ignore
public class StepFileTest {

	private static final File CORPUS = new File("testStepFiles");

	private static final double ANGLE = 35.0;
	private static final double TOPOLOGY_TOLERANCE = 0.00001;
	private static final double STITCH_TOLERANCE = 0.002;

	// --- round trip: what this class writes, it reads back ---

	@Test
	public void exportsRoundTripThroughItsOwnReader() throws Exception {
		for (String name : new String[]{"cuboid_export.step", "hole_export.step", "cylinder_export.step"}) {
			STEPFile written = export(new File(CORPUS, name));
			CSG source = written.getCsg();
			double[] sourceVertices = source.getVertices().clone();
			long[] sourceTriangles = source.getTriangles().clone();
			List<FeatureEdgeExtractor.Edge> sourceEdges = written.getEdges();

			STEPFile read = read(new File(CORPUS, name));

			assertNull("an imported file has no source CSG", read.getCsg());
			assertTrue("imported geometry should not be empty for " + name, read.getImported().size() >= 1);

			CSG imported = read.getImported().get(0);
			assertEquals(name + " vertex count", sourceVertices.length / 3, imported.getVertCount());
			assertEquals(name + " triangle count", sourceTriangles.length / 3, imported.getTriCount());

			// the mesh must survive exactly: same vertices in the same order, same indices
			double[] readVertices = imported.getVertices();
			for (int i = 0; i < sourceVertices.length; i++)
				assertEquals(name + " vertex " + i, sourceVertices[i], readVertices[i], 1e-9);

			long[] readTriangles = imported.getTriangles();
			for (int i = 0; i < sourceTriangles.length; i++)
				assertEquals(name + " index " + i, sourceTriangles[i], readTriangles[i]);

			// and the edges must survive as geometry, segment for segment
			List<FeatureEdgeExtractor.Edge> readEdges = read.getShapes().get(0).getEdges();
			assertEquals(name + " edge count", sourceEdges.size(), readEdges.size());
			for (int i = 0; i < sourceEdges.size(); i++) {
				assertEdgeEquals(name + " edge " + i, sourceEdges.get(i), readEdges.get(i));
			}
		}
	}

	@Test
	public void exportBoundsSurviveTheRoundTrip() throws Exception {
		STEPFile written = export(new File(CORPUS, "hole_export.step"));
		StepImport.Bounds source = StepImport.Bounds.of(written.getCsg());
		StepImport.Bounds read = StepImport.Bounds.of(read(new File(CORPUS, "hole_export.step")).getImported().get(0));

		assertEquals("min x", source.minX, read.minX, 1e-9);
		assertEquals("min y", source.minY, read.minY, 1e-9);
		assertEquals("min z", source.minZ, read.minZ, 1e-9);
		assertEquals("max x", source.maxX, read.maxX, 1e-9);
		assertEquals("max y", source.maxY, read.maxY, 1e-9);
		assertEquals("max z", source.maxZ, read.maxZ, 1e-9);
	}

	@Test
	public void exportBeforeLoadIsUnchanged() throws Exception {
		STEPFile step = export(new File(CORPUS, "cuboid_export.step"));
		assertNotNull("a written file has a source CSG", step.getCsg());
		assertEquals("a written file has no imported shapes", 0, step.getShapes().size());
		assertEquals("a written file has no imported meshes", 0, step.getImported().size());
		assertEquals("a cube has twelve feature edges", 12, step.getEdges().size());
		assertTrue("the text is a STEP file", step.toStep().startsWith("ISO-10303-21;"));
	}

	// --- loading files written by other tools ---

	@Test
	public void loadsTessellatedMeshFromOcct() throws Exception {
		CSG mesh = firstMesh("box_brep.step");
		assertEquals("octant vertex count", 8, mesh.getVertCount());
		assertEquals("octant triangle count", 12, mesh.getTriCount());
		assertEquals("octant size x", 10.0, extent(mesh, 0), 1e-9);
		assertEquals("octant size y", 10.0, extent(mesh, 1), 1e-9);
		assertEquals("octant size z", 10.0, extent(mesh, 2), 1e-9);
		assertEquals("edges of an octant", 12, edges("box_brep.step"));
	}

	@Test
	public void loadsBrepSolidLoopsAsEdges() throws Exception {
		// the flat caps of a cylinder are faceted, so they tessellate, and the whole
		// outline comes through as edge loops
		StepImport.Shape shape = firstShape("cylinder_brep.step");
		assertNotNull("the cylinder caps should tessellate", shape.getMesh());
		assertTrue("a cylinder should yield its loops", shape.getEdges().size() >= 3);

		StepImport.Bounds bounds = StepImport.Bounds.of(shape.getEdges());
		assertEquals("cylinder diameter", 80.0, bounds.sizeX(), 1e-6);
		assertEquals("cylinder diameter", 80.0, bounds.sizeY(), 1e-6);
		assertEquals("cylinder height", 100.0, bounds.sizeZ(), 1e-6);
	}

	@Test
	public void loadsWireframeCurves() throws Exception {
		STEPFile step = read(new File(CORPUS, "wireframe.step"));
		assertEquals("a wireframe has no mesh", 0, step.getImported().size());
		assertEquals("one wireframe shape", 1, step.getShapes().size());

		// the closed square wire is four segments
		assertEquals("square wire segments", 4, step.getShapes().get(0).getEdges().size());
		StepImport.Bounds bounds = StepImport.Bounds.of(step.getShapes().get(0).getEdges());
		assertEquals("wire width", 50.0, bounds.sizeX(), 1e-6);
		assertEquals("wire height", 30.0, bounds.sizeY(), 1e-6);
	}

	@Test
	public void convertsNonMillimetreUnits() throws Exception {
		// this file's own context assigns a conversion based unit of one inch, and
		// one of its parts is 50 x 60 x 100 of those units, so 1270 x 1524 x 2540 mm
		STEPFile step = read(new File(CORPUS, "inch_units_ap214.step"));
		assertFalse("the inch file has geometry", step.getImported().isEmpty());

		CSG largest = step.getImported().get(0);
		for (CSG mesh : step.getImported())
			if (extent(mesh, 2) > extent(largest, 2))
				largest = mesh;

		assertEquals("100 inches as millimetres", 2540.0, extent(largest, 2), 1e-6);
		assertEquals("50 inches as millimetres", 1270.0, extent(largest, 0), 1e-6);

		// and the thinnest part is three of those inches thick
		CSG thinnest = step.getImported().get(0);
		for (CSG mesh : step.getImported())
			if (extent(mesh, 2) < extent(thinnest, 2))
				thinnest = mesh;
		assertEquals("3 inches as millimetres", 76.2, extent(thinnest, 2), 1e-6);
	}

	@Test
	public void loadsRealWorldAp214Brep() throws Exception {
		// a screw whose planar faces tessellate and whose edges are B-splines
		StepImport.Shape shape = firstShape("screw_ap214.step");
		assertNotNull("the screw has a mesh", shape.getMesh());
		assertTrue("the screw has edges", shape.getEdges().size() > 10);

		// a filleting part: 62 vertices and 70 triangles of planar faces
		StepImport.Shape revolved = firstShape("inch_units_ap214.step");
		assertNotNull("the revolved part has a mesh", revolved.getMesh());
		assertEquals("vertices of the largest part", 62, revolved.getMesh().getVertCount());
		assertEquals("triangles of the largest part", 70, revolved.getMesh().getTriCount());
	}

	@Test
	public void readsEveryFileInTheCorpusWithoutFailing() throws Exception {
		List<String> failures = new ArrayList<>();
		for (File file : corpus()) {
			try (FileInputStream in = new FileInputStream(file)) {
				new STEPFile(in);
			} catch (Exception e) {
				failures.add(file.getName() + ": " + e);
			}
		}
		assertTrue("every corpus file should load, failures: " + failures, failures.isEmpty());
	}

	@Test
	public void rejectsMalformedInput() throws Exception {
		try {
			StepImport.readShapes(new java.io.ByteArrayInputStream("not a step file".getBytes("UTF-8")));
			assertTrue("malformed input should not be accepted", false);
		} catch (Exception expected) {
			// the reader must complain rather than return empty geometry silently
			assertFalse(expected.getMessage() == null);
		}
	}

	// --- helpers ---

	private static List<File> corpus() {
		File[] files = CORPUS.listFiles((directory, name) -> name.endsWith(".step") || name.endsWith(".stp"));
		assertNotNull("the corpus directory " + CORPUS.getAbsolutePath() + " must exist", files);
		assertTrue("the corpus should contain files", files.length > 0);
		List<File> sorted = new ArrayList<>();
		for (File file : files)
			sorted.add(file);
		sorted.sort(null);
		return sorted;
	}

	/** Writes the named CSG again, to compare against what a load produces. */
	private static STEPFile export(File file) throws Exception {
		String name = require(file).getName().replace(".step", "");
		CSG csg = fixture(name);
		return new STEPFile(csg, ANGLE, TOPOLOGY_TOLERANCE, STITCH_TOLERANCE);
	}

	private static CSG fixture(String name) throws Exception {
		switch (name) {
			case "cuboid_export" :
				return new eu.mihosoft.vrl.v3d.Cube(10).toCSG();
			case "hole_export" :
				return new eu.mihosoft.vrl.v3d.Cube(20, 20, 4).toCSG()
						.difference(new eu.mihosoft.vrl.v3d.Sphere(6, 24, 12).toCSG().move(10, 10, 2));
			case "cylinder_export" :
				return new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 32).toCSG()
						.union(new eu.mihosoft.vrl.v3d.Cylinder(2, 40, 32).toCSG());
			default :
				throw new IllegalArgumentException("no fixture named " + name);
		}
	}

	private static STEPFile read(File file) throws Exception {
		try (FileInputStream in = new FileInputStream(require(file))) {
			return new STEPFile(in);
		}
	}

	private static File require(File file) throws FileNotFoundException {
		if (!file.isFile())
			throw new FileNotFoundException(file.getAbsolutePath() + " is missing from the corpus");
		return file;
	}

	private static CSG firstMesh(String name) throws Exception {
		CSG mesh = firstShape(name).getMesh();
		assertNotNull(name + " should have a mesh", mesh);
		return mesh;
	}

	private static StepImport.Shape firstShape(String name) throws Exception {
		List<StepImport.Shape> shapes = read(new File(CORPUS, name)).getShapes();
		assertFalse(name + " should have a shape", shapes.isEmpty());
		return shapes.get(0);
	}

	private static int edges(String name) throws Exception {
		int total = 0;
		for (StepImport.Shape shape : read(new File(CORPUS, name)).getShapes())
			total += shape.getEdges().size();
		return total;
	}

	private static double extent(CSG csg, int axis) {
		StepImport.Bounds bounds = StepImport.Bounds.of(csg);
		return axis == 0 ? bounds.sizeX() : axis == 1 ? bounds.sizeY() : bounds.sizeZ();
	}

	private static void assertEdgeEquals(String message, FeatureEdgeExtractor.Edge expected,
			FeatureEdgeExtractor.Edge actual) {
		assertPoint(message + " start", expected.a, actual.a);
		assertPoint(message + " end", expected.b, actual.b);
	}

	private static void assertPoint(String message, eu.mihosoft.vrl.v3d.Vector3d expected,
			eu.mihosoft.vrl.v3d.Vector3d actual) {
		assertEquals(message + " x", expected.x, actual.x, 1e-9);
		assertEquals(message + " y", expected.y, actual.y, 1e-9);
		assertEquals(message + " z", expected.z, actual.z, 1e-9);
	}
}
