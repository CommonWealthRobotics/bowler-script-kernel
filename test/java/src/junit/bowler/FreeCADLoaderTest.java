package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Paths;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

public class FreeCADLoaderTest {

	@Test
	public void test() throws Exception {
		File model = new File("FreeCADModel.FCStd");
		CSG back = (CSG) ScriptingEngine.inlineScriptRun(CSGDatabase.getInstance(), model, null, "FreeCAD");
		FileUtil.write(Paths.get(model.getName() + ".stl"), back.toStlString());
	}

}
