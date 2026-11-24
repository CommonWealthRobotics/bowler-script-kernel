package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.IScriptingLanguage;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ConvertToBlender;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ConvertToFileOperation;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ConvertToFreeCAD;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ConvertToOpenSCAD;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

public class ConversionOutputNameTest {
	@Test
	public void freeCADOutputsHaveReadableNames() throws Exception {
		assertOutputNames(new ConvertToFreeCAD(), "FreeCAD model");
	}

	@Test
	public void openSCADOutputsHaveReadableNames() throws Exception {
		assertOutputNames(new ConvertToOpenSCAD(), "OpenSCAD model");
	}

	@Test
	public void blenderOutputsHaveReadableNames() throws Exception {
		assertOutputNames(new ConvertToBlender(), "Blender model");
	}

	private void assertOutputNames(ConvertToFileOperation operation, String label) throws Exception {
		File directory = Files.createTempDirectory(new File("build").toPath(), "conversion-names-").toFile();
		CaDoodleFile document = new CaDoodleFile().setSelf(new File(directory, "names.doodle"));
		operation.setCaDoodleFile(document);
		String internalId = operation.getName();
		File target = operation.getFile();
		assertTrue(target.createNewFile());
		IScriptingLanguage original = ScriptingEngine.getLangaugeByExtension(operation.getExtension());
		ScriptingEngine.addScriptingLanguage(new ShapeLoader(original));
		try {
			CSG untouched = new Cube(10).toCSG().setName("untouched-id").setUserDefinedName("Keep me");
			for (int replay = 0; replay < 2; replay++) {
				List<CSG> result = operation.process(Collections.singletonList(untouched));
				assertEquals(3, result.size());
				assertSame(untouched, result.get(0));
				assertEquals("Keep me", untouched.getUserDefinedName());
				for (int i = 0; i < 2; i++) {
					CSG output = result.get(i + 1);
					String expectedId = internalId + (i == 0 ? "" : "_" + i);
					assertEquals(expectedId, output.getName());
					assertEquals(label + " " + (i + 1), output.getUserDefinedName());
					CSG regenerated = output.regenerate();
					assertEquals(expectedId, regenerated.getName());
					assertEquals(label + " " + (i + 1), regenerated.getUserDefinedName());
				}
				assertEquals(internalId, operation.getName());
				assertEquals(internalId + "." + operation.getExtension(), target.getName());
				assertEquals(0, target.length());
			}
		} finally {
			ScriptingEngine.addScriptingLanguage(original);
			for (File file : directory.listFiles())
				Files.delete(file.toPath());
			Files.delete(directory.toPath());
		}
	}

	// Exercise add-file and regeneration paths; avoid launching external CAD tools
	private static class ShapeLoader implements IScriptingLanguage {
		private final IScriptingLanguage original;

		ShapeLoader(IScriptingLanguage original) {
			this.original = original;
		}

		@Override
		public Object inlineScriptRun(CSGDatabaseInstance db, File code, ArrayList<Object> args) {
			CSG first = new Cube(10).toCSG();
			CSG second = new Cube(12).toCSG();
			first.setVolume(1000);
			second.setVolume(1728);
			return Arrays.asList(first, second);
		}

		@Override
		public Object inlineScriptRun(CSGDatabaseInstance db, String code, ArrayList<Object> args) {
			throw new UnsupportedOperationException("File loading only");
		}

		@Override
		public String getShellType() {
			return original.getShellType();
		}

		@Override
		public ArrayList<String> getFileExtension() {
			return original.getFileExtension();
		}

		@Override
		public boolean getIsTextFile() {
			return original.getIsTextFile();
		}
	}
}
