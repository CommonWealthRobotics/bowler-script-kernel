package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.CSG;

public class BlenderLoadingTest {

	@Test
	public void test() throws Exception {
		CSG loaded =(CSG)ScriptingEngine.gitScriptRun(
				"https://github.com/madhephaestus/TestRepo.git",
				"TestRepo4.blend");
		if(loaded.getPolygons().size()!=12)
			fail("Failed to load polygon!");
		System.out.println("Blender file loaded num polys: "+loaded.getPolygons().size());
	}

}
