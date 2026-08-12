package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.util.GeometrySimplification;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

public class BlenderLoadingTest {

	@Test
	public void test() throws Exception {
		CSG loaded = (CSG) ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(),
				"https://github.com/madhephaestus/TestRepo.git", "TestRepo4.blend");
		if (loaded.getNumberOfTriangles() != 12)
			fail("Failed to load polygon!");
		com.neuronrobotics.sdk.common.Log.error("Blender file loaded num polys: " + loaded.getNumberOfTriangles());
		CSG cube = new Cube(100).toCSG();
		CSG remeshed = GeometrySimplification.remesh(cube, 10.0, CSGDatabase.getInstance());
		if (remeshed.getNumberOfTriangles() == cube.getNumberOfTriangles())
			fail("Blender failed to remesh " + remeshed.getNumberOfTriangles());
		com.neuronrobotics.sdk.common.Log.error("Remeshing produced: " + remeshed.getNumberOfTriangles());
	}

}
