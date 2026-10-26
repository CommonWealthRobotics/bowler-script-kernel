package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/** Writes exports so an external CAD kernel can validate them. */
public class DiagExport2Test {
	@Test
	public void diag() throws Throwable {
		String out = "/tmp/steps_final2";
		new File(out).mkdirs();
		new STEPFile(CSG.fromThreeMF(new File("testStepFiles", "Charming_Troll_Group_of_3.3mf").toPath()).get(0))
				.write(new File(out + "/troll.step"));
		List<CSG> hand = CSG.fromThreeMF(new File("testStepFiles", "Pointer_Hand_star.3mf").toPath());
		new STEPFile(hand.get(0)).write(new File(out + "/hand.step"));
		new STEPFile(new eu.mihosoft.vrl.v3d.Cube(10).toCSG()).write(new File(out + "/cube.step"));
		new STEPFile(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG()))
				.write(new File(out + "/square_hole.step"));
		new STEPFile(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 20, 32).toCSG().move(0, 0, -10)))
				.write(new File(out + "/round_hole.step"));
		new STEPFile(new eu.mihosoft.vrl.v3d.Sphere(10, 24, 12).toCSG()).write(new File(out + "/sphere.step"));
		new STEPFile(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 32).toCSG()).write(new File(out + "/cylinder.step"));
		System.out.println("DIAG done " + out);
	}
}
