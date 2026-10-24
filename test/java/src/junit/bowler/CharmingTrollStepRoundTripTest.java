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

import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.bowlerstudio.threed.StepFace;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.TickToc;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Round trips the trolley group fixture through {@link STEPFile}: a 3MF's mesh is
 * exported as STEP, read back, and must reproduce the source geometry exactly,
 * with no points invented and none lost.
 *
 * <p>
 * The 3MF is the source of truth. Its mesh comes from Manifold, so it is
 * watertight by construction. The export writes both the planar BRep a CAD
 * package edits and a tessellation of the mesh itself; the reader takes the
 * tessellation, so vertices and indices must survive unchanged.
 */
public class CharmingTrollStepRoundTripTest {

	private static final File CORPUS = new File("testStepFiles");
	private static final String SOURCE = "Charming_Troll_Group_of_3.3mf";

	/** Profiling is opt-in in TickToc and Log, so switch both on for these tests. */
	@BeforeClass
	public static void enableProfiling() {
		Log.enableDebugPrint();
		TickToc.setEnabled(true);
	}

	/** Each test times its own run, so a previous failure's markers are discarded. */
	@Before
	public void startTiming() {
		// a test that failed before its toc() leaves markers behind; start clean, then
		// give toc() the start event it expects as the first entry
		TickToc.clear();
		TickToc.tic("test start");
	}

	/** The 3MF's own meshes, one per object, which are the ground truth. */
	private static List<CSG> source() throws Exception {
		Log.debug("source: reading " + SOURCE);
		TickToc.tic("load 3MF");
		File file = new File(CORPUS, SOURCE);
		assertTrue("the 3MF fixture must exist at " + file.getAbsolutePath(), file.isFile());
		List<CSG> parts = CSG.fromThreeMF(file.toPath());
		assertFalse("the 3MF should hold parts", parts.isEmpty());
		long vertices = 0;
		long triangles = 0;
		for (CSG part : parts) {
			vertices += part.getVertCount();
			triangles += part.getTriCount();
		}
		Log.debug("source: " + parts.size() + " part(s), " + vertices + " vertices, " + triangles + " triangles");
		return parts;
	}

	/** A STEP file written from the given parts, in a temporary file. */
	private static File export(List<CSG> parts) throws Exception {
		Log.debug("export: writing " + parts.size() + " part(s) to STEP");
		TickToc.tic("write STEP");
		File written = File.createTempFile("charming_troll", ".step");
		written.deleteOnExit();
		new STEPFile(parts).write(written);
		Log.debug("export: wrote " + written.length() + " bytes to " + written.getAbsolutePath());
		return written;
	}

	private static STEPFile read(File file) throws Exception {
		Log.debug("read: parsing " + file.getAbsolutePath());
		TickToc.tic("read STEP");
		try (FileInputStream in = new FileInputStream(file)) {
			return new STEPFile(in);
		}
	}

	// --- the mesh itself ---

	@Test
	public void exportedStepReproducesTheSourceMeshExactly() throws Exception {
		Log.debug("---- exportedStepReproducesTheSourceMeshExactly ----");
		List<CSG> parts = source();
		File export = export(parts);
		STEPFile imported = read(export);

		Log.debug("compare: shape count " + parts.size() + " vs " + imported.getImported().size());
		assertEquals("one shape per part", parts.size(), imported.getImported().size());
		for (int i = 0; i < parts.size(); i++) {
			CSG expected = parts.get(i);
			CSG actual = imported.getImported().get(i);
			String name = "part " + i;
			Log.debug("compare " + name + ": " + expected.getVertCount() + "v/" + expected.getTriCount() + "t vs "
					+ actual.getVertCount() + "v/" + actual.getTriCount() + "t");

			assertEquals(name + " vertex count", expected.getVertCount(), actual.getVertCount());
			assertEquals(name + " triangle count", expected.getTriCount(), actual.getTriCount());

			TickToc.tic("compare vertices " + name);
			double[] expectedVertices = expected.getVertices();
			double[] actualVertices = actual.getVertices();
			for (int v = 0; v < expectedVertices.length; v++)
				assertEquals(name + " vertex coordinate " + v, Double.doubleToLongBits(expectedVertices[v]),
						Double.doubleToLongBits(actualVertices[v]));

			TickToc.tic("compare triangles " + name);
			long[] expectedTriangles = expected.getTriangles();
			long[] actualTriangles = actual.getTriangles();
			for (int t = 0; t < expectedTriangles.length; t++)
				assertEquals(name + " triangle index " + t, expectedTriangles[t], actualTriangles[t]);
		}
		TickToc.toc();
	}

