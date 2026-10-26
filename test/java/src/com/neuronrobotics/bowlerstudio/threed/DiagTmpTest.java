package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.util.*;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/** Reports unpaired directed edges of the file as written (named loops). */
public class DiagTmpTest {
	@Test
	public void diag() throws Throwable {
		for (String name : new String[]{"Charming_Troll_Group_of_3.3mf", "Pointer_Hand_star.3mf"}) {
			CSG c = CSG.fromThreeMF(new File("testStepFiles", name).toPath()).get(0);
			File step = File.createTempFile("diag_" + name.replaceAll("\\W", ""), ".step");
			step.deleteOnExit();
			new STEPFile(c).write(step);
			String text = new String(java.nio.file.Files.readAllBytes(step.toPath()), "UTF-8");
			System.out.println("DIAGFILE " + name + " faces=" + count(text, "ADVANCED_FACE") + " open="
					+ count(text, "OPEN_SHELL") + " closed=" + count(text, "CLOSED_SHELL"));
		}
	}

	private static int count(String t, String s) {
		int n = 0;
		for (int i = t.indexOf(s); i >= 0; i = t.indexOf(s, i + s.length()))
			n++;
		return n;
	}
}
