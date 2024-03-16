package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.physics.MuJoCoPhysicsManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.CSG;

public class MuJoCoBowlerIntegrationTest {

	@Test
	public void test() throws Exception {
		List<CSG> parts = (List<CSG>) ScriptingEngine.gitScriptRun(
				"https://gist.github.com/4814b39ee72e9f590757.git",
				"javaCad.groovy");
		ArrayList<CSG> lifted =new ArrayList<>();
		ArrayList<CSG> terrain = new ArrayList<>();
		for(int i=45;i<parts.size();i++) {
			CSG p= parts.get(i);
			CSG pl=p.movez(200);
			pl.setName(p.getName());
			lifted.add(pl);
			terrain.add(p);
		}
		MuJoCoPhysicsManager manager = new MuJoCoPhysicsManager("javaCadTest", null, lifted, terrain, new File("./physicsTest"));
		
		File f = manager.getXMLFile();
		String s = manager.getXML();
		System.out.println(s);
		System.out.println(f.getAbsolutePath());
		System.out.println("Parts size = "+parts.size());
	}

}