	@Test
	public void exportedStepAddsNoPointsAndLosesNone() throws Exception {
		Log.debug("---- exportedStepAddsNoPointsAndLosesNone ----");
		List<CSG> parts = source();
		STEPFile imported = read(export(parts));

		for (int i = 0; i < parts.size(); i++) {
			CSG expected = parts.get(i);
			CSG actual = imported.getImported().get(i);
			Log.debug("bounds " + i + ": " + expected.getVertCount() + "v/" + expected.getTriCount() + "t vs "
					+ actual.getVertCount() + "v/" + actual.getTriCount() + "t");
			assertEquals("part " + i + " must not gain or lose points", expected.getVertCount(), actual.getVertCount());
			assertEquals("part " + i + " must not gain or lose faces", expected.getTriCount(), actual.getTriCount());

			// nothing may sit outside the source's own bounds
			assertBounds("part " + i, expected, actual);
		}
		TickToc.toc();
	}

	// --- the surface a CAD package shows ---

	@Test
	public void exportedFacesMatchWhatTheReaderReconstructs() throws Exception {
		Log.debug("---- exportedFacesMatchWhatTheReaderReconstructs ----");
		List<CSG> parts = source();
		STEPFile imported = read(export(parts));

		// the surfaces the export writes, part by part
		List<List<StepFace>> written = new ArrayList<>();
		int partIndex = 0;
		for (CSG part : parts) {
			TickToc.tic("writer faces part " + partIndex);
			List<StepFace> faces = com.neuronrobotics.bowlerstudio.threed.StepBRepWrite.faces(part);
			assertFalse("every part must describe faces", faces.isEmpty());
			written.add(faces);
			Log.debug("writer faces part " + partIndex + ": " + faces.size() + " face(s), "
					+ faces.stream().mapToInt(face -> face.loops.size()).sum() + " bound(s)");
			partIndex++;
		}

		// and the surfaces the file describes, as a reader reconstructs them
		List<List<StepFace>> fromDisk = new ArrayList<>();
		TickToc.tic("reader faces");
		for (com.neuronrobotics.bowlerstudio.threed.StepImport.Shape shape : imported.getShapes())
			fromDisk.add(shape.getFaces());
		Log.debug("reader faces: " + fromDisk.stream().mapToInt(List::size).sum() + " face(s) over " + fromDisk.size()
				+ " shape(s)");

		assertEquals("one shape per part", written.size(), fromDisk.size());
		for (int part = 0; part < written.size(); part++) {
			List<StepFace> expected = written.get(part);
			List<StepFace> actual = fromDisk.get(part);
			Log.debug("faces part " + part + ": " + expected.size() + " written vs " + actual.size() + " read");
			assertEquals("part " + part + " face count", expected.size(), actual.size());
			for (int i = 0; i < expected.size(); i++)
				assertFaceEquals("part " + part + " face " + i, expected.get(i), actual.get(i));
		}
		TickToc.toc();
	}

	@Test
	public void everyExportedFaceIsPlanarAndBounded() throws Exception {
		Log.debug("---- everyExportedFaceIsPlanarAndBounded ----");
		int partIndex = 0;
		for (CSG part : source()) {
			TickToc.tic("faces part " + partIndex);
			List<StepFace> faces = com.neuronrobotics.bowlerstudio.threed.StepBRepWrite.faces(part);
			assertFalse("the export must describe faces", faces.isEmpty());

			for (int i = 0; i < faces.size(); i++) {
				StepFace face = faces.get(i);
				assertTrue("face " + i + " must lie in its plane", face.isPlanar());
				assertFalse("face " + i + " must be bounded", face.loops.isEmpty());
				for (List<double[]> loop : face.loops)
					assertTrue("face " + i + " loops must enclose an area", loop.size() >= 3);
			}
			Log.debug("faces part " + partIndex + ": " + faces.size() + " face(s) all planar and bounded");
			partIndex++;
		}
		TickToc.toc();
	}

