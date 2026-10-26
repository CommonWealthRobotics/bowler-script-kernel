package com.neuronrobotics.bowlerstudio.threed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/** Traces the writer's mesh for the square-hole plate. */
public class DiagMeshTest {

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
		Field ff = meshClass.getDeclaredField("faces");
		ff.setAccessible(true);
		List<int[]> triangles = (List<int[]>) tf.get(mesh);
		List<List<Integer>> groups = (List<List<Integer>>) ff.get(mesh);
		Method point = meshClass.getDeclaredMethod("point", int.class);
		point.setAccessible(true);
		Method normal = meshClass.getDeclaredMethod("normal", int[].class);
		normal.setAccessible(true);
		Method areaMethod = meshClass.getDeclaredMethod("area", List.class, Vector3d.class);
		areaMethod.setAccessible(true);
		Method asLoop = meshClass.getDeclaredMethod("asLoop", int[].class);
		asLoop.setAccessible(true);
		Method loops = meshClass.getDeclaredMethod("loops", List.class);
		loops.setAccessible(true);

		System.out.println("DIAG mesh triangles=" + triangles.size() + " groups=" + groups.size());
		int zero = 0;
		for (int i = 0; i < triangles.size(); i++) {
			Vector3d n = (Vector3d) normal.invoke(mesh, triangles.get(i));
			if (n == null) {
				zero++;
				int[] tri = triangles.get(i);
				System.out.println("DIAG zeroArea tri " + i + " = " + tri[0] + "," + tri[1] + "," + tri[2]);
				for (int c = 0; c < 3; c++) {
					double[] p = (double[]) point.invoke(mesh, tri[c]);
					System.out.println("DIAG   (" + p[0] + "," + p[1] + "," + p[2] + ")");
				}
			}
		}
		System.out.println("DIAG zeroArea triangles in mesh=" + zero);

		// the group whose loop is the collinear one at y=5,z=-3
		for (int g = 0; g < groups.size(); g++) {
			List<List<Integer>> derived = (List<List<Integer>>) loops.invoke(mesh, groups.get(g));
			for (List<Integer> loop : derived) {
				if (loop.size() != 3)
					continue;
				boolean all = true;
				for (int vertex : loop) {
					double[] p = (double[]) point.invoke(mesh, vertex);
					if (Math.abs(p[1] - 5.0) > 1e-6 || Math.abs(p[2] + 3.0) > 1e-6)
						all = false;
				}
				if (!all)
					continue;
				System.out.println("DIAG found collinear loop in group " + g + " loop=" + loop);
				for (int index : groups.get(g)) {
					int[] tri = triangles.get(index);
					Vector3d n = (Vector3d) normal.invoke(mesh, tri);
					double area = n == null
							? 0
							: Math.abs((Double) areaMethod.invoke(mesh, asLoop.invoke(null, tri), n)) / 2;
					System.out.println(
							"DIAG   tri " + index + " " + tri[0] + "," + tri[1] + "," + tri[2] + " area=" + area);
				}
			}
		}
	}
}
