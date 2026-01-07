package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

public class SequenceTest {

	@Test
	public void test() throws Exception {
//		try {
//			ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(),"https://github.com/madhephaestus/sequencetest.git", "test.sequence");
//		}catch(Throwable t) {
//			StringWriter sw = new StringWriter();
//			PrintWriter pw = new PrintWriter(sw);
//			t.printStackTrace(pw);
//			fail(sw.toString());
//		}
	}

}
