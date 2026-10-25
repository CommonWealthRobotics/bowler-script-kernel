package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/** Finds the sliver that becomes a zero-area face, and its neighbours. */
public class DiagSliverTest {

	@Test
	public void diag() throws Throwable {
		CSG plate = new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG());
		double[] v = plate.getVertices();
		long[] t = plate.getTriangles();
		System.out.println("DIAG source tris=" + plate.getTriCount());
		for (int i = 0; i + 2 < t.length; i += 3) {
			int a = (int) t[i] * 3, b = (int) t[i + 1] * 3, c = (int) t[i + 2] * 3;
			double ux = v[b] - v[a], uy = v[b + 1] - v[a + 1], uz = v[b + 2] - v[a + 2];
			double wx = v[c] - v[a], wy = v[c + 1] - v[a + 1], wz = v[c + 2] - v[a + 2];
			double nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
			double area = Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
			if (area <= 1e-9) {
				System.out.println("DIAG zeroArea tri " + (i / 3) + " area=" + area + " verts " + t[i] + "," + t[i + 1]
						+ "," + t[i + 2]);
				System.out.println("DIAG   a=(" + v[a] + "," + v[a + 1] + "," + v[a + 2] + ")");
				System.out.println("DIAG   b=(" + v[b] + "," + v[b + 1] + "," + v[b + 2] + ")");
				System.out.println("DIAG   c=(" + v[c] + "," + v[c + 1] + "," + v[c + 2] + ")");
			}
		}

		// the writer's faces with zero net area
		for (StepFace face : StepBRepWrite.faces(plate)) {
			double net = Math.abs(DiagHolesTest.projected(face.loops.get(0), face.normal)) / 2;
			for (int i = 1; i < face.loops.size(); i++)
				net -= Math.abs(DiagHolesTest.projected(face.loops.get(i), face.normal)) / 2;
			if (Math.abs(net) < 1e-9) {
				System.out.println("DIAG zeroAreaFace normal=(" + face.normal.x + "," + face.normal.y + ","
						+ face.normal.z + ")");
				for (List<double[]> loop : face.loops)
					for (double[] p : loop)
						System.out.println("DIAG   loop point (" + p[0] + "," + p[1] + "," + p[2] + ")");
			}
		}
	}
}
