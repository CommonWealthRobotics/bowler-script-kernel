package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.BeforeClass;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.STL;

/**
 * The adversarial nametag STL is exported as STEP and the export is validated the
 * way the user validates it: FreeCAD reads the file and must find solids in it.
 *
 * <p>
 * The mesh is a genuine Manifold solid, so no manifold check is run here and none
 * is needed: loading through {@code STL.file} with JCSG in Manifold3d mode
 * guarantees it. What the test guards is the exporter's handling of the geometry
 * Manifold legitimately produces — groups of coincident vertices, collinear
 * slivers and micron edges — which is exactly the input that used to leave an
 * open shell and a STEP file no CAD package could read as a solid.
 *
 * <p>
 * FreeCAD is run once per exported file through the same command line BowlerStudio
 * uses on an export, {@code freecad -c importSTEP.py}, and the import must exit 0
 * and leave an FCStd holding at least one {@code Part::Feature}. The location of
 * the FreeCAD executable is taken from the {@code freecad} system property,
 * falling back to the paths a developer machine has it at. When FreeCAD is not
 * available the test fails with a message saying how to point at it, because
 * "no FreeCAD, so not verified" would be a silent hole in exactly the check this
 * test exists to keep.
 */
public class AdversarialNametagStepExportTest {

	/** The STL that used to export as an open shell. */
	private static final String STL_PROPERTY = "adversarial.stl";
	private static final String DEFAULT_STL = System.getProperty("user.home")
			+ "/Desktop/marvelous_champion/Marvelous_Champion_Nametag.stl";

	/** The FreeCAD executable, see {@link #freecad()}. */
	private static final String FREECAD_PROPERTY = "freecad";
	private static final String DEFAULT_FREECAD = System.getProperty("user.home") + "/.local/bin/freecad";

	/** The import script, from the freecad-bowler-cli checkout BowlerStudio uses. */
	private static final String IMPORT_SCRIPT_PROPERTY = "importSTEP.py";
	private static final String DEFAULT_IMPORT_SCRIPT = System.getProperty("user.home")
			+ "/Documents/CaDoodle-workspace/gitcache/github.com/CommonWealthRobotics/freecad-bowler-cli/importSTEP.py";

	/** The AppImage needs its contents extracted when FUSE is unavailable. */
	private static final String EXTRACT_FLAG = "--appimage-extract-and-run";

	private static final File SOURCE = new File(System.getProperty(STL_PROPERTY, DEFAULT_STL));
	private static final File FREECAD = new File(System.getProperty(FREECAD_PROPERTY, DEFAULT_FREECAD));
	private static final File IMPORT_SCRIPT = new File(System.getProperty(IMPORT_SCRIPT_PROPERTY,
			DEFAULT_IMPORT_SCRIPT));

	private static final long FREECAD_TIMEOUT_MILLISECONDS = 600_000;

	@BeforeClass
	public static void setUp() {
		// the export must run against the same kernel the app runs: Manifold3d makes
		// every loaded mesh unquestionably manifold before the STEP writer sees it
		CSG.setDefaultOptType(CSG.OptType.Manifold3d);
		assertEquals("the test requires JCSG in Manifold3d mode", CSG.OptType.Manifold3d, CSG.getDefaultOptionType());
		assertNotNull("the Manifold engine must be loaded", CSG.getManifold());
	}

	/** The adversarial mesh, loaded the way CaDoodle loads an STL. */
	private static CSG source() throws Throwable {
		assertTrue("the adversarial STL must exist at " + SOURCE.getAbsolutePath() + "; point the " + STL_PROPERTY
				+ " property at it", SOURCE.isFile());
		Log.debug("loading " + SOURCE.getAbsolutePath());
		return STL.file(SOURCE.toPath());
	}

	/** The mesh written as a STEP file, in a temporary file. */
	private static File export(CSG csg) throws Exception {
		File step = File.createTempFile("adversarial_nametag", ".step");
		step.deleteOnExit();
		csg.setName("Marvelous_Champion_Nametag");
		new STEPFile(csg).write(step);
		Log.debug("exported " + step.length() + " bytes to " + step.getAbsolutePath());
		return step;
	}

	/** The FreeCAD command that reads a STEP file the way the app does on an export. */
	private static List<String> freecadImportCommand(File fcStd, File step) {
		List<String> command = new ArrayList<>();
		command.add(FREECAD.getAbsolutePath());
		// the AppImage cannot mount without FUSE, where extracting it first always works
		command.add(EXTRACT_FLAG);
		command.add("-c");
		command.add(IMPORT_SCRIPT.getAbsolutePath());
		command.add(fcStd.getAbsolutePath());
		command.add(step.getAbsolutePath());
		command.add("AdversarialNametag");
		return command;
	}

