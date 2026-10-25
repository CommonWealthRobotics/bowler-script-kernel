package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Scans many shapes for lost surface: the writer's net loop area and the
 * read-back BRep area against the source mesh's own area, plus hole counts.
 */
public class DiagScanTest {

	@Test
	public void diag() throws Throwable {
		List<CSG> shapes = new ArrayList<>();
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 20, 32).toCSG().move(0, 0, -10)));
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(8, 20, 4).toCSG().move(0, 0, -10)));
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG()));
		// two holes
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 16).toCSG().move(-10, 0, -10))
				.difference(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 16).toCSG().move(10, 0, -10)));
		File hand = new File("testStepFiles", "Pointer_Hand_star.3mf");
		if (hand.isFile())
			shapes.add(CSG.fromThreeMF(hand.toPath()).get(0));
		File troll = new File("testStepFiles", "Charming_Troll_Group_of_3.3mf");
		if (troll.isFile())
			shapes.add(CSG.fromThreeMF(troll.toPath()).get(0));

		for (int i = 0; i < shapes.size(); i++) {
			CSG shape = shapes.get(i);
			File written = File.createTempFile("scan", ".step");
			written.deleteOnExit();
			new STEPFile(shape).write(written);

			List<StepFace> writerFaces = StepBRepWrite.faces(shape);
			double writerArea = 0;
			int writerHoles = 0;
			for (StepFace face : writerFaces) {
				double net = Math.abs(DiagHolesTest.projected(face.loops.get(0), face.normal)) / 2;
				if (face.loops.size() > 1)
					writerHoles++;
				for (int h = 1; h < face.loops.size(); h++)
					net -= Math.abs(DiagHolesTest.projected(face.loops.get(h), face.normal)) / 2;
				writerArea += net;
			}

			List<StepFace> readFaces;
			try (FileInputStream in = new FileInputStream(written)) {
				readFaces = new STEPFile(in, false).getShapes().get(0).getFaces();
			}
			double readArea = 0;
			int readHoles = 0;
			for (StepFace face : readFaces) {
				double net = Math.abs(DiagHolesTest.projected(face.loops.get(0), face.normal)) / 2;
				if (face.loops.size() > 1)
					readHoles++;
				for (int h = 1; h < face.loops.size(); h++)
					net -= Math.abs(DiagHolesTest.projected(face.loops.get(h), face.normal)) / 2;
				readArea += net;
			}
			double sourceArea = DiagHolesTest.area(shape);
			System.out.println("DIAG shape " + i + " tris=" + shape.getTriCount() + " sourceArea=" + trim(sourceArea)
					+ " writerArea=" + trim(writerArea) + " readArea=" + trim(readArea) + " writerHoles=" + writerHoles
					+ " readHoles=" + readHoles + " writerDelta=" + trim(writerArea - sourceArea) + " readDelta="
					+ trim(readArea - sourceArea));
		}
	}

	private static String trim(double value) {
		return String.format("%.4f", value);
	}
}
