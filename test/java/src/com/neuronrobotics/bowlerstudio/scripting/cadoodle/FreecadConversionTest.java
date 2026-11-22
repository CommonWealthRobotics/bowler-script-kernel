package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.Collections;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.FreecadLoader;
import com.neuronrobotics.bowlerstudio.threed.STEPFile.ExportMode;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.ICSGProgress;

public class FreecadConversionTest {
	private enum Load {
		VALID, UNREADABLE, EMPTY, OPEN_SECOND_SOLID, WRONG_VOLUME, FATAL
	}

	private static final class Backend implements FreecadConversion.Backend {
		final Map<ExportMode, Load> loads = new EnumMap<>(ExportMode.class);
		final List<ExportMode> attempts = new ArrayList<>();
		final List<File> documents = new ArrayList<>();
		final List<List<CSG>> sources = new ArrayList<>();
		boolean failExport;
		boolean failPrimaryGeometryExport;
		boolean failFallbackWrite;
		ExportMode active = ExportMode.PRIMARY;

		@Override
		public void writeStep(List<CSG> source, File step, ExportMode mode) throws IOException {
			active = mode;
			attempts.add(mode);
			sources.add(source);
			if (failFallbackWrite && mode == ExportMode.EDGE_SNAPPING)
				throw new IOException("fallback write failed");
			Files.write(step.toPath(), mode.name().getBytes(StandardCharsets.UTF_8));
		}

		@Override
		public void importStep(File step, File document, String name) throws IOException {
			assertFalse("each attempt starts with a fresh document", document.exists());
			documents.add(document);
			Files.copy(step.toPath(), document.toPath());
		}

		@Override
		public void exportThreeMF(File document, File mesh) throws IOException {
			if (failPrimaryGeometryExport && active == ExportMode.PRIMARY)
				throw new FreecadLoader.MeshExportFailure("invalid FreeCAD solid");
			if (failExport)
				throw new IOException("external process failed");
		}

		@Override
		public List<CSG> readThreeMF(File mesh) throws IOException {
			switch (loads.getOrDefault(active, Load.VALID)) {
				case UNREADABLE :
					throw new IOException("invalid 3MF");
				case EMPTY :
					return Collections.emptyList();
				case WRONG_VOLUME :
					return Arrays.asList(new Cube(5).toCSG());
				case FATAL :
					throw new AssertionError("fatal loader error");
				case OPEN_SECOND_SOLID :
					CSG open = new Cube(10).toCSG();
					open.setTriangles(Arrays.copyOf(open.getTriangles(), open.getTriangles().length - 3));
					return Arrays.asList(new Cube(10).toCSG(), open);
				default :
					return Arrays.asList(new Cube(10).toCSG());
			}
		}
	}

	private static File target() throws IOException {
		File root = new File("build/test-output/freecad-fallback");
		Files.createDirectories(root.toPath());
		return Files.createTempDirectory(root.toPath(), "case-").resolve("converted.FCStd").toFile();
	}

	private static List<CSG> source() {
		return Arrays.asList(new Cube(10).toCSG());
	}

	@Test
	public void reportsStagesAndCompletionAfterPublication() throws IOException {
		ICSGProgress previous = CSG.getProgressMoniter();
		List<String> messages = new ArrayList<>();
		File target = target();
		List<Boolean> published = new ArrayList<>();
		CSG.setProgressMoniter((index, total, message, shape) -> {
			assertTrue(index >= 0 && index < total);
			messages.add(message);
			published.add(target.isFile());
		});
		try {
			new FreecadConversion(new Backend()).write(target, source(), "test");
			assertTrue(messages.size() >= 8);
			assertFalse(published.get(0));
			assertTrue(published.get(published.size() - 1));
			assertTrue(messages.stream().distinct().count() >= 8);
		} finally {
			CSG.setProgressMoniter(previous);
		}
	}

	@Test
	public void failedExportStopsProgressBeforePublication() throws IOException {
		ICSGProgress previous = CSG.getProgressMoniter();
		Backend backend = new Backend();
		backend.failExport = true;
		File target = target();
		List<Integer> stages = new ArrayList<>();
		CSG.setProgressMoniter((index, total, message, shape) -> {
			if (total == 4)
				stages.add(index);
		});
		try {
			try {
				new FreecadConversion(backend).write(target, source(), "test");
				fail("export should fail");
			} catch (IOException expected) {
				assertEquals(Arrays.asList(0, 1, 2), stages);
				assertFalse(target.exists());
			}
		} finally {
			CSG.setProgressMoniter(previous);
		}
	}

