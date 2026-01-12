package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.Build123dLoader;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;

public class Build123dTest {

	@Test
	@Ignore
	public void test() throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		Build123dLoader loader = new Build123dLoader();
		Log.enableDebugPrint();
		//ScriptingEngine.pull("https://github.com/madhephaestus/CaDoodle-Example-Objects.git");
		ArrayList<CSG > parts = (ArrayList<CSG>)ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(), 
				"https://github.com/madhephaestus/CaDoodle-Example-Objects.git", "build123d/gggears.groovy");

//		if(parts.size()==0)
//			throw new IOException("Failed to create files");
		
		
	}

}
