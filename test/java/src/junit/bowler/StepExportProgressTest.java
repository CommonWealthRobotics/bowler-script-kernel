package junit.bowler;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.ICSGProgress;
import eu.mihosoft.vrl.v3d.Sphere;

public class StepExportProgressTest {
	@Test
	public void reportsRealWorkForMultipleObjectsAndBodiesWithoutChangingGeometry() throws Exception {
		CSG first = new Cube(10).toCSG();
		CSG islands = first.union(first.movex(30));
		List<CSG> sources = Arrays.asList(islands, first.movey(30));
		ICSGProgress previous = CSG.getProgressMoniter();
		List<Event> events = new ArrayList<>();
		Path output = Files.createTempFile("step-progress-", ".step");
		try {
			CSG.setProgressMoniter((current, total, message, shape) -> {
			});
			String baseline = new STEPFile(sources).toStep();
			CSG.setProgressMoniter((current, total, message, shape) -> {
				if (message.startsWith("STEP: ")) {
					assertNull(shape);
					assertTrue(current >= 0);
					assertTrue(total > 0);
					assertTrue(current < total);
					events.add(new Event(current, total, message));
				}
			});
			STEPFile export = new STEPFile(sources);
			assertEquals(3, export.getParts().size());
			export.write(output.toFile());
			String actual = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
			assertEquals(data(baseline), data(actual));
			List<CSG> imported = new STEPFile(new ByteArrayInputStream(actual.getBytes(StandardCharsets.UTF_8)))
					.getImported();
			assertEquals(export.getParts().size(), imported.size());
			for (int i = 0; i < imported.size(); i++) {
				assertArrayEquals(export.getParts().get(i).getVertices(), imported.get(i).getVertices(), 1e-9);
				assertArrayEquals(export.getParts().get(i).getTriangles(), imported.get(i).getTriangles());
			}
			for (String stage : Arrays.asList("Preparing", "Simplifying", "Decomposing")) {
				assertStage(events, stage + " object 1/2", 1);
				assertStage(events, stage + " object 2/2", 1);
			}
			assertStage(events, "Extracting feature edges for object 1/2", 2);
			assertStage(events, "Extracting feature edges for object 2/2", 1);
			assertStage(events, "Writing STEP bodies", 3);
			assertEquals(3, events.stream()
					.filter(e -> e.message.startsWith("STEP: Writing BRep for ") && e.current == 1).count());
			assertEquals(3, events.stream()
					.filter(e -> e.message.startsWith("STEP: Writing tessellation for ") && e.current == 1).count());
			assertEquals(15, events.stream()
					.filter(e -> e.message.startsWith("STEP: Writing BRep faces for ") && e.current > 0).count());
			assertStage(events, "Assembling STEP text", 1);
			assertStage(events, "Writing STEP file " + output.getFileName(), 1);
		} finally {
			CSG.setProgressMoniter(previous);
			Files.deleteIfExists(output);
		}
	}

	@Test
	public void singleBodyEdgeSnappingReportsCompletionAndPreservesOutput() {
		CSG source = new Cube(10).toCSG();
		ICSGProgress previous = CSG.getProgressMoniter();
		List<Event> events = new ArrayList<>();
		try {
			CSG.setProgressMoniter((current, total, message, shape) -> {
			});
			String baseline = new STEPFile(source, STEPFile.ExportMode.EDGE_SNAPPING).toStep();
			CSG.setProgressMoniter((current, total, message, shape) -> {
				if (message.startsWith("STEP: ")) {
					assertTrue(current >= 0 && current < total);
					events.add(new Event(current, total, message));
				}
			});
			String actual = new STEPFile(source, STEPFile.ExportMode.EDGE_SNAPPING).toStep();
			assertEquals(data(baseline), data(actual));
			assertStage(events, "Preparing object 1/1", 1);
			assertStage(events, "Writing STEP bodies", 1);
		} finally {
			CSG.setProgressMoniter(previous);
		}
	}

	@Test
	public void faceUpdatesAreBoundedByDisplayResolution() {
		CSG sphere = new Sphere(10, 32, 16).toCSG();
		ICSGProgress previous = CSG.getProgressMoniter();
		List<Event> events = new ArrayList<>();
		try {
			CSG.setProgressMoniter((current, total, message, shape) -> {
				if (message.startsWith("STEP: Writing BRep faces for "))
					events.add(new Event(current, total, message));
			});
			new STEPFile(sphere).toStep();
			assertFalse(events.isEmpty());
			int total = events.get(0).total;
			assertTrue("fixture must exceed display resolution", total > 100);
			assertTrue("initial event plus whole-percent boundaries", events.size() <= 101);
			assertEquals(0, events.get(0).current);
			assertEquals(total - 1, events.get(events.size() - 1).current);
			for (int i = 1; i < events.size(); i++) {
				assertEquals(total, events.get(i).total);
				assertTrue(events.get(i).current > events.get(i - 1).current);
			}
		} finally {
			CSG.setProgressMoniter(previous);
		}
	}

	private static String data(String step) {
		return step.substring(step.indexOf("DATA;"));
	}

	private static void assertStage(List<Event> events, String stage, int total) {
		List<Event> matching = new ArrayList<>();
		for (Event event : events)
			if (event.message.equals("STEP: " + stage))
				matching.add(event);
		assertEquals(stage, total + 1, matching.size());
		for (int i = 0; i <= total; i++) {
			assertEquals(stage, total == 1 ? i : Math.max(0, i - 1), matching.get(i).current);
			assertEquals(stage, Math.max(2, total), matching.get(i).total);
		}
	}

	private static final class Event {
		final int current;
		final int total;
		final String message;

		Event(int current, int total, String message) {
			this.current = current;
			this.total = total;
			this.message = message;
		}
	}
}
