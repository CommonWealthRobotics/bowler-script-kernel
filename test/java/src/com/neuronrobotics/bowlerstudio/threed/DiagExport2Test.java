package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/** Writes more fixed paths for external OCCT validation. */
public class DiagExport2Test {
	@Test
	public void diag() throws Throwable {
		String out = "/tmp/steps2";
		new File(out).mkdirs();
		write(out + "/island.step", new eu.mihosoft.vrl.v3d.Cube(60, 60, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(12, 20, 32).toCSG().move(0, 0, -10))
				.union(new eu.mihosoft.vrl.v3d.Cylinder(6, 8, 24).toCSG()));
		write(out + "/nested.step", new eu.mihosoft.vrl.v3d.Cube(40, 40, 2).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(12, 10, 24).toCSG().move(0, 0, -5))
				.union(new eu.mihosoft.vrl.v3d.Cylinder(5, 4, 16).toCSG()));
		write(out + "/troll.step",
				CSG.fromThreeMF(new File("testStepFiles", "Charming_Troll_Group_of_3.3mf").toPath()).get(0));
		List<CSG> hand = CSG.fromThreeMF(new File("testStepFiles", "Pointer_Hand_star.3mf").toPath());
		new STEPFile(hand.get(0)).write(new File(out + "/hand.step"));
		System.out.println("DIAG done");
	}

	private static void write(String path, CSG csg) throws Exception {
		new STEPFile(csg).write(new File(path));
	}
}
