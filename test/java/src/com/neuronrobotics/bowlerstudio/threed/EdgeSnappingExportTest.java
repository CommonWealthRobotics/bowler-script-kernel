package com.neuronrobotics.bowlerstudio.threed;

import java.util.List;

import java.util.ArrayList;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import javafx.scene.paint.Color;

public class EdgeSnappingExportTest {
	private static CSG seam(double scale) {
		double[] points = {0, 0, 0, 1, 0, 0, 1, 1, 0, 0.0000005, 0, 0, 0, 1, 0};
		for (int i = 0; i < points.length; i++)
			points[i] *= scale;
		return new CSG(points, new long[]{0, 1, 2, 3, 2, 4, 0, 3, 1}, Color.GRAY);
	}

	@Test
	public void defaultAndExplicitPrimaryHaveIdenticalGeometry() {
		CSG cube = new Cube(10).toCSG();
		STEPFile implicit = new STEPFile(cube);
		STEPFile explicit = new STEPFile(cube, STEPFile.ExportMode.PRIMARY);
		assertArrayEquals(implicit.getCsg().getVertices(), explicit.getCsg().getVertices(), 0);
		assertArrayEquals(implicit.getCsg().getTriangles(), explicit.getCsg().getTriangles());
		assertEquals(implicit.toStep().split("DATA;", 2)[1], explicit.toStep().split("DATA;", 2)[1]);
	}

	@Test
	public void snappingDropsCollapsedTrianglesWithoutChangingSource() {
		CSG source = seam(1);
		double[] points = source.getVertices().clone();
		long[] triangles = source.getTriangles().clone();
		CSG snapped = EdgeSnappingMesh.prepare(source);
		assertEquals(12, snapped.getVertices().length);
		assertEquals(6, snapped.getTriangles().length);
		assertEquals(snapped.getTriangles()[0], snapped.getTriangles()[3]);
		assertArrayEquals(points, source.getVertices(), 0);
		assertArrayEquals(triangles, source.getTriangles());
	}

	@Test
	public void tinyGeometryUsesRelativeNotAbsoluteTolerance() {
		CSG snapped = EdgeSnappingMesh.prepare(seam(1e-8));
		assertEquals(12, snapped.getVertices().length);
		assertEquals(6, snapped.getTriangles().length);
		assertEquals(1e-8, Arrays.stream(snapped.getVertices()).max().getAsDouble(), 0);
	}

	@Test
	public void translatedGeometryHasTheSameSnap() {
		CSG source = seam(1);
		for (int i = 0; i < source.getVertices().length; i++)
			source.getVertices()[i] -= 1000;
		assertArrayEquals(EdgeSnappingMesh.prepare(seam(1)).getTriangles(),
				EdgeSnappingMesh.prepare(source).getTriangles());
	}

	@Test
	public void compatibilityFacesAndCurvesUseTheSameSnappedGeometry() {
		CSG cube = new Cube(1).toCSG();
		double[] vertices = Arrays.copyOf(cube.getVertices(), cube.getVertices().length + 3);
		long[] triangles = cube.getTriangles().clone();
		int original = (int) triangles[0], duplicate = cube.getVertices().length / 3;
		System.arraycopy(vertices, 3 * original, vertices, 3 * duplicate, 3);
		vertices[3 * duplicate] += 5e-7;
		triangles[0] = duplicate;
		CSG cracked = new CSG(vertices, triangles, Color.GRAY);
		STEPFile export = new STEPFile(cracked, STEPFile.ExportMode.EDGE_SNAPPING);
		assertEquals(6, export.getFaces().size());
		String text = export.toStep();
		Map<Integer, String> entities = new HashMap<>();
		Matcher entity = Pattern.compile("#(\\d+)=(.*?);", Pattern.DOTALL).matcher(text);
		while (entity.find())
			entities.put(Integer.parseInt(entity.group(1)), entity.group(2));
		int count = 0;
		for (String value : entities.values()) {
			if (!value.startsWith("EDGE_CURVE("))
				continue;
			int[] edge = refs(value);
			double[] start = coordinates(entities.get(refs(entities.get(edge[0]))[0]));
			double[] end = coordinates(entities.get(refs(entities.get(edge[1]))[0]));
			assertFalse(Arrays.equals(start, end));
			int[] line = refs(entities.get(edge[2]));
			assertArrayEquals(start, coordinates(entities.get(line[0])), 0);
			String vector = entities.get(line[1]);
			double[] direction = coordinates(entities.get(refs(vector)[0]));
			double length = Double.parseDouble(vector.substring(vector.lastIndexOf(',') + 1, vector.length() - 1));
			assertTrue(length > 0);
			for (int axis = 0; axis < 3; axis++)
				assertEquals(end[axis], start[axis] + length * direction[axis], 1e-14);
			count++;
		}
		assertEquals(12, count);
		for (StepFace face : export.getFaces())
			for (List<double[]> loop : face.loops)
				for (double[] point : loop)
					for (double coordinate : point)
						assertEquals(0.5, Math.abs(coordinate), 0);
	}

