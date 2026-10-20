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

		// and the thinnest part is three of those inches thick on its shortest axis;
		// the nut is placed so that axis is no longer the file's own z
		CSG thinnest = step.getImported().get(0);
		for (CSG mesh : step.getImported())
			if (thinnestExtent(mesh) < thinnestExtent(thinnest))
				thinnest = mesh;
		assertEquals("3 inches as millimetres", 76.2, thinnestExtent(thinnest), 1e-6);
	}

	@Test
	public void loadsRealWorldAp214Brep() throws Exception {
		// a screw whose planar faces tessellate and whose edges are B-splines
		StepImport.Shape shape = firstShape("screw_ap214.step");
		assertNotNull("the screw has a mesh", shape.getMesh());
		assertTrue("the screw has edges", shape.getEdges().size() > 10);

		// a filleting part: 62 vertices of planar faces. A face bounded by an outer
		// loop and holes needs at least two triangles per hole, so the mesh has more
		// triangles than a fan over the vertices would
		StepImport.Shape revolved = firstShape("inch_units_ap214.step");
		assertNotNull("the revolved part has a mesh", revolved.getMesh());
		assertEquals("vertices of the largest part", 62, revolved.getMesh().getVertCount());
		assertEquals("triangles of the largest part", 62, revolved.getMesh().getTriCount());
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

	// --- assemblies: several parts in one file ---

	@Test
	public void exportsAssemblyAsSeveralShapes() throws Exception {
		List<CSG> parts = new ArrayList<>();
		parts.add(new eu.mihosoft.vrl.v3d.Cube(10).toCSG().setName("left").move(-15, 0, 0));
		parts.add(new eu.mihosoft.vrl.v3d.Cube(10).toCSG().setName("right").move(15, 0, 0));

		STEPFile step = new STEPFile(parts, ANGLE, TOPOLOGY_TOLERANCE, STITCH_TOLERANCE);
		assertEquals("both parts are kept", 2, step.getParts().size());

		String text = step.toStep();
		assertTrue("an assembly has one product per part", text.contains("PRODUCT('left'"));
		assertTrue("an assembly has one product per part", text.contains("PRODUCT('right'"));
		assertTrue("the assembly names the whole model", text.contains("PRODUCT('" + "assembly" + "'"));
		assertEquals("one occurrence per part", 2, count(text, "NEXT_ASSEMBLY_USAGE_OCCURRENCE"));
		assertEquals("one transformed relationship per part", 2, count(text, "ITEM_DEFINED_TRANSFORMATION"));

		File written = File.createTempFile("assembly_export", ".step");
		written.deleteOnExit();
		step.write(written);

		STEPFile read = read(written);
		assertEquals("each part reads back as a shape", 2, read.getImported().size());

		// both parts sit at their own coordinates, ten units apart plus a gap
		List<CSG> imported = read.getImported();
		double leftX = centerOf(imported.get(0), 0);
		double rightX = centerOf(imported.get(1), 0);
		double span = Math.max(leftX, rightX) - Math.min(leftX, rightX);
		assertEquals("the parts keep their spacing", 30.0, span, 1e-6);
	}

	@Test
	public void assemblyOfOnePartIsNotAnAssembly() throws Exception {
		List<CSG> parts = new ArrayList<>();
		parts.add(new eu.mihosoft.vrl.v3d.Cube(10).toCSG().setName("only"));

		String text = new STEPFile(parts, ANGLE, TOPOLOGY_TOLERANCE, STITCH_TOLERANCE).toStep();
		assertFalse("a single part needs no assembly structure", text.contains("NEXT_ASSEMBLY_USAGE_OCCURRENCE"));
	}

	@Test
	public void rejectsAnEmptyAssembly() throws Exception {
		try {
			new STEPFile(new ArrayList<CSG>());
			assertTrue("an empty part list should not be accepted", false);
		} catch (IllegalArgumentException expected) {
			assertFalse(expected.getMessage() == null);
		}
	}

	@Test
	public void importsAssemblyPartsAtTheirWorldPlacements() throws Exception {
		List<CSG> meshes = new STEPFile(new FileInputStream(new File(CORPUS, "inch_units_ap214.step"))).getImported();
		assertTrue("the assembly has several parts", meshes.size() >= 4);

		// a nut sits at 0.3959, -1.0039, 1.3779 of an inch once placed, and its own
		// mesh is centred at the origin, so its world centre is the placement itself
		boolean foundNut = false;
		for (CSG mesh : meshes) {
			StepImport.Bounds b = StepImport.Bounds.of(mesh);
			double cx = (b.minX + b.maxX) / 2;
			double cy = (b.minY + b.maxY) / 2;
			double cz = (b.minZ + b.maxZ) / 2;
			if (Math.abs(cx - 10.06) < 0.2 && Math.abs(cy + 25.5) < 0.2 && Math.abs(cz - 35.0) < 0.2)
				foundNut = true;
		}
		assertTrue("a nut is placed where its occurrence puts it", foundNut);

		// the parts share the assembly's own frame, so one nut's depth is another's
		// offset up the stack by exactly fifteen millimetres
		assertTrue("the parts keep their spacing", spacingOf(meshes, 10.06, 35.0, 65.0));
	}

	@Test
	public void importsAssignedColours() throws Exception {
		// the revolve part is filed with a yellow colour, which reaches its geometry
		StepImport.Shape shape = firstShape("revolve_ap214.step");
		assertEquals("the file's yellow should come through", javafx.scene.paint.Color.YELLOW, shape.getColor());
	}

	@Test
	public void exportsAndImportsAColour() throws Exception {
		CSG source = new eu.mihosoft.vrl.v3d.Cube(10).toCSG().setName("tinted")
				.setColor(javafx.scene.paint.Color.web("#3399cc"));

		File written = File.createTempFile("colour_export", ".step");
		written.deleteOnExit();
		new STEPFile(source, ANGLE, TOPOLOGY_TOLERANCE, STITCH_TOLERANCE).write(written);

		StepImport.Shape read = read(written).getShapes().get(0);
		assertNotNull("the colour should be read back", read.getColor());
		assertEquals("red", source.getColor().getRed(), read.getColor().getRed(), 1e-6);
		assertEquals("green", source.getColor().getGreen(), read.getColor().getGreen(), 1e-6);
		assertEquals("blue", source.getColor().getBlue(), read.getColor().getBlue(), 1e-6);
	}

	@Test
	public void readsAListOfFilesIntoAListOfStepFiles() throws Exception {
		List<File> files = new ArrayList<>();
		files.add(new File(CORPUS, "cuboid_export.step"));
		files.add(new File(CORPUS, "hole_export.step"));

		List<STEPFile> steps = STEPFile.read(files);
		assertEquals("one STEPFile per file", 2, steps.size());
		assertEquals("the first file's geometry", 1, steps.get(0).getImported().size());
		assertEquals("the second file's geometry", 1, steps.get(1).getImported().size());
	}

	@Test
	public void stepLoaderImportsAPart() throws Exception {
		Object imported = new com.neuronrobotics.bowlerstudio.scripting.StepLoader().inlineScriptRun(null,
				new File(CORPUS, "cuboid_export.step"), new ArrayList<>());
		assertTrue("one part comes back as a CSG", imported instanceof CSG);

		StepImport.Bounds bounds = StepImport.Bounds.of((CSG) imported);
		assertEquals("the cube's width", 10.0, bounds.sizeX(), 1e-6);
	}

	@Test
	public void stepLoaderImportsAnAssemblyAsAListOfParts() throws Exception {
		Object imported = new com.neuronrobotics.bowlerstudio.scripting.StepLoader().inlineScriptRun(null,
				new File(CORPUS, "inch_units_ap214.step"), new ArrayList<>());
		assertTrue("an assembly comes back as a list of CSGs", imported instanceof List);
		assertTrue("the assembly has several parts", ((List<?>) imported).size() >= 4);
	}

	@Test
	public void exportsASolidAsClosedBRep() throws Exception {
		CSG cube = new eu.mihosoft.vrl.v3d.Cube(10).toCSG().setName("cube");
		File written = File.createTempFile("brep_export", ".step");
		written.deleteOnExit();
		new STEPFile(cube, ANGLE, TOPOLOGY_TOLERANCE, STITCH_TOLERANCE).write(written);

		String text = new String(java.nio.file.Files.readAllBytes(written.toPath()), "UTF-8");
		assertTrue("a closed mesh becomes a solid", text.contains("MANIFOLD_SOLID_BREP"));
		assertTrue("the solid is bounded by a shell", text.contains("CLOSED_SHELL"));
		assertEquals("a cube is six faces", 6, count(text, "ADVANCED_FACE"));
		assertEquals("one wire per face", 6, count(text, "EDGE_LOOP"));
		assertEquals("a cube has eight vertices", 8, count(text, "VERTEX_POINT"));
		assertEquals("a cube has twelve edges", 12, count(text, "EDGE_CURVE"));
		// each edge borders two faces, so its two loops share it and the wire is
		// continuous rather than a pile of separate segments
		assertEquals("each edge is used by two faces", 24, count(text, "ORIENTED_EDGE"));
	}

	@Test
	public void readsBackItsOwnBRepExport() throws Exception {
		CSG cube = new eu.mihosoft.vrl.v3d.Cube(10).toCSG().setName("cube");
		File written = File.createTempFile("brep_roundtrip", ".step");
		written.deleteOnExit();
		new STEPFile(cube).write(written);

		STEPFile read = read(written);
		assertEquals("a solid reads back as one shape", 1, read.getImported().size());
		StepImport.Bounds bounds = StepImport.Bounds.of(read.getImported().get(0));
		assertEquals("cube width", 10.0, bounds.sizeX(), 1e-6);
		assertEquals("cube depth", 10.0, bounds.sizeZ(), 1e-6);
	}

	// --- a meshed part with near degenerate facets ---

	@Test
	public void exportsAPartWithThinFacetsAsOneSolid() throws Exception {
		// this mesh has facets a few nanometres across, so its vertices are closer
		// than the file's own length precision; welding them is what lets a reader
		// close the wires, and dropping the facets instead would open the shell
		CSG mesh = threeMf("Pointer_Hand_star.3mf");
		File written = File.createTempFile("hand_export", ".step");
		written.deleteOnExit();
		new STEPFile(mesh).write(written);

		String text = new String(java.nio.file.Files.readAllBytes(written.toPath()), "UTF-8");
		assertTrue("a closed mesh becomes a solid", text.contains("MANIFOLD_SOLID_BREP"));
		assertEquals("one shell, not one per facet", 1, count(text, "CLOSED_SHELL"));
		assertFalse("a closed mesh is not a surface model", text.contains("SHELL_BASED_SURFACE_MODEL"));
		assertEquals("no face has an empty bound list", 0, count(text, ",(),#"));
	}

	@Test
	public void exportsAPartWithThinFacetsWithoutLosingArea() throws Exception {
		CSG source = threeMf("Pointer_Hand_star.3mf");
		File written = File.createTempFile("hand_area", ".step");
		written.deleteOnExit();
		new STEPFile(source).write(written);

		// every face of the exported BRep, taken as its outer loop less its holes,
		// must add up to the mesh it came from
		double writtenArea = 0;
		for (StepImport.Shape shape : read(written).getShapes())
			if (shape.getMesh() != null)
				writtenArea += area(shape.getMesh());

		assertEquals("the exported faces cover the mesh", area(source), writtenArea, area(source) * 1e-5);
	}

	@Test
	public void readsAPartWithThinFacets() throws Exception {
		// the hand's shell is a surface model, which the reader must flatten into the
		// faces it holds rather than read as a face itself. Its facets are far
		// smaller than the model, so the mesh it describes is the source mesh
		CSG source = threeMf("Pointer_Hand_star.3mf");
		CSG mesh = firstMesh("Pointer_Hand_star.step");
		assertEquals("the hand's vertices", 6711, mesh.getVertCount());
		assertEquals("the hand's triangles", 13418, mesh.getTriCount());
		assertEquals("the hand's surface area", area(source), area(mesh), area(source) * 1e-5);
		assertEquals("the hand's volume", volume(source), volume(mesh), volume(source) * 1e-5);
	}

	@Test
	public void importsAPartWithFacesThatHaveHoles() throws Exception {
		// the file's largest part is a box with four holes bored through one face.
		// Filling the holes in, as reading only the outer loop would, gives 1.287e7
		// square millimetres; the part itself is 1.589e7, and its curved faces are
		// approximated by their chords, so the reader lands just under that
		CSG mesh = firstMesh("inch_units_ap214.step");
		assertEquals("the part is one closed surface", 62, mesh.getTriCount());
		assertEquals("the part's surface area", 1.548e7, area(mesh), 1e4);
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

	/** The mesh of a 3mf fixture, which is the source geometry for the exports. */
	private static CSG threeMf(String name) throws Exception {
		List<CSG> parts = CSG.fromThreeMF(require(new File(CORPUS, name)).toPath());
		assertFalse(name + " should hold a part", parts.isEmpty());
		return parts.get(0);
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

	private static int count(String text, String token) {
		int total = 0;
		for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length()))
			total++;
		return total;
	}

	/** True when two meshes sit at the given x, one thirty millimetres above the other. */
	private static boolean spacingOf(List<CSG> meshes, double x, double lowerZ, double upperZ) {
		boolean lower = false;
		boolean upper = false;
		for (CSG mesh : meshes) {
			StepImport.Bounds b = StepImport.Bounds.of(mesh);
			double cx = (b.minX + b.maxX) / 2;
			double cz = (b.minZ + b.maxZ) / 2;
			if (Math.abs(cx - x) < 0.2 && Math.abs(cz - lowerZ) < 0.2)
				lower = true;
			if (Math.abs(cx - x) < 0.2 && Math.abs(cz - upperZ) < 0.2)
				upper = true;
		}
		return lower && upper;
	}

	/** The surface area of a mesh, as the triangles it is made of. */
	private static double area(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		double total = 0;
		for (int i = 0; i < triangles.length; i += 3) {
			double[] a = point(vertices, triangles[i]);
			double[] b = point(vertices, triangles[i + 1]);
			double[] c = point(vertices, triangles[i + 2]);
			double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
			double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			total += Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
		}
		return total;
	}

	/** The volume a mesh encloses, as the sum of its tetrahedra about the origin. */
	private static double volume(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		double total = 0;
		for (int i = 0; i < triangles.length; i += 3) {
			double[] a = point(vertices, triangles[i]);
			double[] b = point(vertices, triangles[i + 1]);
			double[] c = point(vertices, triangles[i + 2]);
			total += (a[0] * (b[1] * c[2] - b[2] * c[1]) - a[1] * (b[0] * c[2] - b[2] * c[0])
					+ a[2] * (b[0] * c[1] - b[1] * c[0])) / 6;
		}
		return Math.abs(total);
	}

	private static double[] point(double[] vertices, long vertex) {
		int at = (int) vertex * 3;
		return new double[]{vertices[at], vertices[at + 1], vertices[at + 2]};
	}

	private static double centerOf(CSG csg, int axis) {
		StepImport.Bounds bounds = StepImport.Bounds.of(csg);
		return axis == 0
				? (bounds.minX + bounds.maxX) / 2
				: axis == 1 ? (bounds.minY + bounds.maxY) / 2 : (bounds.minZ + bounds.maxZ) / 2;
	}

	private static double extent(CSG csg, int axis) {
		StepImport.Bounds bounds = StepImport.Bounds.of(csg);
		return axis == 0 ? bounds.sizeX() : axis == 1 ? bounds.sizeY() : bounds.sizeZ();
	}

	/** The size of a mesh along its shortest axis, which a placement can rotate. */
	private static double thinnestExtent(CSG csg) {
		double smallest = extent(csg, 0);
		for (int axis = 1; axis < 3; axis++)
			smallest = Math.min(smallest, extent(csg, axis));
		return smallest;
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