	@Test
	public void aSolidIsWrittenAsAClosedShell() throws Exception {
		Log.debug("---- aSolidIsWrittenAsAClosedShell ----");
		List<CSG> parts = source();
		File written = export(parts);

		TickToc.tic("scan written STEP");
		String text = new String(java.nio.file.Files.readAllBytes(written.toPath()), "UTF-8");
		Log.debug("scan: " + text.length() + " characters, " + count(text, "CLOSED_SHELL") + " closed shell(s), "
				+ count(text, "OPEN_SHELL") + " open shell(s)");
		StringBuilder reason = new StringBuilder();
		for (int i = 0; i < parts.size(); i++)
			reason.append("part ").append(i).append(": ").append(edgeReport(parts.get(i))).append("; ");
		Log.debug("mesh edges: " + reason);
		assertEquals("a closed mesh must be written as a closed shell, not an open surface: "
				+ count(text, "OPEN_SHELL") + " open shell(s) were written [" + reason + "]", parts.size(),
				count(text, "CLOSED_SHELL"));
		assertEquals("a watertight mesh is not a surface model", 0, count(text, "SHELL_BASED_SURFACE_MODEL"));
		// an unbounded face is what makes a reader cover its whole plane and invent
		// geometry, so every advanced face must name at least one bound
		TickToc.tic("scan for unbounded faces");
		assertEquals("every face must name a bound", 0, unboundedFaces(text));
		TickToc.toc();
	}

	@Test
	public void theTessellationIsRelatedToTheBRep() throws Exception {
		Log.debug("---- theTessellationIsRelatedToTheBRep ----");
		List<CSG> parts = source();
		TickToc.tic("build STEP text");
		String text = new STEPFile(parts).toStep();
		Log.debug("text: " + text.length() + " characters");
		assertTrue("the mesh must be carried as a tessellation", text.contains("TESSELLATED_SHAPE_REPRESENTATION"));
		assertTrue("the tessellation must be tied to the BRep", text.contains("SHAPE_REPRESENTATION_RELATIONSHIP"));
		assertEquals("every part gets a tessellation of its own", parts.size(),
				count(text, "TESSELLATED_SHAPE_REPRESENTATION"));
		// one shape definition per part, plus the assembly when there is more than one
		assertEquals("the BRep and its tessellation are both written", parts.size() + (parts.size() > 1 ? 1 : 0),
				count(text, "SHAPE_DEFINITION_REPRESENTATION"));
		TickToc.toc();
	}

	// --- helpers ---

	private static void assertBounds(String message, CSG expected, CSG actual) {
		double[] e = expected.getVertices();
		double[] a = actual.getVertices();
		for (int axis = 0; axis < 3; axis++) {
			double min = Double.POSITIVE_INFINITY;
			double max = Double.NEGATIVE_INFINITY;
			for (int i = axis; i < a.length; i += 3) {
				min = Math.min(min, a[i]);
				max = Math.max(max, a[i]);
			}
			double expectedMin = Double.POSITIVE_INFINITY;
			double expectedMax = Double.NEGATIVE_INFINITY;
			for (int i = axis; i < e.length; i += 3) {
				expectedMin = Math.min(expectedMin, e[i]);
				expectedMax = Math.max(expectedMax, e[i]);
			}
			assertEquals(message + " min " + axis, expectedMin, min, 1e-9);
			assertEquals(message + " max " + axis, expectedMax, max, 1e-9);
		}
	}

	/** Two faces describe the same plane and the same boundary loops. */
	private static void assertFaceEquals(String message, StepFace expected, StepFace actual) {
		double dot = expected.normal.x * actual.normal.x + expected.normal.y * actual.normal.y
				+ expected.normal.z * actual.normal.z;
		assertEquals(message + " faces must look the same way", 1.0, dot, 1e-9);
		for (int axis = 0; axis < 3; axis++)
			assertEquals(message + " plane origin " + axis, expected.origin[axis], actual.origin[axis], 1e-9);

		assertEquals(message + " loop count", expected.loops.size(), actual.loops.size());
		for (int i = 0; i < expected.loops.size(); i++)
			assertLoopEquals(message + " loop " + i, expected.loops.get(i), actual.loops.get(i));
	}

	/** Loops describe the same cycle, whichever point the walk starts at. */
	private static void assertLoopEquals(String message, List<double[]> expected, List<double[]> actual) {
		assertEquals(message + " point count", expected.size(), actual.size());
		if (expected.isEmpty())
			return;

		List<double[]> a = canonical(expected);
		List<double[]> b = canonical(actual);
		if (sameCycle(a, b))
			return;
		assertTrue(message + " must follow the same loop, forward or backward", sameCycle(a, reversed(b)));
	}

	private static boolean sameCycle(List<double[]> a, List<double[]> b) {
		for (int i = 0; i < a.size(); i++)
			for (int axis = 0; axis < 3; axis++)
				if (Math.abs(a.get(i)[axis] - b.get(i)[axis]) > 1e-9)
					return false;
		return true;
	}

	private static List<double[]> reversed(List<double[]> loop) {
		List<double[]> reversed = new ArrayList<>(loop.size());
		for (int i = loop.size() - 1; i >= 0; i--)
			reversed.add(loop.get(i));
		return reversed;
	}

