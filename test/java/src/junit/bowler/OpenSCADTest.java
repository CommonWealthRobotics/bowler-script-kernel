package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

public class OpenSCADTest {

	@Test
	public void test() {
		try {
			ScriptingEngine.inlineFileScriptRun(CSGDatabase.getInstance(), new File("OpenScadScript.scad"), null);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

}
