package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * A plate with a hole that contains an island: the cap face is an annulus plus a
 * separate disc inside its hole. Writing the disc as a second bound subtracts it
 * instead of adding it, so the island disappears. Reports what the writer does.
 */
public class DiagIslandTest {

	@Test
	public void diag() throws Throwable {
		// frame: 60x60x6 plate, hole of radius 12, island disc of radius 6 in the hole
		CSG plate = new eu.mihosoft.vrl.v3d.Cube(60, 60, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(12, 20, 32).toCSG().move(0, 0, -10))
				.union(new eu.mihosoft.vrl.v3d.Cylinder(6, 8, 24).toCSG());
		System.out.println("DIAG island source tris=" + plate.getTriCount());
		report("islandPlate", plate);

		// simpler: single face with a hole containing an island, built by hand
		CSG nested = new eu.mihosoft.vrl.v3d.Cube(40, 40, 2).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(12, 10, 24).toCSG().move(0, 0, -5))
				.union(new eu.mihosoft.vrl.v3d.Cylinder(5, 4, 16).toCSG());
		report("nestedIsland", nested);
	}

	private static void report(String label, CSG source) {
		List<StepFace> faces = StepBRepWrite.faces(source);
		double total = 0;
		int zeroArea = 0, multi = 0;
		List<String> detail = new ArrayList<>();
		for (StepFace face : faces) {
			double net = Math.abs(DiagHolesTest.projected(face.loops.get(0), face.normal)) / 2;
			if (face.loops.size() > 1) {
				multi++;
				StringBuilder sizes = new StringBuilder();
				for (List<double[]> loop : face.loops)
					sizes.append(loop.size()).append('/');
				detail.add("multi face normal=(" + trim(face.normal.x) + "," + trim(face.normal.y) + ","
						+ trim(face.normal.z) + ") loops=" + sizes + " netArea=" + trim(net));
			}
			for (int i = 1; i < face.loops.size(); i++)
				net -= Math.abs(DiagHolesTest.projected(face.loops.get(i), face.normal)) / 2;
			total += net;
			if (Math.abs(net) < 1e-9)
				zeroArea++;
		}
		System.out.println("DIAG " + label + " faces=" + faces.size() + " multiLoop=" + multi + " zeroArea=" + zeroArea
				+ " writerArea=" + trim(total) + " sourceArea=" + trim(area(source)));
		for (String line : detail)
			System.out.println("DIAG   " + line);
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
