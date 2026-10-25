package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/** Smallest nonzero triangle area in each shape, relative to the model scale. */
public class DiagAreaScaleTest {

	@Test
	public void diag() throws Throwable {
		List<CSG> shapes = new ArrayList<>();
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(10).toCSG());
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG()));
		shapes.add(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 32).toCSG());
		shapes.add(new eu.mihosoft.vrl.v3d.Sphere(10, 24, 12).toCSG());
		File troll = new File("testStepFiles", "Charming_Troll_Group_of_3.3mf");
		if (troll.isFile())
			shapes.add(CSG.fromThreeMF(troll.toPath()).get(0));
		File hand = new File("testStepFiles", "Pointer_Hand_star.3mf");
		if (hand.isFile())
			shapes.add(CSG.fromThreeMF(hand.toPath()).get(0));

		for (int s = 0; s < shapes.size(); s++) {
			CSG csg = shapes.get(s);
			double[] v = csg.getVertices();
			long[] t = csg.getTriangles();
			double diagonal = diagonal(v);
			double smallest = Double.MAX_VALUE;
			double smallestRatio = Double.MAX_VALUE;
			int belowRel1e9 = 0;
			for (int i = 0; i + 2 < t.length; i += 3) {
				int a = (int) t[i] * 3, b = (int) t[i + 1] * 3, c = (int) t[i + 2] * 3;
				double ux = v[b] - v[a], uy = v[b + 1] - v[a + 1], uz = v[b + 2] - v[a + 2];
				double wx = v[c] - v[a], wy = v[c + 1] - v[a + 1], wz = v[c + 2] - v[a + 2];
				double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
				double area = Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
				if (area > 0) {
					smallest = Math.min(smallest, area);
					double ratio = area / (diagonal * diagonal);
					smallestRatio = Math.min(smallestRatio, ratio);
					if (ratio <= 1e-9)
						belowRel1e9++;
				}
			}
			System.out.println("DIAG shape" + s + " tris=" + csg.getTriCount() + " diagonal=" + String.format("%.3f", diagonal)
					+ " smallestArea=" + smallest + " smallest/diag^2=" + smallestRatio + " trisWithRatio<=1e-9="
					+ belowRel1e9);
		}
	}

	private static double diagonal(double[] vertices) {
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE,
				minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
		for (int i = 0; i + 2 < vertices.length; i += 3) {
			minX = Math.min(minX, vertices[i]);
			maxX = Math.max(maxX, vertices[i]);
			minY = Math.min(minY, vertices[i + 1]);
			maxY = Math.max(maxY, vertices[i + 1]);
			minZ = Math.min(minZ, vertices[i + 2]);
			maxZ = Math.max(maxZ, vertices[i + 2]);
		}
		return Math.sqrt(Math.pow(maxX - minX, 2) + Math.pow(maxY - minY, 2) + Math.pow(maxZ - minZ, 2));
	}
}
