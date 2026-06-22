package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.Build123dLoader;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

public class Build123dTest {

	@Test
	
	public void test() throws Exception {
		Log.enableDebugPrint();
//		HashMap<String, String> options = Build123dLoader.getTypeOptions();
//		com.neuronrobotics.sdk.common.Log.debug("Build123d Options ");
//		for (Map.Entry<String, String> entry : options.entrySet()) {
//			System.out.println(entry.getKey() + " = " + entry.getValue());
//
//		}
		ScriptingEngine.pull("https://github.com/madhephaestus/CaDoodle-Example-Objects.git");
		ArrayList<CSG> parts = (ArrayList<CSG>) ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(),
				"https://github.com/madhephaestus/CaDoodle-Example-Objects.git", "build123d/gggears.groovy");

		if (parts.size() == 0)
			throw new IOException("Failed to create files");


	}

}
