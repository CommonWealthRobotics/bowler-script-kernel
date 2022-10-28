package junit.bowler;

import static org.junit.Assert.*;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

public class SequenceTest {

	@Test
	public void test() throws Exception {
		try {
			ScriptingEngine.gitScriptRun("https://github.com/madhephaestus/sequencetest.git", "test.sequence");
		}catch(Throwable t) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			t.printStackTrace(pw);
			fail(sw.toString());
		}
	}

}
