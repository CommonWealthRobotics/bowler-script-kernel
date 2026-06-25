package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.Build123dLoader;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.video.OSUtil;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

public class Build123dTest {

	@Test
	public void test() throws Exception {
//		if (OSUtil.isWindows() || OSUtil.isOSX())
//			return;
		Log.enableDebugPrint();
		Map<String, Object> options = Build123dLoader.getTypeOptions();
		com.neuronrobotics.sdk.common.Log.debug("Build123d Options "+options);
		for (Map.Entry<String, Object> entry : options.entrySet()) {
			Log.debug("\tCatagories "+entry.getKey() + " = " + entry.getValue());
			Map<String, Object> types = Build123dLoader.getTypeOptions(entry.getKey());
			for(String t:types.keySet()) {
				Log.debug("\t\t Types " +t+ " = " + types.get(t));
			}
		}

		ScriptingEngine.pull("https://github.com/madhephaestus/CaDoodle-Example-Objects.git");
		ArrayList<CSG> parts = (ArrayList<CSG>) ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(),
				"https://github.com/madhephaestus/CaDoodle-Example-Objects.git", "build123d/gggears.groovy");

		if (parts.size() == 0)
			throw new IOException("Failed to create files");


	}

}
