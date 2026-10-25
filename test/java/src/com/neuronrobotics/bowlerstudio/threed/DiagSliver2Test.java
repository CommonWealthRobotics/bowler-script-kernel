package com.neuronrobotics.bowlerstudio.threed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/** Reports the exact coordinates of the degenerate mesh triangle. */
public class DiagSliver2Test {

	@SuppressWarnings("unchecked")
	@Test
	public void diag() throws Throwable {
		CSG plate = new eu.mihosoft.vrl.v3d.Cube(40, 40, 6).toCSG()
				.difference(new eu.mihosoft.vrl.v3d.Cube(10, 10, 20).toCSG());
		Class<?> meshClass = Class.forName("com.neuronrobotics.bowlerstudio.threed.StepBRepWrite$Mesh");
		Constructor<?> constructor = meshClass.getDeclaredConstructor(double[].class, long[].class);
		constructor.setAccessible(true);
		Object mesh = constructor.newInstance(plate.getVertices(), plate.getTriangles());
		Field tf = meshClass.getDeclaredField("triangles");
		tf.setAccessible(true);
		List<int[]> triangles = (List<int[]>) tf.get(mesh);
		Method point = meshClass.getDeclaredMethod("point", int.class);
		point.setAccessible(true);

		int[] tri = triangles.get(13);
		System.out.println("DIAG tri13 = " + tri[0] + "," + tri[1] + "," + tri[2]);
		for (int corner = 0; corner < 3; corner++) {
			double[] p = (double[]) point.invoke(mesh, tri[corner]);
			System.out.println("DIAG   (" + p[0] + "," + p[1] + "," + p[2] + ")");
		}
		System.out.println("DIAG source vertex 14/30/9 coordinates:");
		double[] v = plate.getVertices();
		for (int vertex : new int[]{14, 30, 9}) {
			System.out.println("DIAG   src " + vertex + " = (" + v[vertex * 3] + "," + v[vertex * 3 + 1] + ","
					+ v[vertex * 3 + 2] + ")");
		}

		// is the source triangle itself degenerate?
		long[] t = plate.getTriangles();
		for (int i = 0; i + 2 < t.length; i += 3) {
			if (t[i] == 14 && t[i + 1] == 30 && t[i + 2] == 9)
				System.out.println("DIAG source has triangle 14,30,9 at index " + (i / 3));
		}
		// the mesh's own vertex count
		Field pf = meshClass.getDeclaredField("points");
		pf.setAccessible(true);
		double[] points = (double[]) pf.get(mesh);
		System.out.println("DIAG mesh points=" + points.length / 3 + " source vertices=" + plate.getVertCount());
	}
}
