package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/**
 * A plate with a through hole: its top and bottom faces are annuli. Reports the
 * writer's faces by area and loop count, so a lost hole (a face that should
 * carry an inner loop but does not) shows up.
 */
public class DiagHolesTest {

	@Test
	public void diag() throws Throwable {
		CSG plate = new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 8, 32).toCSG());
		System.out.println("DIAG plate sourceTris=" + plate.getTriCount() + " area=" + area(plate));

		List<StepFace> faces = StepBRepWrite.faces(plate);
		double total = 0;
		int multiLoop = 0;
		List<String> sorted = new ArrayList<>();
		for (StepFace face : faces) {
			double net = Math.abs(projected(face.loops.get(0), face.normal)) / 2;
			if (face.loops.size() > 1)
				multiLoop++;
			for (int i = 1; i < face.loops.size(); i++)
				net -= Math.abs(projected(face.loops.get(i), face.normal)) / 2;
			total += net;
			StringBuilder sizes = new StringBuilder();
			for (List<double[]> loop : face.loops)
				sizes.append(loop.size()).append(' ');
			sorted.add("net=" + trim(net) + " loops=[" + sizes + "] normal=(" + trim(face.normal.x) + ","
					+ trim(face.normal.y) + "," + trim(face.normal.z) + ") origin=(" + trim(face.origin[0]) + ","
					+ trim(face.origin[1]) + "," + trim(face.origin[2]) + ")");
		}
		java.util.Collections.sort(sorted);
		System.out.println("DIAG plate writerFaces=" + faces.size() + " multiLoop=" + multiLoop + " totalNetArea="
				+ trim(total) + " (source=" + trim(area(plate)) + ")");
		for (String line : sorted)
			System.out.println("DIAG face " + line);

		// the annulus faces specifically: two of them, z = +/- 3
		System.out.println("DIAG expected two annuli with areas 1600 - pi*64 = " + trim(1600 - Math.PI * 64));
	}

	private static String trim(double value) {
		return String.format("%.4f", value);
	}

	static double projected(List<double[]> loop, Vector3d normal) {
		Vector3d candidate = Math.abs(normal.x) < 0.9 ? new Vector3d(1, 0, 0) : new Vector3d(0, 1, 0);
		Vector3d u = normal.cross(candidate).normalized();
		Vector3d v = normal.cross(u);
		double sum = 0;
		for (int i = 0; i < loop.size(); i++) {
			double[] a = loop.get(i), b = loop.get((i + 1) % loop.size());
			sum += (a[0] * u.x + a[1] * u.y + a[2] * u.z) * (b[0] * v.x + b[1] * v.y + b[2] * v.z)
					- (b[0] * u.x + b[1] * u.y + b[2] * u.z) * (a[0] * v.x + a[1] * v.y + a[2] * v.z);
		}
		return sum;
	}

	static double area(CSG csg) {
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
