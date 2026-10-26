package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import org.junit.Test;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.CSG.OptType;

/** Whether the troll mesh is manifold by its own indices, and its sliver census. */
public class DiagTrollTest {
	@Test
	public void diag() throws Throwable {
		CSG.setDefaultOptType(OptType.Manifold3d);
		CSG csg = CSG.fromThreeMF(new File("testStepFiles", "Charming_Troll_Group_of_3.3mf").toPath()).get(0);
		long[] t = csg.getTriangles();
		java.util.Map<Long, Integer> dir = new java.util.HashMap<>();
		for (int i = 0; i + 2 < t.length; i += 3)
			for (int c = 0; c < 3; c++) {
				long from = t[i + c], to = t[i + (c + 1) % 3];
				dir.merge((from << 32) | (to & 0xffffffffL), 1, Integer::sum);
			}
		int unpaired = 0, doubled = 0;
		for (java.util.Map.Entry<Long, Integer> e : dir.entrySet()) {
			long from = e.getKey() >>> 32, to = e.getKey() & 0xffffffffL;
			Integer rev = dir.get((to << 32) | (from & 0xffffffffL));
			if (e.getValue() != 1) doubled++;
			if (rev == null) unpaired++;
		}
		System.out.println("DIAGTROLL tris=" + t.length / 3 + " verts=" + csg.getVertCount() + " unpaired=" + unpaired
				+ " doubled=" + doubled);
	}
}