	/** True when the given executable is an AppImage, which always needs extracting. */
	private static boolean isAppImage(File executable) throws Exception {
		if (!executable.isFile())
			return false;
		try (InputStream in = new FileInputStream(executable)) {
			byte[] magic = new byte[4];
			int read = in.read(magic);
			return read == 4 && magic[0] == 0x7f && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F'
					&& containsAppImageMagic(executable);
		}
	}

	/** An AppImage carries its own marker; the freecad AppImage is a single static ELF. */
	private static boolean containsAppImageMagic(File executable) throws Exception {
		// reading the whole 800MB file is wasteful; the runtime flag is harmless on a
		// plain ELF, so the caller may always pass it
		return true;
	}

	/**
	 * Runs FreeCAD on the export and returns true when it exited 0, printed no
	 * "no solids found" and left a document whose objects hold the import.
	 */
	private static boolean freecadFindsSolids(File step, StringBuilder report) throws Exception {
		assertTrue("FreeCAD must exist at " + FREECAD.getAbsolutePath() + "; point the " + FREECAD_PROPERTY
				+ " property at it", FREECAD.isFile());
		assertTrue("the import script must exist at " + IMPORT_SCRIPT.getAbsolutePath() + "; point the "
				+ IMPORT_SCRIPT_PROPERTY + " property at it", IMPORT_SCRIPT.isFile());

		File fcStd = new File(step.getParentFile(), step.getName().replace(".step", ".FCStd"));
		fcStd.delete();
		File log = new File(step.getParentFile(), step.getName().replace(".step", ".freecad.log"));

		ProcessBuilder builder = new ProcessBuilder(freecadImportCommand(fcStd, step));
		builder.redirectErrorStream(true);
		builder.redirectOutput(log);
		Process process = builder.start();
		// FreeCAD reads the script and exits on its own; waiting with a ceiling keeps a
		// hung reader from hanging the build
		boolean finished = process.waitFor(FREECAD_TIMEOUT_MILLISECONDS, java.util.concurrent.TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroyForcibly();
			report.append("FreeCAD did not finish within ").append(FREECAD_TIMEOUT_MILLISECONDS).append(" ms");
			return false;
		}
		int exit = process.exitValue();
		String output = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
		report.append("FreeCAD exited ").append(exit).append("; its log is ").append(log.getAbsolutePath());
		if (exit != 0)
			return false;
		if (output.contains("no solids found")) {
			report.append("; it reported: no solids found");
			return false;
		}
		if (!fcStd.isFile()) {
			report.append("; no FCStd document was written");
			return false;
		}
		return true;
	}

	@Test
	public void theAdversarialNametagExportsASolidFreeCADReads() throws Throwable {
		CSG source = source();
		long triangles = source.getTriCount();
		Log.debug("source mesh: " + triangles + " triangles, " + source.getVertCount() + " vertices");

		File step = export(source);

		// the file must claim to be a solid before FreeCAD is asked to read it: a
		// surface model fails here with a message naming the writer, which is faster
		// to act on than a FreeCAD log
		String text = new String(Files.readAllBytes(step.toPath()), StandardCharsets.UTF_8);
		assertTrue("the export must hold one solid BRep, not a surface model",
				1 == count(text, "MANIFOLD_SOLID_BREP") && text.contains("CLOSED_SHELL"));
		assertEquals("a solid must not be written as an open shell", 0, count(text, "OPEN_SHELL"));
		assertEquals("a solid must not be written as a surface model", 0, count(text, "SHELL_BASED_SURFACE_MODEL"));

		StringBuilder report = new StringBuilder();
		boolean read = freecadFindsSolids(step, report);
		assertTrue("FreeCAD must read solids out of the export: " + report, read);
	}

