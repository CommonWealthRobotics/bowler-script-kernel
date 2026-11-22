package junit.bowler;

import java.util.List;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.bowlerstudio.threed.StepBRepWrite;
import com.neuronrobotics.bowlerstudio.threed.StepFace;
import com.neuronrobotics.manifold3d.CSGManifold3d;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import javafx.scene.paint.Color;

public class StepSimplificationTest {
	@Test
	public void slightlyBentSheetIsNotWrittenOnAnIncompatiblePlane() {
		CSG bent = new CSG(new double[]{0, 0, 0, 10, 0, 0, 10, 10, 2e-6, 0, 10, 0}, new long[]{0, 1, 2, 0, 2, 3},
				Color.GRAY);
		for (StepFace face : StepBRepWrite.faces(bent))
			for (List<double[]> loop : face.loops)
				for (double[] point : loop) {
					double offset = (point[0] - face.origin[0]) * face.normal.x
							+ (point[1] - face.origin[1]) * face.normal.y + (point[2] - face.origin[2]) * face.normal.z;
					assertEquals("boundary must lie on the emitted plane", 0, offset, 1e-7);
				}
	}

	@Test
	public void resolvedThinWallKeepsDistinctEdgeEndpoints() throws Throwable {
		new Cube(10).toCSG().simplify();
		String step = new STEPFile(CSG.getManifold().cube(10, 10, 1e-5, true)).toStep();
		java.util.regex.Matcher edges = java.util.regex.Pattern.compile("EDGE_CURVE\\('.*?',#(\\d+),#(\\d+),")
				.matcher(step);
		int count = 0;
		while (edges.find()) {
			assertFalse("a straight edge must have distinct endpoints", edges.group(1).equals(edges.group(2)));
			count++;
		}
		assertTrue("the BRep must contain edges", count > 0);
	}

	@Test
	public void exportPrunesOnlyUnresolvableMembraneAndKeepsSmallSolid() throws Throwable {
		CSG body = new Cube(10).toCSG().simplify();
		CSG small = CSG.getManifold().cube(0.001, 0.001, 0.001, true).movex(20);
		CSG membrane = CSG.getManifold().cube(2, 2, 1e-8, true).movex(-20);
		CSGManifold3d nativeApi = CSG.getManifold();
		CSG source = nativeApi.booleanOp(nativeApi.booleanOp(body, small, CSGManifold3d.BooleanOp.UNION), membrane,
				CSGManifold3d.BooleanOp.UNION);
		double[] vertices = source.getVertices().clone();
		long[] triangles = source.getTriangles().clone();
		STEPFile export = new STEPFile(source);
		assertEquals(2, export.getParts().size());
		double volume = 0;
		for (CSG part : export.getParts())
			volume += nativeApi.volume(part);
		assertEquals(1000 + 1e-9, volume, 1e-10);
		String text = export.toStep();
		assertFalse(text.contains("OPEN_SHELL"));
		assertFalse(text.contains("SHELL_BASED_SURFACE_MODEL"));
		assertTrue(Arrays.equals(vertices, source.getVertices()));
		assertTrue(Arrays.equals(triangles, source.getTriangles()));
	}

	@Test(expected = IllegalStateException.class)
	public void invalidMeshFailsInsteadOfBeingExportedUnchanged() {
		CSG open = new Cube(10).toCSG();
		open.setTriangles(Arrays.copyOf(open.getTriangles(), open.getTriangles().length - 3));
		new STEPFile(open);
	}
}
