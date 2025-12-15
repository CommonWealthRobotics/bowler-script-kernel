package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Test;
import org.mujoco.xml.attributetypes.IntegratorType;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.physics.MuJoCoPhysicsManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
@SuppressWarnings("unchecked")
public class MuJoCoBowlerIntegrationTest {
	  @Before
	  public void setup() throws InvalidRemoteException, TransportException, IOException, GitAPIException, Exception {
		  BowlerKernel.startupProcedures();
	  }
	@Test
	public void test() throws Exception {
		try {
			JavaFXInitializer.go();
		} catch (Throwable t) {
			t.printStackTrace();
			com.neuronrobotics.sdk.common.Log.error("ERROR No UI engine availible");
		}
		ArrayList<MobileBase> bases = new ArrayList<>();
		ArrayList<CSG> free =new ArrayList<>();
		ArrayList<CSG> terrain = new ArrayList<>();


		List<CSG> parts = (List<CSG>) ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(),
				"https://github.com/madhephaestus/VexHighStakes2024.git",
				"fieldElements.groovy");
		terrain= (ArrayList<CSG>) ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(),
				"https://github.com/madhephaestus/VexHighStakes2024.git",
				"field.groovy");
		com.neuronrobotics.sdk.common.Log.error("Parts size = "+parts.size());
		//terrain.add(new Cube(10000,10000,100).toCSG().toZMax());
		free.addAll(parts);
		MuJoCoPhysicsManager manager = new MuJoCoPhysicsManager(CSGDatabase.getInstance(),"javaCadTest", bases, free, terrain, new File("./physicsTest"));
//		manager.setTimestep(0.005);
//		manager.setIntegratorType(IntegratorType.IMPLICITFAST);
		manager.generateNewModel(CSGDatabase.getInstance());// generate model before start counting time
		long start = System.currentTimeMillis();
		double now = 0;
		boolean first=true;
		while((now=manager.getCurrentSimulationTimeSeconds())<5) {
			long took;
			if((took = manager.stepAndWait())>(manager.getCurrentSimulationTimeSeconds()*1000.0)) {
				if(first) {
					first=false;
					continue;
				}
				fail("Real time broken! "+took+" instead of expected "+manager.getCurrentSimulationTimeSeconds());
			}else {
				com.neuronrobotics.sdk.common.Log.error("Time "+now);
			}
			long timeSinceStart = System.currentTimeMillis()-start;
			double sec = ((double)timeSinceStart)/1000.0;
			if((sec-1)>now) {
				fail("Simulation froze and restarted! "+sec+" expected "+now);
			}
		}
		manager.close();
		com.neuronrobotics.sdk.common.Log.error("Success!");

	}

}