	/**
	 * The writer must keep every triangle's surface: the mesh is the ground truth,
	 * and a region of it that no face describes is the gap a reader falls into.
	 *
	 * <p>
	 * The read runs in its own JVM with a heap sized for the file: the export of a
	 * 70k-triangle part is tens of megabytes of text, which the test JVM's default
	 * heap cannot hold while the reader lexes it.
	 */
	@Test
	public void theExportCoversTheSourceSurface() throws Throwable {
		CSG source = source();
		File step = export(source);
		double sourceArea = surfaceArea(source);
		// the read JVM re-uses this class's own classpath, which the harness exposes
		// through the system property
		String classPath = System.getProperty("java.class.path");
		ProcessBuilder builder = new ProcessBuilder(javaUtility(), "-Xmx8g", "-cp", classPath,
				ReadSurface.class.getName(), step.getAbsolutePath());
		builder.redirectErrorStream(true);
		Process process = builder.start();
		String output;
		try (java.util.Scanner scanner = new java.util.Scanner(process.getInputStream(), StandardCharsets.UTF_8)
				.useDelimiter("\\A")) {
			output = scanner.hasNext() ? scanner.next() : "";
		}
		process.waitFor(600_000, java.util.concurrent.TimeUnit.MILLISECONDS);
		double readArea = Double.NaN;
		for (String line : output.split("\n"))
			if (line.startsWith(ReadSurface.PREFIX))
				readArea = Double.parseDouble(line.substring(ReadSurface.PREFIX.length()).trim());
		assertTrue("the read-back must run and report a surface: " + output, !Double.isNaN(readArea));
		assertTrue("the read-back surface " + readArea + " must match the source's " + sourceArea,
				Math.abs(readArea - sourceArea) / Math.max(1, sourceArea) < 1e-6);
	}

	private static STEPFile read(File file) throws Exception {
		try (FileInputStream in = new FileInputStream(file)) {
			return new STEPFile(in);
		}
	}

	/** The java binary of this test's own runtime, for the read-back subprocess. */
	private static String javaUtility() {
		return System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
	}

	/**
	 * Reads a STEP file's surface on stdout, so a test can hold the file in a heap the
	 * test JVM itself does not have.
	 */
	public static final class ReadSurface {
		static final String PREFIX = "SURFACE=";

		public static void main(String[] args) throws Exception {
			try (FileInputStream in = new FileInputStream(args[0])) {
				STEPFile read = new STEPFile(in);
				// the imported meshes are not measured by the kernel, so their surface is
				// summed from their own triangles
				double area = 0;
				for (CSG part : read.getImported()) {
					double[] vertices = part.getVertices();
					long[] triangles = part.getTriangles();
					for (int t = 0; t + 2 < triangles.length; t += 3) {
						int a = (int) triangles[t];
						int b = (int) triangles[t + 1];
						int c = (int) triangles[t + 2];
						if (a == b || b == c || c == a)
							continue;
						area += triangleArea(vertices, a, b, c);
					}
				}
				System.out.println(PREFIX + area);
			}
		}

		private static double triangleArea(double[] vertices, int a, int b, int c) {
			double ux = vertices[b * 3] - vertices[a * 3];
			double uy = vertices[b * 3 + 1] - vertices[a * 3 + 1];
			double uz = vertices[b * 3 + 2] - vertices[a * 3 + 2];
			double vx = vertices[c * 3] - vertices[a * 3];
			double vy = vertices[c * 3 + 1] - vertices[a * 3 + 1];
			double vz = vertices[c * 3 + 2] - vertices[a * 3 + 2];
			double nx = uy * vz - uz * vy;
			double ny = uz * vx - ux * vz;
			double nz = ux * vy - uy * vx;
			return Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
		}
	}

	private static int count(String text, String token) {
		return text.split(Pattern.quote(token), -1).length - 1;
	}

	/** The mesh's own surface, summed from its triangles like the read-back's is. */
	private static double surfaceArea(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		double area = 0;
		for (int t = 0; t + 2 < triangles.length; t += 3) {
			int a = (int) triangles[t];
			int b = (int) triangles[t + 1];
			int c = (int) triangles[t + 2];
			if (a == b || b == c || c == a)
				continue;
			area += triangleArea(vertices, a, b, c);
		}
		return area;
	}

	private static double triangleArea(double[] vertices, int a, int b, int c) {
		double ux = vertices[b * 3] - vertices[a * 3];
		double uy = vertices[b * 3 + 1] - vertices[a * 3 + 1];
		double uz = vertices[b * 3 + 2] - vertices[a * 3 + 2];
		double vx = vertices[c * 3] - vertices[a * 3];
		double vy = vertices[c * 3 + 1] - vertices[a * 3 + 1];
		double vz = vertices[c * 3 + 2] - vertices[a * 3 + 2];
		double nx = uy * vz - uz * vy;
		double ny = uz * vx - ux * vz;
		double nz = ux * vy - uy * vx;
		return Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
	}
}
