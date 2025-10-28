package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.Build123dLoader;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.common.Log;

public class Build123dTest {

	@Test
	public void test() throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
//		Build123dLoader loader = new Build123dLoader();
//		Log.enableDebugPrint();
//		// create test file
//		File testblend = new File("build123dTest.py");
//		if(!testblend.exists())
//			loader.getDefaultContents(testblend);
//		HashMap<String,Double> params = new HashMap<String, Double>();
//		Build123dLoader.toSTLFile(testblend, new File("build123dTest.py.stl"),params);
//		
//		File gears = ScriptingEngine.fileFromGit("https://github.com/GarryBGoode/gggears.git", "examples/examples.py");
//		Build123dLoader.toSTLFile(gears, new File("gears.stl"),params);
		
		
	}

}
