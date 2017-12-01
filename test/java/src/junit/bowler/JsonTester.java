package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.JsonRunner;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import javafx.scene.control.MenuItem;

public class JsonTester {

  @Test
  public void test() throws Exception {
		/*
		try{
			ScriptingEngine.setupAnyonmous();
			//ScriptingEngine.setAutoupdate(true);
		}catch (Exception ex){
			System.out.println("User not logged in, test can not run");
		}
		File f = ScriptingEngine
				.fileFromGit(
						"https://github.com/madhephaestus/BowlerStudioExampleRobots.git",// git repo, change this if you fork this demo
					"exampleRobots.json"// File from within the Git repo
				);
		System.out.println("File: "+f);
		HashMap<String,HashMap<String,Object>> map = (HashMap<String, HashMap<String, Object>>) ScriptingEngine
																	.inlineFileScriptRun(f, null);
		for(String menuTitle:map.keySet()){
			HashMap<String,Object> script = map.get(menuTitle);
			System.out.println((String)script.get("scriptGit"));
			System.out.println((String)script.get("scriptFile"));						;
			
		}
		*/
  }

}
