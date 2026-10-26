package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.CSG.OptType;

/**
 * Writes the gate's shape corpus to /tmp/gate_out, one file per shape, so
 * check_step.py can be run over it. Uses the app's boolean output (Manifold3d).
 */
public class DiagGateExportTest {

	@Test
	public void export() throws Throwable {
		CSG.setDefaultOptType(OptType.Manifold3d);
		System.out
				.println("GATEWELD weld=" + System.getenv("STEP_WELD") + " always=" + System.getenv("STEP_ALWAYSWELD"));
		System.out.println("GATESWEEP minweld=" + System.getenv("STEP_MINWELD"));
		System.out.println(
				"GATESWEEP minweld=" + System.getenv("STEP_MINWELD") + " always=" + System.getenv("STEP_ALWAYS"));
		File out = new File("/tmp/gate_out");
		out.mkdirs();
		for (File stale : out.listFiles())
			if (stale.getName().endsWith(".step"))
				stale.delete();

		List<CSG> shapes = new ArrayList<>();
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(10).toCSG().setName("cube"));
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG()).setName("square_hole"));
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 20, 32).toCSG().move(0, 0, -10)).setName("round_hole"));
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 16).toCSG().move(-10, 0, -10))
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 16).toCSG().move(10, 0, -10)).setName("two_holes"));
		shapes.add(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 32).toCSG().setName("cylinder"));
		shapes.add(new eu.mihosoft.vrl.v3d.Sphere(10, 24, 12).toCSG().setName("sphere"));
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(20, 20, 4).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Sphere(6, 24, 12).toCSG().move(10, 10, 2)).setName("sphere_cut"));

		for (CSG shape : shapes)
			new STEPFile(shape).write(new File(out, shape.getName() + ".step"));

		for (String name : new String[]{"Charming_Troll_Group_of_3.3mf", "Pointer_Hand_star.3mf"}) {
			File source = new File("testStepFiles", name);
			if (!source.isFile())
				continue;
			CSG part = CSG.fromThreeMF(source.toPath()).get(0);
			String base = name.replaceAll("\\..*", "");
			new STEPFile(part).write(new File(out, base + ".step"));
		}
		System.out.println("GATEEXPORT into " + out.getAbsolutePath());
	}
}