	/** Rotates a loop so it starts at its smallest point, making the comparison orientation independent. */
	private static List<double[]> canonical(List<double[]> loop) {
		int best = 0;
		for (int i = 1; i < loop.size(); i++)
			if (compare(loop.get(i), loop.get(best)) < 0)
				best = i;

		List<double[]> rotated = new ArrayList<>(loop.size());
		for (int i = 0; i < loop.size(); i++)
			rotated.add(loop.get((best + i) % loop.size()));
		return rotated;
	}

	private static int compare(double[] a, double[] b) {
		double scale = 1e-9;
		for (int axis = 0; axis < 3; axis++) {
			long left = Math.round(a[axis] / scale);
			long right = Math.round(b[axis] / scale);
			if (left != right)
				return Long.compare(left, right);
		}
		return 0;
	}

	private static int count(String text, String token) {
		int total = 0;
		for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length()))
			total++;
		return total;
	}

	/**
	 * How the mesh's own triangles pair up, by index, before any welding: the count
	 * of undirected edges and how many of them lack a triangle on the other side.
	 * A closed mesh has none, so a nonzero count means the mesh itself is open.
	 */
	private static String edgeReport(CSG csg) {
		long[] triangles = csg.getTriangles();
		Map<Long, Integer> directions = new HashMap<>();
		Set<Long> edges = new LinkedHashSet<>();
		for (int t = 0; t + 2 < triangles.length; t += 3)
			for (int i = 0; i < 3; i++) {
				int from = (int) triangles[t + i];
				int to = (int) triangles[t + (i + 1) % 3];
				directions.merge(((long) from << 32) | (to & 0xffffffffL), 1, Integer::sum);
				int low = Math.min(from, to);
				int high = Math.max(from, to);
				edges.add(((long) low << 32) | (high & 0xffffffffL));
			}

		int unpaired = 0;
		int doubled = 0;
		for (long edge : edges) {
			int from = (int) (edge >> 32);
			int to = (int) (edge & 0xffffffffL);
			Integer forward = directions.get(((long) from << 32) | (to & 0xffffffffL));
			Integer backward = directions.get(((long) to << 32) | (from & 0xffffffffL));
			if (forward == null || backward == null)
				unpaired++;
			else if (forward.intValue() != 1 || backward.intValue() != 1)
				doubled++;
		}
		return csg.getVertCount() + " vertices, " + csg.getTriCount() + " triangles, " + edges.size() + " edges, "
				+ unpaired + " unpaired, " + doubled + " used more than once each way";
	}

	/**
	 * How many {@code ADVANCED_FACE} entities name no bound: their second argument
	 * is an empty list.
	 */
	private static int unboundedFaces(String text) {
		int total = 0;
		String token = "=ADVANCED_FACE(";
		for (int at = text.indexOf(token); at >= 0; at = text.indexOf(token, at + token.length())) {
			int open = text.indexOf("(", at + token.length() - 1);
			int close = matching(text, open);
			List<String> arguments = topLevelArguments(text.substring(open + 1, close));
			// name, bounds, face_geometry, same_sense
			if (arguments.size() < 2 || "()".equals(arguments.get(1)))
				total++;
		}
		return total;
	}

	/** The index of the parenthesis closing the one at {@code open}. */
	private static int matching(String text, int open) {
		int depth = 0;
		for (int i = open; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '\'') {
				// skip a quoted string, whose parentheses are not structural
				for (i++; i < text.length(); i++) {
					if (text.charAt(i) == '\'') {
						if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
							i++;
							continue;
						}
						break;
					}
				}
			} else if (c == '(')
				depth++;
			else if (c == ')' && --depth == 0)
				return i;
		}
		throw new IllegalArgumentException("unbalanced parentheses");
	}

	/** Splits an entity's argument list at its top level commas. */
	private static List<String> topLevelArguments(String arguments) {
		List<String> parts = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < arguments.length(); i++) {
			char c = arguments.charAt(i);
			if (c == '\'') {
				for (i++; i < arguments.length(); i++) {
					if (arguments.charAt(i) == '\'') {
						if (i + 1 < arguments.length() && arguments.charAt(i + 1) == '\'') {
							i++;
							continue;
						}
						break;
					}
				}
			} else if (c == '(')
				depth++;
			else if (c == ')')
				depth--;
			else if (c == ',' && depth == 0) {
				parts.add(arguments.substring(start, i));
				start = i + 1;
			}
		}
		parts.add(arguments.substring(start));
		return parts;
	}
}
