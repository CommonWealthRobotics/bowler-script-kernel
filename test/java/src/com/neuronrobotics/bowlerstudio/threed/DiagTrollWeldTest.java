package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.CSG.OptType;

/** What welding at the file's resolution does to the troll mesh. */
public class DiagTrollWeldTest {
	@Test
	public void diag() throws Throwable {
		CSG.setDefaultOptType(OptType.Manifold3d);
		CSG csg = CSG.fromThreeMF(new File("testStepFiles", "Charming_Troll_Group_of_3.3mf").toPath()).get(0);
		double[] v = csg.getVertices();
		long[] t = csg.getTriangles();
		double T = 1e-6;
		int n = v.length / 3;
		int[] rep = new int[n];
		for (int i = 0; i < n; i++)
			rep[i] = i;
		List<double[]> pts = new ArrayList<>();
		for (int i = 0; i < n; i++)
			pts.add(new double[]{v[i * 3], v[i * 3 + 1], v[i * 3 + 2]});
		int merged = 0;
		for (int i = 0; i < n; i++)
			for (int j = i + 1; j < n; j++) {
				if (rep[j] != j)
					continue;
				double dx = pts.get(i)[0] - pts.get(j)[0], dy = pts.get(i)[1] - pts.get(j)[1],
						dz = pts.get(i)[2] - pts.get(j)[2];
				if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= T) {
					rep[j] = i;
					merged++;
				}
			}
		int collapsed = 0;
		double lost = 0;
		List<Integer> kept = new ArrayList<>();
		for (int i = 0; i + 2 < t.length; i += 3) {
			int a = rep[(int) t[i]], b = rep[(int) t[i + 1]], c = rep[(int) t[i + 2]];
			if (a == b || b == c || c == a) {
				collapsed++;
				lost += area(pts.get((int) t[i]), pts.get((int) t[i + 1]), pts.get((int) t[i + 2]));
				continue;
			}
			kept.add(i);
		}
		System.out.println("DIAGTROLLWELD merged=" + merged + " collapsed=" + collapsed + " kept=" + (kept.size())
				+ " lostArea=" + lost);
	}

	private static double area(double[] a, double[] b, double[] c) {
		double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		double vx = c[0] - a[0], vy = c[1] - a[1], vz = c[2] - a[2];
		double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
		return Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
	}
}
