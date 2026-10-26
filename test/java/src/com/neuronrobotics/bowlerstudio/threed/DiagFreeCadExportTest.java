package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/** Writes the 3MF fixtures into out/ for check_step.py. Never writes testStepFiles. */
public class DiagFreeCadExportTest {
	@Test
	public void export() throws Throwable {
		File out = new File("../step-gate-out");
		out.mkdirs();
		new STEPFile(CSG.fromThreeMF(new File("testStepFiles", "Charming_Troll_Group_of_3.3mf").toPath()))
				.write(new File(out, "troll.step"));
		new STEPFile(CSG.fromThreeMF(new File("testStepFiles", "Pointer_Hand_star.3mf").toPath()).get(0))
				.write(new File(out, "star.step"));
		System.out.println("FCEXPORT into " + out.getAbsolutePath());
	}
}
