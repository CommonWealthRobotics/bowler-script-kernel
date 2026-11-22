package junit.bowler;

import java.util.ArrayList;

import java.util.Collections;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.junit.Assume;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.manifold3d.CSGManifold3d.BooleanOp;

import eu.mihosoft.vrl.v3d.CSG;

/** Local FreeCAD integration; no application workspace, downloads, or GUI. */
public class FreecadStepRoundTripIntegrationTest {
	@Test
	public void nametagSurvivesStepAndFreecad() throws Throwable {
		roundTrip("freecad-step-roundtrip", STEPFile.ExportMode.PRIMARY,
				() -> CSG.fromThreeMF(new File("testStepFiles/Marvelous_Champion_Nametag.3mf").toPath()));
	}

	@Test
	public void snappedTinyEdgeSurvivesFreecad() throws Throwable {
		roundTrip("freecad-snapping-roundtrip", STEPFile.ExportMode.EDGE_SNAPPING, () -> Arrays.asList(cubeWithTinyEdge()));
	}

	@Test
	public void nametagSnappingSurvivesStepAndFreecad() throws Throwable {
		roundTrip("freecad-nametag-snapping-roundtrip", STEPFile.ExportMode.EDGE_SNAPPING,
				() -> CSG.fromThreeMF(new File("testStepFiles/Marvelous_Champion_Nametag.3mf").toPath()));
	}

	@Test
	public void placedAsymmetricPartSurvivesFreecad() throws Throwable {
		roundTrip("freecad-placement-roundtrip", STEPFile.ExportMode.PRIMARY, () -> {
			CSG box = CSG.getManifold().cube(7, 11, 13, true);
			box = CSG.getManifold().rotateZ(box, 37);
			return Collections.singletonList(CSG.getManifold().translate(box, 12.25, -31.5, 4.125));
		});
	}

	@Test
	public void serializerPreservesPrecisionPlacementsAndTopology() throws Exception {
		Path work = Files.createDirectories(new File("build/test-output/freecad-serializer").toPath());
		String python = python(work);
		Path fixture = copyResource("/scripts/freecad_3mf_behavior.py", work.resolve("freecad_3mf_behavior.py"));
		Path exporter = copyResource("/com/neuronrobotics/bowlerstudio/scripting/freecad_3mf.py",
				work.resolve("freecad_3mf.py"));
		Path output = work.resolve("tests.log");
		int exit = run(python, output, fixture.toAbsolutePath().toString(), exporter.toAbsolutePath().toString());
		assertEquals(log(output), 0, exit);
	}

	@FunctionalInterface
	private interface Source {
		List<CSG> load() throws Throwable;
	}

	private void roundTrip(String directory, STEPFile.ExportMode mode, Source fixture) throws Throwable {
		Path work = Files.createDirectories(new File("build/test-output/" + directory).toPath());
		String python = python(work);

		CSG.OptType previous = CSG.getDefaultOptionType();
		try {
			CSG.setDefaultOptType(CSG.OptType.Manifold3d);
			assertNotNull("native Manifold must load", CSG.getManifold());
			List<CSG> source = fixture.load();
			double expectedVolume = StepRoundTripAssertions.volume(source);
			Path step = work.resolve("model.step");
			new STEPFile(source, mode).write(step.toFile());
			Path script = copyResource("/scripts/freecad_step_roundtrip.py", work.resolve("freecad_step_roundtrip.py"));
			Path exporter = copyResource("/com/neuronrobotics/bowlerstudio/scripting/freecad_3mf.py",
					work.resolve("freecad_3mf.py"));
			Path mesh = work.resolve("model.3mf");
			Path report = work.resolve("report.json");
			Files.deleteIfExists(mesh);
			Files.deleteIfExists(report);
			Path processLog = work.resolve("freecad.log");
			int exit = run(python, processLog, script.toAbsolutePath().toString(), step.toAbsolutePath().toString(),
					mesh.toAbsolutePath().toString(), report.toAbsolutePath().toString(), "--expected-volume",
					Double.toString(expectedVolume), "--exporter", exporter.toAbsolutePath().toString());
			assertEquals(log(processLog), 0, exit);
			assertTrue("FreeCAD wrote no report; " + log(processLog), Files.size(report) > 0);
			assertTrue("FreeCAD wrote no mesh; " + log(processLog), Files.size(mesh) > 0);
			List<CSG> actual = CSG.fromThreeMF(mesh);
			StepRoundTripAssertions.volume(expectedVolume, StepRoundTripAssertions.volume(actual));
			CSG expectedSolid = combine(source);
			CSG actualSolid = combine(actual);
			StepRoundTripAssertions.bounds(expectedSolid.getBounds(), actualSolid.getBounds());
			double missing = CSG.getManifold().booleanOp(expectedSolid, actualSolid, BooleanOp.DIFFERENCE).getVolume();
			double extra = CSG.getManifold().booleanOp(actualSolid, expectedSolid, BooleanOp.DIFFERENCE).getVolume();
			// Surface area times coordinate tolerance bounds a thin displaced boundary
			double differenceTolerance = CSG.getManifold().surfaceArea(expectedSolid) * 1e-5;
			Files.write(work.resolve("comparison.txt"),
					Arrays.asList("source volume=" + expectedVolume,
							"round-trip volume=" + StepRoundTripAssertions.volume(actual), "missing volume=" + missing,
							"extra volume=" + extra, "symmetric difference tolerance=" + differenceTolerance),
					StandardCharsets.UTF_8);
			assertTrue("missing=" + missing + ", extra=" + extra + ", tolerance=" + differenceTolerance,
					Double.isFinite(missing) && Double.isFinite(extra) && missing >= 0 && extra >= 0
							&& missing + extra <= differenceTolerance);
		} finally {
			CSG.setDefaultOptType(previous);
		}
	}

