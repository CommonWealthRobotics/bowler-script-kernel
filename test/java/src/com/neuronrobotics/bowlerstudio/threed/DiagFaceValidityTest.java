package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/**
 * Checks every written loop against what a STEP wire must be: at least three
 * distinct points, no zero-length edge, no point revisited within one loop, and
 * no three consecutive points collinear (a spike a kernel will not triangulate).
 */
public class DiagFaceValidityTest {

	@Test
	public void diag() throws Throwable {
		List<CSG> shapes = new ArrayList<>();
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(10).toCSG());
		shapes.add(new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG()));
		shapes.add(new eu.mihosoft.vrl.v3d.Cylinder(5, 20, 32).toCSG());
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
		int repeatedVertex = 0, zeroLength = 0, collinear = 0, shortLoop = 0, collinearAll = 0;
		int facesAffected = 0;
		List<String> examples = new ArrayList<>();
		for (StepFace face : faces) {
			boolean bad = false;
			for (List<double[]> loop : face.loops) {
				if (loop.size() < 3) {
					shortLoop++;
					bad = true;
					continue;
				}
				Set<String> seen = new HashSet<>();
				for (int i = 0; i < loop.size(); i++) {
					double[] p = loop.get(i);
					if (!seen.add(key(p))) {
						repeatedVertex++;
						bad = true;
						if (examples.size() < 6)
							examples.add("repeated vertex in loop of " + loop.size());
					}
					double[] q = loop.get((i + 1) % loop.size());
					if (dist(p, q) == 0) {
						zeroLength++;
						bad = true;
					}
				}
				for (int i = 0; i < loop.size(); i++) {
					double[] a = loop.get(i);
					double[] b = loop.get((i + 1) % loop.size());
					double[] c = loop.get((i + 2) % loop.size());
					if (isCollinear(a, b, c, face.normal)) {
						collinear++;
						bad = true;
						if (examples.size() < 6)
							examples.add("collinear at " + loop.size() + "-point loop, normal=(" + trim(face.normal.x)
									+ "," + trim(face.normal.y) + "," + trim(face.normal.z) + ")");
					}
				}
				if (allCollinear(loop, face.normal)) {
					collinearAll++;
					if (examples.size() < 6)
						examples.add("WHOLE LOOP collinear (" + loop.size() + " points)");
				}
			}
			if (bad)
				facesAffected++;
		}
		System.out.println("DIAG " + label + " faces=" + faces.size() + " facesAffected=" + facesAffected
				+ " repeatedVertex=" + repeatedVertex + " zeroLengthEdges=" + zeroLength + " collinearTriples="
				+ collinear + " wholeLoopCollinear=" + collinearAll + " shortLoops=" + shortLoop);
		for (String example : examples)
			System.out.println("DIAG   " + example);
	}

	private static boolean allCollinear(List<double[]> loop, Vector3d normal) {
		for (int i = 0; i < loop.size(); i++)
			if (!isCollinear(loop.get(i), loop.get((i + 1) % loop.size()), loop.get((i + 2) % loop.size()), normal))
				return false;
		return true;
	}

	private static boolean isCollinear(double[] a, double[] b, double[] c, Vector3d normal) {
		double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
		double vx = c[0] - b[0], vy = c[1] - b[1], vz = c[2] - b[2];
		double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
		double magnitude = Math.sqrt(cx * cx + cy * cy + cz * cz);
		double scale = Math.max(1,
				Math.max(Math.sqrt(ux * ux + uy * uy + uz * uz), Math.sqrt(vx * vx + vy * vy + vz * vz)));
		return magnitude <= 1e-9 * scale * scale;
	}

	private static double dist(double[] a, double[] b) {
		return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
	}

	private static String key(double[] p) {
		return Double.toString(p[0]) + "," + Double.toString(p[1]) + "," + Double.toString(p[2]);
	}

	private static String trim(double value) {
		return String.format("%.4f", value);
	}
}
