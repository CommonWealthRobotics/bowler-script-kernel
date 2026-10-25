package com.neuronrobotics.bowlerstudio.threed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/**
 * A plate with a true through hole: both flat caps are annuli, so the writer
 * must emit two faces that each carry a hole. Reports what it derives.
 */
public class DiagGroupTest {

	@SuppressWarnings("unchecked")
	@Test
	public void diag() throws Throwable {
		CSG plate = new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 20, 32).toCSG().move(0, 0, -10));
		System.out.println("DIAG plate tris=" + plate.getTriCount() + " area=" + trim(area(plate))
				+ " expectedArea=" + trim(2 * 40 * 40 + 4 * 40 * 6 - 2 * Math.PI * 64 + 2 * Math.PI * 8 * 6));

		List<StepFace> faces = StepBRepWrite.faces(plate);
		double total = 0;
		int multiLoop = 0;
		for (StepFace face : faces) {
			double net = Math.abs(DiagHolesTest.projected(face.loops.get(0), face.normal)) / 2;
			if (face.loops.size() > 1) {
				multiLoop++;
				StringBuilder sizes = new StringBuilder();
				for (List<double[]> loop : face.loops)
					sizes.append(loop.size()).append('/');
				System.out.println("DIAG holedFace normal=(" + trim(face.normal.x) + "," + trim(face.normal.y) + ","
						+ trim(face.normal.z) + ") loops=" + sizes);
			}
			for (int i = 1; i < face.loops.size(); i++)
				net -= Math.abs(DiagHolesTest.projected(face.loops.get(i), face.normal)) / 2;
			total += net;
		}
		System.out.println("DIAG writerFaces=" + faces.size() + " multiLoop=" + multiLoop + " total=" + trim(total)
				+ " source=" + trim(area(plate)));
	}

	private static String trim(double value) {
		return String.format("%.4f", value);
	}

	private static double area(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		double total = 0;
		for (int i = 0; i + 2 < triangles.length; i += 3) {
			int a = (int) triangles[i] * 3, b = (int) triangles[i + 1] * 3, c = (int) triangles[i + 2] * 3;
			double[] pa = {vertices[a], vertices[a + 1], vertices[a + 2]};
			double[] pb = {vertices[b], vertices[b + 1], vertices[b + 2]};
			double[] pc = {vertices[c], vertices[c + 1], vertices[c + 2]};
			double ux = pb[0] - pa[0], uy = pb[1] - pa[1], uz = pb[2] - pa[2];
			double vx = pc[0] - pa[0], vy = pc[1] - pa[1], vz = pc[2] - pa[2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			total += Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
		}
		return total;
	}
}
