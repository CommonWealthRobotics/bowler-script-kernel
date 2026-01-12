package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.util.HashMap;

import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.Build123dLoader;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

public class Build123dTest {

	@Test
	public void test() throws Exception {
		Log.enableDebugPrint();
		//ScriptingEngine.pull("https://github.com/madhephaestus/CaDoodle-Example-Objects.git");
		ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(), 
				"https://github.com/madhephaestus/CaDoodle-Example-Objects.git", "build123d/gggears.groovy");
		
		
	}

}
