package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.FreecadLoader;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * The whole loop, over the adversarial nametag: the reference CSG comes from the
 * 3MF in the corpus, is exported as a STEP file, FreeCAD imports the STEP, FreeCAD
 * exports a 3MF, and that 3MF is loaded back into a CSG without throwing — the same
 * path an export takes through the app.
 *
 * <p>
 * The FreeCAD steps run through {@link FreecadLoader}'s own execution methods,
 * which resolve the CLI through
 * {@link DownloadManager#getConfigExecutable(String, Object)} and drive the
 * freecad-bowler-cli scripts exactly as the app does.
 *
 * <p>
 * The stage under test is the STEP export, so the FreeCAD gate
 * ({@code check_step.py}) is run over the exported file and its JSON report is
 * loaded here: the verdict is taken from what FreeCAD itself measured, with the
 * report attached to the failure. The loop closes on the bounds — the CSG loaded
 * from FreeCAD's 3MF must match the reference CSG's
 * {@link CSG#getBounds()} within a hundredth of a micron — and on the load
 * throwing, which Manifold does on a mesh that is not manifold.
 */
public class AdversarialNametagStepExportTest {

	/** The reference model, from the 3MF in the repo's STEP corpus. */
	private static final File SOURCE = new File("testStepFiles/Marvelous_Champion_Nametag.3mf");

	/** How far the round-tripped bounds may sit from the reference's, in millimetres. */
	private static final double BOUNDS_TOLERANCE = 0.00001;

	// for this model a healthy export's gate finishes in seconds; a gate that
	// outlives half a minute is chewing on geometry FreeCAD cannot resolve, so
	// it is a failure of the export and must fail the test, not wait it out
	private static final long GATE_TIMEOUT_MILLISECONDS = 30_000;

	/** The reference CSG, loaded once. */
	private static CSG reference;

	@BeforeClass
	public static void setUp() throws Throwable {
		// the working directory is the app's own: CaDoodle-workspace under Documents,
		// so the FreeCAD scripts resolve through the same git cache the app uses and
		// the run leaves its files where a run of the app would leave them
		String relative = ScriptingEngine.getWorkingDirectory().getAbsolutePath();
		File workspace = new File(relative + DownloadManager.delim() + "CaDoodle-workspace" + DownloadManager.delim());
		workspace.mkdirs();
		ScriptingEngine.setWorkspace(workspace);

		// everything the loop prints goes to a log file in the workspace, so a failure
		// carries its whole history rather than whatever the console kept
		File log = new File(workspace, "adversarial-nametag-loop.log");
		if (log.exists())
			log.delete();
		log.createNewFile();
		Log.setFile(log);
		Log.enableDebugPrint();

		// the loop must run against the same kernel the app runs: Manifold3d makes
		// every loaded mesh unquestionably manifold before the STEP writer sees it
		CSG.setDefaultOptType(CSG.OptType.Manifold3d);
		assertEquals("the test requires JCSG in Manifold3d mode", CSG.OptType.Manifold3d, CSG.getDefaultOptionType());
		assertNotNull("the Manifold engine must be loaded", CSG.getManifold());

		// the reference CSG: what the 3MF in the corpus holds
		assertTrue("the reference 3MF must exist at " + SOURCE.getAbsolutePath(), SOURCE.isFile());
		reference = CSG.fromThreeMF(SOURCE.toPath()).get(0);
		reference.setNoScale(true);
		Log.debug("reference mesh: " + reference.getTriCount() + " triangles, bounds " + reference.getBounds());
	}

	@Test
	public void theWholeLoopFromCSGToStepToFreeCADTo3mfToCSG() throws Throwable {
		// the reference bounds, from CSG's own measure
		eu.mihosoft.vrl.v3d.Bounds expected = reference.getBounds();

		File work = Files.createTempDirectory("adversarial_nametag").toFile();
		work.deleteOnExit();

		// CSG -> STEP: the export under test
		File step = export(reference, work);
		String text = new String(Files.readAllBytes(step.toPath()), StandardCharsets.UTF_8);
		int count = count(text, "MANIFOLD_SOLID_BREP");
		boolean contains = text.contains("CLOSED_SHELL");
		Log.debug("exported entities: MANIFOLD_SOLID_BREP " + count + ", CLOSED_SHELL " + contains + ", OPEN_SHELL "
				+ count(text, "OPEN_SHELL") + ", SHELL_BASED_SURFACE_MODEL " + count(text, "SHELL_BASED_SURFACE_MODEL")
				+ ", ADVANCED_FACE " + count(text, "ADVANCED_FACE"));
		assertTrue("the export must hold one solid BRep, not a surface model", 1 == count && contains);
		assertEquals("a solid must not be written as an open shell", 0, count(text, "OPEN_SHELL"));
		assertEquals("a solid must not be written as a surface model", 0, count(text, "SHELL_BASED_SURFACE_MODEL"));

		// STEP -> FreeCAD: the import the app runs on an exported file
		File fcStd = new File(work, "Marvelous_Champion_Nametag.FCStd");
		FreecadLoader.addSTEPToFreecad(fcStd, step, "AdversarialNametag");
		assertTrue("the FreeCAD import wrote no document", fcStd.isFile() && fcStd.length() > 0);

		// FreeCAD -> 3MF: the export the app runs on a FreeCAD model
		File threeMF = new File(work, "Marvelous_Champion_Nametag_exported.3mf");
		FreecadLoader.to3MFFile(fcStd, threeMF);
		assertTrue("the FreeCAD 3MF export wrote no file", threeMF.isFile() && threeMF.length() > 0);

		// the FreeCAD gate over the exported STEP, its report read here: the verdict
		// comes from what FreeCAD measured, not from this JVM's own opinion
		File report = new File(work, "check_step_report.json");
		runGate(step, report);
		String reportText = new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8);
		Log.debug("the FreeCAD gate report:\n" + reportText);
		assertTrue("the FreeCAD gate wrote an empty report at " + report.getAbsolutePath(), reportText.length() > 0);
		assertTrue("the FreeCAD gate reported problems in the STEP export; its report is " + report.getAbsolutePath()
				+ ":\n" + reportText, reportText.contains("\"pass\": true"));

		// 3MF -> CSG: loading through Manifold throws on a mesh that is not manifold,
		// so the load itself is the manifold check the loop ends with
		CSG roundTripped = CSG.fromThreeMF(threeMF.toPath()).get(0);
		roundTripped.setNoScale(true);
		assertTrue("the 3MF round trip lost every triangle", roundTripped.getTriCount() > 0);

		// the bounds of the loaded CSG must match the reference's, from CSG's own
		// bounds, to a hundredth of a micron
		eu.mihosoft.vrl.v3d.Bounds actual = roundTripped.getBounds();
		double[][] checks = {{expected.getMin().x, actual.getMin().x}, {expected.getMin().y, actual.getMin().y},
				{expected.getMin().z, expected.getMin().z}, {expected.getMax().x, actual.getMax().x},
				{expected.getMax().y, actual.getMax().y}, {expected.getMax().z, actual.getMax().z}};
		String[] names = {"minimum x", "minimum y", "minimum z", "maximum x", "maximum y", "maximum z"};
		for (int at = 0; at < checks.length; at++) {
			double wanted = checks[at][0];
			double got = checks[at][at == 2 ? 0 : 1];
			assertTrue("the round trip's " + names[at] + " " + got + " must match the reference's " + wanted
					+ " within " + BOUNDS_TOLERANCE, Math.abs(got - wanted) <= BOUNDS_TOLERANCE);
		}
	}

	/** The occurrences of a token in the file text, for the entity counts. */
	private static int count(String text, String token) {
		return text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
	}

	/** The mesh written as a STEP file, the export the app would produce. */
	private static File export(CSG csg, File directory) throws Exception {
		csg.setName("Marvelous_Champion_Nametag");
		File step = new File(directory, csg.getName() + ".step");
		new STEPFile(csg).write(step);
		Log.debug("exported " + step.length() + " bytes to " + step.getAbsolutePath());
		return step;
	}

	/**
	 * Runs the FreeCAD gate ({@code check_step.py}) over the exported STEP file and
	 * leaves its JSON report at {@code report}. The gate runs FreeCAD the same way
	 * the app's own FreeCAD steps do.
	 */
	private static void runGate(File step, File report) throws Exception {
		File gateScript = new File(ScriptingEngine.getWorkspace().getAbsolutePath()
				+ "/gitcache/github.com/CommonWealthRobotics/freecad-bowler-cli/check_step.py");
		assertTrue("the FreeCAD gate script must exist at " + gateScript.getAbsolutePath(), gateScript.isFile());

		List<String> command = new java.util.ArrayList<>();
		command.add(DownloadManager.getConfigExecutable("freecad", null).getAbsolutePath());
		command.add("--appimage-extract-and-run");
		command.add("-c");
		command.add(gateScript.getAbsolutePath());
		ProcessBuilder builder = new ProcessBuilder(command);
		// the gate is a headless scripted FreeCAD run, so it needs the environment the
		// app's own FreeCAD steps run under: without it Qt aborts the process before
		// the script's Python even starts, and the report is never written
		builder.environment().putAll(java.util.Map.of("QT_QPA_PLATFORM", "offscreen"));
		builder.environment().put("STEP_FILES", step.getAbsolutePath());
		builder.environment().put("ROUND_TRIP_JSON", report.getAbsolutePath());
		// a scripted FreeCAD run waits for an EOF on stdin after its script completes,
		// so it must never read the caller's console, or the run hangs forever
		builder.redirectInput(ProcessBuilder.Redirect.from(new File(java.io.File.separator + "dev"
				+ java.io.File.separator + "null")));
		builder.redirectErrorStream(true);
		File log = new File(step.getParentFile(), "check_step.log");
		builder.redirectOutput(log);
		Process process = builder.start();
		boolean finished = process.waitFor(GATE_TIMEOUT_MILLISECONDS, java.util.concurrent.TimeUnit.MILLISECONDS);
		if (!finished) {
			process.destroyForcibly();
			throw new AssertionError("the FreeCAD gate did not finish within " + GATE_TIMEOUT_MILLISECONDS
					+ " ms; its log is " + log.getAbsolutePath());
		}
		Log.debug("the FreeCAD gate printed:\n" + new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8));
		assertTrue("the FreeCAD gate wrote no report at " + report.getAbsolutePath(), report.isFile());
	}
}