	@Test
	public void validPrimaryIsPublishedWithoutSnapping() throws IOException {
		Backend backend = new Backend();
		File target = target();
		assertEquals(ExportMode.PRIMARY, new FreecadConversion(backend).write(target, source(), "test"));
		assertEquals(Arrays.asList(ExportMode.PRIMARY), backend.attempts);
		assertEquals("PRIMARY", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void unreadablePrimaryUsesOneValidatedSnappingAttemptFromOriginalSource() throws IOException {
		Backend backend = new Backend();
		backend.loads.put(ExportMode.PRIMARY, Load.UNREADABLE);
		File target = target();
		List<CSG> source = source();
		assertEquals(ExportMode.EDGE_SNAPPING, new FreecadConversion(backend).write(target, source, "test"));
		assertEquals(Arrays.asList(ExportMode.PRIMARY, ExportMode.EDGE_SNAPPING), backend.attempts);
		assertSame(source, backend.sources.get(0));
		assertSame(source, backend.sources.get(1));
		assertFalse(backend.documents.get(0).equals(backend.documents.get(1)));
		assertEquals("EDGE_SNAPPING", new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8));
	}

	@Test
	public void emptyNonManifoldAndWrongVolumeMeshesAreNotAccepted() throws IOException {
		for (Load failure : Arrays.asList(Load.EMPTY, Load.OPEN_SECOND_SOLID, Load.WRONG_VOLUME)) {
			Backend backend = new Backend();
			backend.loads.put(ExportMode.PRIMARY, failure);
			assertEquals(ExportMode.EDGE_SNAPPING, new FreecadConversion(backend).write(target(), source(), "test"));
		}
	}

	@Test
	public void failureOfBothLoadAttemptsLeavesNoTargetAndPreservesBothErrors() throws IOException {
		Backend backend = new Backend();
		backend.loads.put(ExportMode.PRIMARY, Load.UNREADABLE);
		backend.loads.put(ExportMode.EDGE_SNAPPING, Load.UNREADABLE);
		File target = target();
		try {
			new FreecadConversion(backend).write(target, source(), "test");
			fail("both attempts must fail");
		} catch (IOException failure) {
			assertEquals(1, failure.getSuppressed().length);
			assertNotNull(failure.getCause());
		}
		assertFalse(target.exists());
		assertEquals(2, backend.attempts.size());
	}

	@Test
	public void invalidSnappedGeometryIsNotPublished() throws IOException {
		Backend backend = new Backend();
		backend.loads.put(ExportMode.PRIMARY, Load.UNREADABLE);
		backend.loads.put(ExportMode.EDGE_SNAPPING, Load.OPEN_SECOND_SOLID);
		File target = target();
		try {
			new FreecadConversion(backend).write(target, source(), "test");
			fail("invalid fallback must fail");
		} catch (IOException expected) {
			assertEquals(1, expected.getSuppressed().length);
		}
		assertFalse(target.exists());
	}

	@Test
	public void fallbackWriteFailurePreservesThePrimaryLoadFailure() throws IOException {
		Backend backend = new Backend();
		backend.loads.put(ExportMode.PRIMARY, Load.UNREADABLE);
		backend.failFallbackWrite = true;
		File target = target();
		try {
			new FreecadConversion(backend).write(target, source(), "test");
			fail("fallback write must fail");
		} catch (IOException failure) {
			assertEquals(1, failure.getSuppressed().length);
			assertEquals("fallback write failed", failure.getCause().getMessage());
		}
		assertFalse(target.exists());
	}

	@Test
	public void processErrorsDoNotTriggerGeometryFallback() throws IOException {
		Backend backend = new Backend();
		backend.failExport = true;
		File target = target();
		try {
			new FreecadConversion(backend).write(target, source(), "test");
			fail("export must fail");
		} catch (IOException expected) {
			assertEquals(Arrays.asList(ExportMode.PRIMARY), backend.attempts);
		}
		assertFalse(target.exists());
	}

	@Test
	public void invalidFreecadGeometryTriggersTheSameValidatedFallback() throws IOException {
		Backend backend = new Backend();
		backend.failPrimaryGeometryExport = true;
		File target = target();
		assertEquals(ExportMode.EDGE_SNAPPING, new FreecadConversion(backend).write(target, source(), "test"));
		assertEquals(Arrays.asList(ExportMode.PRIMARY, ExportMode.EDGE_SNAPPING), backend.attempts);
		assertTrue(target.isFile());
	}

	@Test
	public void fatalErrorsAreNotTurnedIntoGeometryFallback() throws IOException {
		Backend backend = new Backend();
		backend.loads.put(ExportMode.PRIMARY, Load.FATAL);
		try {
			new FreecadConversion(backend).write(target(), source(), "test");
			fail("fatal error must propagate");
		} catch (AssertionError expected) {
			assertEquals("fatal loader error", expected.getMessage());
			assertEquals(Arrays.asList(ExportMode.PRIMARY), backend.attempts);
		}
	}
}
