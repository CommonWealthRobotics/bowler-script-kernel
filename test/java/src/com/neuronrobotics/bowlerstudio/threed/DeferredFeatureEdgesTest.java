package com.neuronrobotics.bowlerstudio.threed;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;

public class DeferredFeatureEdgesTest {
	@Test
	public void invalidTopologyIsNotTraversedUntilEdgesAreRequested() {
		CSG source = new Cube(10).toCSG();
		source.getTriangles()[0] = source.getVertices().length;
		DeferredFeatureEdges deferred = new DeferredFeatureEdges(source, 35, 0, .002);
		try {
			deferred.get();
			fail("Extraction must reject the invalid index");
		} catch (IndexOutOfBoundsException expected) {
			// Snapshot construction does not traverse topology; extraction does.
		}
	}

	@Test
	public void snapshotSurvivesSourceMutationAndCachesResult() {
		CSG source = new Cube(10).toCSG();
		for (double angle : new double[]{0, 35, 180}) {
			for (double repair : new double[]{0, 1e-5}) {
				List<FeatureEdgeExtractor.Edge> expected = FeatureEdgeExtractor.extract(source, angle, repair, .002);
				DeferredFeatureEdges deferred = new DeferredFeatureEdges(source, angle, repair, .002);
				double[] positions = source.getVertices();
				long[] triangles = source.getTriangles();
				double first = positions[0];
				long index = triangles[0];
				positions[0] = 999;
				triangles[0] = triangles[1];
				try {
					List<FeatureEdgeExtractor.Edge> actual = deferred.get();
					assertEdges(expected, actual);
					assertSame(actual, deferred.get());
				} finally {
					positions[0] = first;
					triangles[0] = index;
				}
			}
		}
	}

	@Test
	public void requestingEdgesDoesNotChangeExportedGeometry() {
		STEPFile step = new STEPFile(new Cube(10).toCSG());
		String before = step.toStep();
		assertEdges(FeatureEdgeExtractor.extract(step.getCsg(), STEPFile.DEFAULT_ANGLE_DEGREES,
				STEPFile.DEFAULT_TOPOLOGY_TOLERANCE, STEPFile.DEFAULT_STITCH_TOLERANCE), step.getEdges());
		String after = step.toStep();
		assertEquals(before.substring(before.indexOf("DATA;")), after.substring(after.indexOf("DATA;")));
		assertSame(step.getEdges(), step.getEdges());
	}

	private static void assertEdges(List<FeatureEdgeExtractor.Edge> expected, List<FeatureEdgeExtractor.Edge> actual) {
		assertEquals(expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++) {
			assertEquals(expected.get(i).a, actual.get(i).a);
			assertEquals(expected.get(i).b, actual.get(i).b);
		}
	}
}