	@Test
	public void proximityClustersAreTransitiveAndKeepLowestIndex() {
		CSG mesh = new CSG(new double[]{0, 0, 0, 1e-6, 0, 0, 2e-6, 0, 0, 1, 0, 0, 0, 1, 0},
				new long[]{0, 3, 4, 1, 3, 4, 2, 3, 4}, Color.GRAY);
		CSG snapped = EdgeSnappingMesh.prepare(mesh);
		assertEquals(9, snapped.getVertices().length);
		assertArrayEquals(new long[]{0, 1, 2, 0, 1, 2, 0, 1, 2}, snapped.getTriangles());
		assertEquals(0, snapped.getVertices()[0], 0);
	}

	@Test
	public void pointsOutsideToleranceRemainDistinct() {
		CSG mesh = seam(1);
		mesh.getVertices()[9] = 2e-6;
		CSG snapped = EdgeSnappingMesh.prepare(mesh);
		assertEquals(15, snapped.getVertices().length);
		assertEquals(9, snapped.getTriangles().length);
	}

	@Test
	public void validSolidStillHasClosedShellInCompatibilityMode() {
		STEPFile export = new STEPFile(new Cube(10).toCSG(), STEPFile.ExportMode.EDGE_SNAPPING);
		assertEquals(6, export.getFaces().size());
		assertTrue(export.toStep().contains("CLOSED_SHELL"));
		assertFalse(export.toStep().contains("OPEN_SHELL"));
	}

	@Test
	public void nativeCleanupRemovesOpposingCollinearFansAfterSnapping() {
		CSG cube = new Cube(1).toCSG();
		int count = cube.getVertices().length / 3;
		double[] points = Arrays.copyOf(cube.getVertices(), cube.getVertices().length + 9);
		System.arraycopy(new double[]{0.123, -0.456, 0, 0.123, -0.456, 0.4, 0.123, -0.456, 0.2},
				0, points, count * 3, 9);
		long[] triangles = Arrays.copyOf(cube.getTriangles(), cube.getTriangles().length + 6);
		System.arraycopy(new long[]{count, count + 1, count + 2, count, count + 2, count + 1},
				0, triangles, cube.getTriangles().length, 6);
		STEPFile export = new STEPFile(new CSG(points, triangles, Color.GRAY), STEPFile.ExportMode.EDGE_SNAPPING);
		assertEquals(12L, export.getCsg().getTriCount());
		assertEquals(1, export.getCsg().getVolume(), 1e-12);
		assertEquals(6, export.getFaces().size());
	}

	@Test(expected = IllegalArgumentException.class)
	public void zeroExtentFailsVisibly() {
		EdgeSnappingMesh.prepare(new CSG(new double[9], new long[]{0, 1, 2}, Color.GRAY));
	}

	@Test(expected = IllegalArgumentException.class)
	public void nonFiniteCoordinateFailsVisibly() {
		CSG mesh = seam(1);
		mesh.getVertices()[0] = Double.NaN;
		EdgeSnappingMesh.prepare(mesh);
	}

	@Test(expected = IllegalStateException.class)
	public void fullyCollapsedMeshFailsVisibly() {
		EdgeSnappingMesh.prepare(
				new CSG(new double[]{0, 0, 0, 1e-8, 0, 0, 0, 1e-8, 0, 10, 0, 0}, new long[]{0, 1, 2}, Color.GRAY));
	}

	private static int[] refs(String entity) {
		Matcher matcher = Pattern.compile("#(\\d+)").matcher(entity);
		List<Integer> references = new ArrayList<>();
		while (matcher.find())
			references.add(Integer.parseInt(matcher.group(1)));
		return references.stream().mapToInt(Integer::intValue).toArray();
	}

	private static double[] coordinates(String entity) {
		String tuple = entity.substring(entity.lastIndexOf('(') + 1, entity.indexOf(')'));
		return Arrays.stream(tuple.split(",")).mapToDouble(Double::parseDouble).toArray();
	}
}
