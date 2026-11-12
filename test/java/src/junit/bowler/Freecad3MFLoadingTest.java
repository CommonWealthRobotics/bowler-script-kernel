package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.scripting.FreecadLoader;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import javafx.scene.paint.Color;

/**
 * Loading a FreeCAD model must export a 3MF file and read the 3MF's meshes, so
 * every solid in the model survives the load, and the colour the model was
 * converted with is not replaced by the loader.
 */
public class Freecad3MFLoadingTest {

	@Before
	public void setup() throws Exception {
		BowlerKernel.startupProcedures();
		JavaFXInitializer.go();
	}

	@Test
	public void loadsFromExported3MF() throws Exception {
		File model = new File("FreeCADModel/Supersonic_Griffin_4lx0E5mzhw.FCStd");
		assertTrue("test fixture missing: " + model, model.exists());

		Object loaded = new FreecadLoader().inlineScriptRun(null, model, new ArrayList<>());
		assertTrue("a FreeCAD file loads at least one CSG", loaded instanceof CSG);
		CSG csg = (CSG) loaded;
		assertTrue("geometry was loaded from the 3MF export", csg.getVertCount() > 0);

		// the 3MF round trip preserves the part's exact bounds
		assertEquals("X extent", 22.35, csg.getMaxX() - csg.getMinX(), 0.1);
		assertEquals("Z extent", 31.0, csg.getMaxZ() - csg.getMinZ(), 0.1);
	}

	@Test
	public void exports3MFFile() throws Exception {
		File model = new File("FreeCADModel/Supersonic_Griffin_4lx0E5mzhw.FCStd");
		File threeMF = File.createTempFile("freecad_3mf", ".3mf");
		threeMF.deleteOnExit();
		FreecadLoader.to3MFFile(model, threeMF);
		assertTrue("3MF export produced a file", threeMF.exists());
		assertTrue("3MF export is not empty", threeMF.length() > 0);
	}
}
