package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

public class OpenSCADTest {

	@Test
	public void test() {
		try {
			ScriptingEngine.inlineFileScriptRun(new File("OpenScadScrit.scad"), null);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

}
