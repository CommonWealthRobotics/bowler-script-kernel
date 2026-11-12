package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AddFromScript;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ConvertToBlender;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ConvertToFreeCAD;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;

/**
 * Runs the convert-to-file operations against a real document: the selection is
 * removed, the file lands in the doodle directory, the converted file is added
 * back, and a second run reuses the file it already made.
 */
public class ConvertToFileTest {

	@Before
	public void setup() throws Exception {
		BowlerKernel.startupProcedures();
		JavaFXInitializer.go();
	}

	private CaDoodleFile newProject(File self) throws Exception {
		CaDoodleFile cf = new CaDoodleFile().setSelf(self).setProjectName("Convert Test");
		cf.initialize();
		return cf;
	}

	private String addTwoCubes(CaDoodleFile cf) throws Exception {
		AddFromScript cube1 = new AddFromScript().set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
				"sphere.groovy");
		AddFromScript cube2 = new AddFromScript().set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
				"sphere.groovy");
		cf.addOperation(cube1).join();
		cf.addOperation(cube2).join();
		return cf.getCurrentState().get(1).getName();
	}

	@Test
	public void convertsToFreeCAD() throws Exception {
		File self = new File("doodle/ConvertFreeCADTest.doodle");
		CaDoodleFile cf = newProject(self);
		String selected = addTwoCubes(cf);

		ConvertToFreeCAD convert = new ConvertToFreeCAD().setNames(Arrays.asList(selected));
		cf.addOperation(convert).join();

		// the selected object is gone, the converted file's object is added
		List<CSG> state = cf.getCurrentState();
		assertEquals("selected removed and file added", 2, state.size());
		assertFalse("converted object must have a new name", state.get(1).getName().contentEquals(selected));
		// the geometry survived the headless FreeCAD round trip
		assertTrue("converted object has volume", state.get(1).getVolume() > 0);

		// the file is in the doodle directory
		File target = convert.getFile();
		assertTrue("FreeCAD file was created", target.exists());
		assertTrue("file is in the doodle directory",
				target.getParentFile().getAbsolutePath().contentEquals(self.getParentFile().getAbsolutePath()));
		long size = target.length();
		assertTrue("file is not empty", size > 0);

		cf.save();
		cf.close();
		// a reload must replay the operation without writing the file again
		CaDoodleFile loaded = CaDoodleFile.fromFile(self);
		assertTrue("timeline keeps the convert operation", loaded.getOperations().get(2) instanceof ConvertToFreeCAD);
		assertEquals("the file was not rewritten on replay", size, target.length());
		loaded.close();
	}

	@Test
	public void convertsToBlender() throws Exception {
		File self = new File("doodle/ConvertBlenderTest.doodle");
		CaDoodleFile cf = newProject(self);
		String selected = addTwoCubes(cf);

		ConvertToBlender convert = new ConvertToBlender().setNames(Arrays.asList(selected));
		cf.addOperation(convert).join();

		List<CSG> state = cf.getCurrentState();
		assertEquals("selected removed and file added", 2, state.size());

		File target = convert.getFile();
		assertTrue("Blender file was created", target.exists());
		assertTrue("file is not empty", target.length() > 0);

		cf.save();
		cf.close();
	}
}
