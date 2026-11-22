package com.neuronrobotics.bowlerstudio.threed;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;

public class StepTessellationAssociationTest extends ManifoldExportTestSupport {
	@Test
	public void tessellationDoesNotAddShapeGeometry() throws Exception {
		String text = new STEPFile(new Cube(10).toCSG()).toStep();
		StepReader reader = StepReader.parse(stream(text));
		int associations = 0;
		for (StepReader.Instance instance : reader.instances().values()) {
			if (!StepReader.kindOf(instance, "REPRESENTATION_RELATIONSHIP"))
				continue;
			StepReader.Instance related = reader
					.instance(reader.attribute(instance, "REPRESENTATION_RELATIONSHIP", "rep_2"));
			if (!StepReader.kindOf(related, "TESSELLATED_SHAPE_REPRESENTATION"))
				continue;
			associations++;
			assertFalse("the exact mesh is auxiliary data, not additional shape geometry",
					StepReader.kindOf(instance, "SHAPE_REPRESENTATION_RELATIONSHIP"));
		}
		assertEquals(1, associations);
	}

	@Test
	public void exactMeshAndBrepRemainAlternativeRepresentations() throws Exception {
		assertRoundTrip(new STEPFile(new Cube(10).toCSG()));
	}

	@Test
	public void assemblyKeepsEachMeshAssociatedWithItsOwnPart() throws Exception {
		CSG first = new Cube(10).toCSG().setName("part");
		CSG second = new Cube(4).toCSG().movex(20).setName("part");
		assertRoundTrip(new STEPFile(Arrays.asList(first, second)));
	}

	private static void assertRoundTrip(STEPFile exported) throws Exception {
		String text = exported.toStep();
		List<CSG> expected = exported.getParts();
		List<CSG> exact = new STEPFile(stream(text), true).getImported();
		List<CSG> brep = new STEPFile(stream(text), false).getImported();
		assertEquals(expected.size(), exact.size());
		assertEquals(expected.size(), brep.size());
		for (int i = 0; i < expected.size(); i++) {
			assertArrayEquals(expected.get(i).getVertices(), exact.get(i).getVertices(), 0);
			assertArrayEquals(expected.get(i).getTriangles(), exact.get(i).getTriangles());
			assertEquals(expected.get(i).getVolume(), brep.get(i).getVolume(), 1e-7);
		}
	}

	private static ByteArrayInputStream stream(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}
}
