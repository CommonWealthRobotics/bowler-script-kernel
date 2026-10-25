package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Writes a through-holed plate, reads it back preferring the BRep, and reports
 * whether the hole survives: the read mesh's area and volume against the source.
 */
public class DiagHoleRoundTripTest {

	@Test
	public void diag() throws Throwable {
		List<CSG> shapes = new ArrayList<>();
		shapes.add(plateWithThroughHole());
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(10).toCSG());
		shapes.add(new eu.mihosoft.vrl.v3d.Sphere(10, 24, 12).toCSG());
		shapes.add(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 32).toCSG());
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(20, 20, 4).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Sphere(6, 24, 12).toCSG().move(10, 10, 2)));

		for (int i = 0; i < shapes.size(); i++) {
			CSG shape = shapes.get(i);
			File written = File.createTempFile("hole_rt", ".step");
			written.deleteOnExit();
			new STEPFile(shape).write(written);
			StepImport.Shape brepShape = null;
			try (FileInputStream in = new FileInputStream(written)) {
				brepShape = new STEPFile(in, false).getShapes().get(0);
			}
			List<StepFace> readFaces = brepShape.getFaces();
			int readWithHoles = 0;
			for (StepFace face : readFaces)
				if (face.loops.size() > 1)
					readWithHoles++;
			List<StepFace> writerFaces = StepBRepWrite.faces(shape);
			int writerWithHoles = 0;
			for (StepFace face : writerFaces)
				if (face.loops.size() > 1)
					writerWithHoles++;

			// the read-back BRep area against the source
			double sourceArea = area(shape);
			double readArea = 0;
			for (StepFace face : readFaces) {
				double net = Math.abs(DiagHolesTest.projected(face.loops.get(0), face.normal)) / 2;
				for (int h = 1; h < face.loops.size(); h++)
					net -= Math.abs(DiagHolesTest.projected(face.loops.get(h), face.normal)) / 2;
				readArea += net;
			}
			System.out.println("DIAG shape " + i + " writerHoles=" + writerWithHoles + " readFaces="
					+ readFaces.size() + " readHoles=" + readWithHoles + " sourceArea=" + trim(sourceArea) + " readArea="
					+ trim(readArea) + " delta=" + trim(readArea - sourceArea));
		}
	}

	private static CSG plateWithThroughHole() {
		return new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 20, 32).toCSG().move(0, 0, -10));
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
