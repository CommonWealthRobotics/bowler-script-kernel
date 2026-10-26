package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.CSG.OptType;

/** Is the writer's own welded triangle set still manifold? */
public class DiagTrollWeld2Test {
	@SuppressWarnings("unchecked")
	@Test
	public void diag() throws Throwable {
		CSG.setDefaultOptType(OptType.Manifold3d);
		CSG csg = CSG.fromThreeMF(new File("testStepFiles", "Charming_Troll_Group_of_3.3mf").toPath()).get(0);
		Class<?> mc = Class.forName("com.neuronrobotics.bowlerstudio.threed.StepBRepWrite$Mesh");
		Constructor<?> ctor = mc.getDeclaredConstructor(double[].class, long[].class);
		ctor.setAccessible(true);
		Object mesh = ctor.newInstance(csg.getVertices(), csg.getTriangles());
		Field tf = mc.getDeclaredField("triangles");
		tf.setAccessible(true);
		List<int[]> tris = (List<int[]>) tf.get(mesh);
		java.util.Map<Long, Integer> dir = new java.util.HashMap<>();
		for (int[] t : tris)
			for (int c = 0; c < 3; c++)
				dir.merge((((long) t[c]) << 32) | (t[(c + 1) % 3] & 0xffffffffL), 1, Integer::sum);
		int unpaired = 0, doubled = 0;
		for (java.util.Map.Entry<Long, Integer> e : dir.entrySet()) {
			long from = e.getKey() >>> 32, to = e.getKey() & 0xffffffffL;
			Integer rev = dir.get((to << 32) | (from & 0xffffffffL));
			if (e.getValue() != 1)
				doubled++;
			if (rev == null)
				unpaired++;
		}
		Field wf = mc.getDeclaredField("weldTolerance");
		wf.setAccessible(true);
		System.out.println("DIAGTROLL2 weldTol=" + wf.get(mesh) + " writerTris=" + tris.size() + " unpairedDirected="
				+ unpaired + " doubled=" + doubled);
	}
}
