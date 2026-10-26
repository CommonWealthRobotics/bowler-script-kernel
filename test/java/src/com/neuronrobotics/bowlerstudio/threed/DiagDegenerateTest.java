package com.neuronrobotics.bowlerstudio.threed;

import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * A plate with a square through hole. Reports the writer's faces that enclose no
 * area, and how many of the source's triangles have no area.
 */
public class DiagDegenerateTest {

	@Test
	public void diag() throws Throwable {
		CSG plate = new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG());
		System.out.println("DIAG source tris=" + plate.getTriCount() + " verts=" + plate.getVertCount());

		// zero-area triangles in the source
		double[] vertices = plate.getVertices();
		long[] triangles = plate.getTriangles();
		int zeroArea = 0;
		for (int t = 0; t + 2 < triangles.length; t += 3) {
			int a = (int) triangles[t] * 3, b = (int) triangles[t + 1] * 3, c = (int) triangles[t + 2] * 3;
			double ux = vertices[b] - vertices[a], uy = vertices[b + 1] - vertices[a + 1],
					uz = vertices[b + 2] - vertices[a + 2];
			double vx = vertices[c] - vertices[a], vy = vertices[c + 1] - vertices[a + 1],
					vz = vertices[c + 2] - vertices[a + 2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			if (Math.sqrt(nx * nx + ny * ny + nz * nz) / 2 < 1e-12)
				zeroArea++;
		}
		System.out.println("DIAG source zero-area triangles=" + zeroArea);

		List<StepFace> faces = StepBRepWrite.faces(plate);
		int noArea = 0;
		int multiLoop = 0;
		for (StepFace face : faces) {
			double net = Math.abs(DiagHolesTest.projected(face.loops.get(0), face.normal)) / 2;
			if (face.loops.size() > 1)
				multiLoop++;
			for (int i = 1; i < face.loops.size(); i++)
				net -= Math.abs(DiagHolesTest.projected(face.loops.get(i), face.normal)) / 2;
			if (Math.abs(net) < 1e-9) {
				noArea++;
				if (noArea <= 8) {
					StringBuilder loopSizes = new StringBuilder();
					for (List<double[]> loop : face.loops)
						loopSizes.append(loop.size()).append('/');
					System.out.println("DIAG zeroAreaFace loops=" + loopSizes + " normal=(" + face.normal.x + ","
							+ face.normal.y + "," + face.normal.z + ") origin=(" + face.origin[0] + "," + face.origin[1]
							+ "," + face.origin[2] + ")");
					for (List<double[]> loop : face.loops)
						for (double[] p : loop)
							System.out.println("DIAG   point (" + p[0] + "," + p[1] + "," + p[2] + ")");
				}
			}
		}
		System.out.println("DIAG writerFaces=" + faces.size() + " multiLoop=" + multiLoop + " zeroAreaFaces=" + noArea);
	}

	private static double area(CSG csg) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		double total = 0;
		for (int i = 0; i + 2 < triangles.length; i += 3) {
			int a = (int) triangles[i] * 3, b = (int) triangles[i + 1] * 3, c = (int) triangles[i + 2] * 3;
			double ux = vertices[b] - vertices[a], uy = vertices[b + 1] - vertices[a + 1],
					uz = vertices[b + 2] - vertices[a + 2];
			double vx = vertices[c] - vertices[a], vy = vertices[c + 1] - vertices[a + 1],
					vz = vertices[c + 2] - vertices[a + 2];
			double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
			total += Math.sqrt(nx * nx + ny * ny + nz * nz) / 2;
		}
		return total;
	}
}
