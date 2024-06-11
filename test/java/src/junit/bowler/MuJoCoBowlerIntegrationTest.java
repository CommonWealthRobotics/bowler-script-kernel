package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.mujoco.xml.attributetypes.IntegratorType;

import com.neuronrobotics.bowlerstudio.physics.MuJoCoPhysicsManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
@SuppressWarnings("unchecked")
public class MuJoCoBowlerIntegrationTest {

	@Test
	public void test() throws Exception {
//		try {
//			JavaFXInitializer.go();
//		} catch (Throwable t) {
//			t.printStackTrace();
//			System.err.println("ERROR No UI engine availible");
//		}
//		ArrayList<MobileBase> bases = new ArrayList<>();
//		ArrayList<CSG> free =new ArrayList<>();
//		ArrayList<CSG> terrain = new ArrayList<>();
//
//
//		List<CSG> parts = (List<CSG>) ScriptingEngine.gitScriptRun(
//				"https://github.com/madhephaestus/VexHighStakes2024.git",
//				"fieldElements.groovy");
//		terrain= (ArrayList<CSG>) ScriptingEngine.gitScriptRun(
//				"https://github.com/madhephaestus/VexHighStakes2024.git",
//				"field.groovy");
//		System.out.println("Parts size = "+parts.size());
//		//terrain.add(new Cube(10000,10000,100).toCSG().toZMax());
//		free.addAll(parts);
//		MuJoCoPhysicsManager manager = new MuJoCoPhysicsManager("javaCadTest", bases, free, terrain, new File("./physicsTest"));
////		manager.setTimestep(0.005);
////		manager.setIntegratorType(IntegratorType.IMPLICITFAST);
//		manager.generateNewModel();// generate model before start counting time
//		long start = System.currentTimeMillis();
//		double now = 0;
//		boolean first=true;
//		while((now=manager.getCurrentSimulationTimeSeconds())<5) {
//			long took;
//			if((took = manager.stepAndWait())>(manager.getCurrentSimulationTimeSeconds()*1000.0)) {
//				if(first) {
//					first=false;
//					continue;
//				}
//				fail("Real time broken! "+took+" instead of expected "+manager.getCurrentSimulationTimeSeconds());
//			}else {
//				System.out.println("Time "+now);
//			}
//			long timeSinceStart = System.currentTimeMillis()-start;
//			double sec = ((double)timeSinceStart)/1000.0;
//			if((sec-1)>now) {
//				fail("Simulation froze and restarted! "+sec+" expected "+now);
//			}
//		}
//		manager.close();
//		System.out.println("Success!");

	}

}
