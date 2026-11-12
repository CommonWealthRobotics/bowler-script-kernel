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
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ConvertToOpenSCAD;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import javafx.scene.paint.Color;

/**
 * Runs the convert-to-OpenSCAD operation against a real document: the
 * selection is removed, the script and its 3MF land in the doodle directory,
 * the script is added back, and a replay re-writes the 3MF from the sources so
 * the OpenSCAD file always operates on live data.
 */
public class ConvertToOpenSCADTest {

	@Before
	public void setup() throws Exception {
		BowlerKernel.startupProcedures();
		JavaFXInitializer.go();
	}

	private CaDoodleFile newProject(File self) throws Exception {
		CaDoodleFile cf = new CaDoodleFile().setSelf(self).setProjectName("OpenSCAD Convert Test");
		cf.initialize();
		return cf;
	}

	private String addTwoSpheres(CaDoodleFile cf) throws Exception {
		AddFromScript sphere1 = new AddFromScript().set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
				"sphere.groovy");
		AddFromScript sphere2 = new AddFromScript().set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
				"sphere.groovy");
		cf.addOperation(sphere1).join();
		cf.addOperation(sphere2).join();
		// give the second sphere a known colour to carry through the conversion
		cf.getCurrentState().get(1).setColor(Color.RED);
		return cf.getCurrentState().get(1).getName();
	}

	@Test
	public void convertsToOpenSCAD() throws Exception {
		File self = new File("doodle/ConvertOpenSCADTest.doodle");
		CaDoodleFile cf = newProject(self);
		String selected = addTwoSpheres(cf);

		ConvertToOpenSCAD convert = new ConvertToOpenSCAD().setNames(Arrays.asList(selected));
		cf.addOperation(convert).join();

		// the selected object is gone, the converted file's object is added
		List<CSG> state = cf.getCurrentState();
		assertEquals("selected removed and file added", 2, state.size());
		assertFalse("converted object must have a new name", state.get(1).getName().contentEquals(selected));
		// the converted object keeps its colour through the 3MF round trip
		assertEquals("colour preserved", Color.RED, state.get(1).getColor());
		// the geometry survived: both spheres have their volume
		assertTrue("converted object has volume", state.get(1).getVolume() > 0);

		// the script is in the doodle directory and imports the 3MF beside it
		File target = convert.getFile();
		assertTrue("OpenSCAD file was created", target.exists());
		assertTrue("file is in the doodle directory",
				target.getParentFile().getAbsolutePath().contentEquals(self.getParentFile().getAbsolutePath()));
		String scad = new String(java.nio.file.Files.readAllBytes(target.toPath()));
		String threeMFName = target.getName().split("\\.")[0] + ".3mf";
		assertTrue("script imports the 3MF", scad.contains("import(\"" + threeMFName + "\")"));

		// the 3MF is beside the script and carries both spheres
		File threeMF = new File(target.getParentFile(), threeMFName);
		assertTrue("3MF was created", threeMF.exists());
		assertTrue("3MF is not empty", threeMF.length() > 0);

		// opening the script in OpenSCAD loads the live geometry
		List<CSG> loaded = CSG.fromThreeMF(threeMF.toPath());
		assertEquals("3MF holds both spheres", 2, loaded.size());
		assertEquals("3MF carries the colour", Color.RED, loaded.get(1).getColor());

		cf.save();
		cf.close();
	}

	@Test
	public void threeMFIsRewrittenOnReplay() throws Exception {
		File self = new File("doodle/ConvertOpenSCADReplayTest.doodle");
		CaDoodleFile cf = newProject(self);
		String selected = addTwoSpheres(cf);

		ConvertToOpenSCAD convert = new ConvertToOpenSCAD().setNames(Arrays.asList(selected));
		cf.addOperation(convert).join();

		File threeMF = new File(convert.getFile().getParentFile(),
				convert.getFile().getName().split("\\.")[0] + ".3mf");
		assertTrue("3MF was created", threeMF.exists());
		long lastModified = threeMF.lastModified();

		cf.save();
		cf.close();
		// a reload must replay the operation and re-write the 3MF from the
		// sources, unlike the Blender and FreeCAD conversions which keep it
		CaDoodleFile loaded = CaDoodleFile.fromFile(self);
		assertTrue("timeline keeps the convert operation", loaded.getOperations().get(2) instanceof ConvertToOpenSCAD);
		assertTrue("the 3MF was re-written on replay", threeMF.lastModified() > lastModified);
		loaded.close();
	}
}
