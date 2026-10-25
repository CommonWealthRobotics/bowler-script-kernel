package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/**
 * Checks the invariants a CAD kernel needs from the written faces: every face
 * encloses area, every loop is a real polygon, and every edge is bound by
 * exactly two faces, once each way.
 */
public class DiagInvariantTest {

	@Test
	public void diag() throws Throwable {
		List<CSG> shapes = new ArrayList<>();
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(10).toCSG());
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG()));
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 20, 32).toCSG().move(0, 0, -10)));
		shapes.add(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 32).toCSG());
		shapes.add(new eu.mihosoft.vrl.v3d.Sphere(10, 24, 12).toCSG());
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(20, 20, 4).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Sphere(6, 24, 12).toCSG().move(10, 10, 2)));
		File troll = new File("testStepFiles", "Charming_Troll_Group_of_3.3mf");
		if (troll.isFile())
			shapes.add(CSG.fromThreeMF(troll.toPath()).get(0));
		File hand = new File("testStepFiles", "Pointer_Hand_star.3mf");
		if (hand.isFile())
			shapes.add(CSG.fromThreeMF(hand.toPath()).get(0));

		for (int s = 0; s < shapes.size(); s++)
			check("shape" + s, shapes.get(s));
	}

	private static void check(String label, CSG source) {
		List<StepFace> faces = StepBRepWrite.faces(source);

		int zeroArea = 0;
		int degenerateLoop = 0;
		int shortLoop = 0;
		int offPlane = 0;
		List<String> examples = new ArrayList<>();
		for (StepFace face : faces) {
			double net = 0;
			for (int i = 0; i < face.loops.size(); i++) {
				double area = Math.abs(DiagHolesTest.projected(face.loops.get(i), face.normal)) / 2;
				net += i == 0 ? area : -area;
				if (face.loops.get(i).size() < 3) {
					shortLoop++;
					if (examples.size() < 5)
						examples.add("loop with " + face.loops.get(i).size() + " points");
				}
				java.util.Set<String> distinct = new java.util.HashSet<>();
				for (double[] p : face.loops.get(i))
					distinct.add(p[0] + "," + p[1] + "," + p[2]);
				if (distinct.size() < 3) {
					degenerateLoop++;
					if (examples.size() < 5)
						examples.add("loop with " + distinct.size() + " distinct points of "
								+ face.loops.get(i).size());
				}
			}
			if (Math.abs(net) < 1e-9) {
				zeroArea++;
				if (examples.size() < 5)
					examples.add("zero net area, loops=" + face.loops.size() + " sizes=" + face.loops.size());
			}
			if (!face.isPlanar())
				offPlane++;
		}

		// edge pairing at full precision
		java.util.Map<String, Integer> directed = new java.util.HashMap<>();
		for (StepFace face : faces)
			for (List<double[]> loop : face.loops)
				for (int i = 0; i < loop.size(); i++)
					directed.merge(key(loop.get(i)) + ">" + key(loop.get((i + 1) % loop.size())), 1, Integer::sum);
		int unpaired = 0;
		int doubled = 0;
		for (java.util.Map.Entry<String, Integer> entry : directed.entrySet()) {
			String edge = entry.getKey();
			int split = edge.indexOf('>');
			String reverse = edge.substring(split + 1) + ">" + edge.substring(0, split);
			if (!Integer.valueOf(1).equals(directed.get(reverse))) {
				unpaired++;
				if (examples.size() < 8)
					examples.add("unpaired " + edge);
			}
			if (entry.getValue() != 1)
				doubled++;
		}

		System.out.println("DIAG " + label + " tris=" + source.getTriCount() + " faces=" + faces.size()
				+ " zeroArea=" + zeroArea + " degenerateLoop=" + degenerateLoop + " shortLoop=" + shortLoop
				+ " nonPlanar=" + offPlane + " unpairedEdges=" + unpaired + " doubledEdges=" + doubled);
		for (String example : examples)
			System.out.println("DIAG   " + example);
	}

	private static String key(double[] p) {
		return Double.toString(p[0]) + "," + Double.toString(p[1]) + "," + Double.toString(p[2]);
	}
}