	private static String python(Path work) throws Exception {
		String python = System.getProperty("freecad.python", "/usr/bin/python3");
		Assume.assumeTrue(Files.isExecutable(new File(python).toPath()));
		Path probeLog = work.resolve("availability.log");
		int probe = run(python, probeLog, "-c",
				"import sys\ntry:\n import FreeCAD, Part, Mesh\n" + "except ModuleNotFoundError as error:\n"
						+ " if error.name not in ('FreeCAD', 'Part', 'Mesh'): raise\n"
						+ " print(error); sys.exit(77)\n");
		Assume.assumeTrue(probe != 77);
		assertEquals(log(probeLog), 0, probe);
		return python;
	}

	private Path copyResource(String name, Path destination) throws Exception {
		try (InputStream resource = getClass().getResourceAsStream(name)) {
			assertNotNull("bundled resource " + name, resource);
			Files.copy(resource, destination, StandardCopyOption.REPLACE_EXISTING);
		}
		return destination;
	}

	private static CSG cubeWithTinyEdge() throws Throwable {
		CSG cube = CSG.getManifold().cube(10, 10, 10, true);
		double[] points = Arrays.copyOf(cube.getVertices(), cube.getVertices().length + 3);
		long[] triangles = cube.getTriangles();
		int a = (int) triangles[0], b = (int) triangles[1], inserted = cube.getVertices().length / 3;
		for (int axis = 0; axis < 3; axis++)
			points[3 * inserted + axis] = points[3 * a + axis] + 5e-7 * (points[3 * b + axis] - points[3 * a + axis]);
		ArrayList<Long> split = new ArrayList<>();
		for (int t = 0; t < triangles.length; t += 3) {
			boolean divided = false;
			for (int k = 0; k < 3; k++) {
				long u = triangles[t + k], v = triangles[t + (k + 1) % 3], w = triangles[t + (k + 2) % 3];
				if ((u == a && v == b) || (u == b && v == a)) {
					split.addAll(Arrays.asList(u, (long) inserted, w, (long) inserted, v, w));
					divided = true;
					break;
				}
			}
			if (!divided)
				for (int k = 0; k < 3; k++)
					split.add(triangles[t + k]);
		}
		CSG source = new CSG(points, split.stream().mapToLong(Long::longValue).toArray(), cube.getColor());
		assertEquals(14L, source.getTriCount());
		assertEquals(1000, CSG.getManifold().volume(source), 1e-9);
		assertEquals(12L, new STEPFile(source, STEPFile.ExportMode.EDGE_SNAPPING).getCsg().getTriCount());
		return source;
	}

	private static CSG combine(List<CSG> parts) throws Throwable {
		CSG combined = parts.get(0);
		for (int index = 1; index < parts.size(); index++) {
			combined = CSG.getManifold().booleanOp(combined, parts.get(index), BooleanOp.UNION);
		}
		return combined;
	}

	private static int run(String python, Path log, String... arguments) throws Exception {
		ArrayList<String> command = new ArrayList<>();
		command.add(python);
		Collections.addAll(command, arguments);
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.environment().put("PYTHONPATH", System.getProperty("freecad.pythonpath", "/usr/lib/freecad/lib"));
		builder.redirectErrorStream(true).redirectOutput(log.toFile());
		return builder.start().waitFor();
	}

	private static String log(Path path) throws Exception {
		return path.toAbsolutePath() + ":\n" + new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}
}
