package junit.bowler;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.CSG.OptType;

public class GenScratch {
	@Test
	public void generate() throws Exception {
		CSG.setDefaultOptType(OptType.Manifold3d);
		System.setProperty("step.debug", "1");
		new STEPFile(new eu.mihosoft.vrl.v3d.Cube(40,40,10).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8,12,32).toCSG()).setName("ring")).toStep();
		System.out.println("DONE");
	}
}
