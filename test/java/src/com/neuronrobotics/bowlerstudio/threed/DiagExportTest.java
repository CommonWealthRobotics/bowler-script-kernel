package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/** Writes fixed paths so an external validator can check them. */
public class DiagExportTest {
	@Test
	public void diag() throws Throwable {
		String out = "/tmp/steps";
		new File(out).mkdirs();
		write(out + "/plate_through_hole.step",
				new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
						.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 20, 32).toCSG().move(0, 0, -10)));
		write(out + "/two_holes.step",
				new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
						.difference(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 16).toCSG().move(-10, 0, -10))
						.difference(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 16).toCSG().move(10, 0, -10)));
		write(out + "/square_hole.step", new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG()));
		List<CSG> troll = CSG.fromThreeMF(new File("testStepFiles", "Charming_Troll_Group_of_3.3mf").toPath());
		new STEPFile(troll).write(new File(out + "/troll.step"));
		File hand = new File("testStepFiles", "Pointer_Hand_star.3mf");
		new STEPFile(CSG.fromThreeMF(hand.toPath()).get(0)).write(new File(out + "/hand.step"));
		write(out + "/sphere.step", new eu.mihosoft.vrl.v3d.Sphere(10, 24, 12).toCSG());
		write(out + "/cube.step", new eu.mihosoft.vrl.v3d.Cube(10).toCSG());
		write(out + "/cylinder.step", new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 32).toCSG());
		write(out + "/sphere_cut.step",
				new eu.mihosoft.vrl.v3d.Cube(20, 20, 4).toCSG()
						.difference(new eu.mihosoft.vrl.v3d.Sphere(6, 24, 12).toCSG().move(10, 10, 2)));
		System.out.println("DIAG written to " + out);
	}

	private static void write(String path, CSG csg) throws Exception {
		new STEPFile(csg).write(new File(path));
		System.out.println("DIAG wrote " + path);
	}
}
