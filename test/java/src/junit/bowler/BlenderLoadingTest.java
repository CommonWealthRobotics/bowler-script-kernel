package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.util.GeometrySimplification;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;

public class BlenderLoadingTest {

	@Test
	public void test() throws Exception {
		CSG loaded =(CSG)ScriptingEngine.gitScriptRun(
				"https://github.com/madhephaestus/TestRepo.git",
				"TestRepo4.blend");
		if(loaded.getPolygons().size()!=12)
			fail("Failed to load polygon!");
		System.out.println("Blender file loaded num polys: "+loaded.getPolygons().size());
		CSG cube = new Cube(100).toCSG();
		CSG remeshed = GeometrySimplification.remesh(cube, 10.0);
		if(remeshed.getPolygons().size()!=1452)
			fail("Blender failed to remesh");
		System.out.println("Remeshing produced: "+remeshed.getPolygons().size());
	}

}
