package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

public class SequenceTest {

	@Test
	public void test() throws Exception {
		ScriptingEngine.gitScriptRun("https://github.com/madhephaestus/sequencetest.git", "test.sequence");
	}

}
