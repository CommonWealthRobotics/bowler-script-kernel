package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.physics.MuJoCoPhysicsManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;

public class MuJoCoBowlerIntegrationTest {

	@Test
	public void test() throws Exception {
		try {
			JavaFXInitializer.go();
		} catch (Throwable t) {
			t.printStackTrace();
			System.err.println("ERROR No UI engine availible");
		}
		@SuppressWarnings("unchecked")
		List<CSG> parts = (List<CSG>) ScriptingEngine.gitScriptRun(
				"https://gist.github.com/4814b39ee72e9f590757.git",
				"javaCad.groovy");
		MobileBase cat = (MobileBase) ScriptingEngine.gitScriptRun(
				"https://github.com/OperationSmallKat/Marcos.git",
				"Marcos.xml");
		ArrayList<MobileBase> bases = new ArrayList<>();
		cat.connect();
		bases.add(cat);
		ArrayList<CSG> lifted =new ArrayList<>();
		ArrayList<CSG> terrain = new ArrayList<>();
		//terrain.add(new Cube(10000,10000,100).toCSG().toZMax());
		for(int i=35;i<parts.size();i++) {
			if (i==27||i==25)
				continue;
			CSG p= parts.get(i);
			CSG pl=p.roty(15).movez(200);
			pl.setName(p.getName());
			lifted.add(pl);
			terrain.add(p);
		}
		MuJoCoPhysicsManager manager = new MuJoCoPhysicsManager("javaCadTest", bases, lifted, terrain, new File("./physicsTest"));
//		manager.setTimestep(0.005);
//		manager.generateNewModel();
//		File f = manager.getXMLFile();
//		String s = manager.getXML();
//		System.out.println(s);
//		System.out.println(f.getAbsolutePath());
		System.out.println("Parts size = "+parts.size());
		manager.generateNewModel();// generate model before start counting time
		long start = System.currentTimeMillis();
		double now = 0;
		while((now=manager.getCurrentSimulationTimeSeconds())<5) {
			if(!manager.stepAndWait()) {
				fail("Real time broken!");
			}else {
				System.out.println("Time "+now);
			}
			long timeSinceStart = System.currentTimeMillis()-start;
			double sec = ((double)timeSinceStart)/1000.0;
			if((sec-1)>now) {
				fail("Simulation froze and restarted! "+sec+" expected "+now);
			}
		}
		manager.close();
		System.out.println("Success!");

	}

}
