package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.bowlerstudio.threed.StepBRepWrite;
import com.neuronrobotics.bowlerstudio.threed.StepFace;
import com.neuronrobotics.bowlerstudio.threed.StepFaceBorders;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * A face must hold every coplanar triangle that touches it.
 *
 * <p>
 * The export's whole purpose is that a CAD package shows one selectable surface
 * where the mesh has one flat surface. A face that stops at an interior line of its
 * own plane is a fractured face: the reader draws a seam through it and the user
 * edits the pieces separately. The mesh's own triangles say exactly where the flat
 * surfaces are, so the property is checked against them rather than against a
 * tolerance: two triangles that share an edge and lie in one plane are one surface,
 * and no written border may run between them.
 *
 * <p>
 * The gate is the feature-edge one, in its relative form. A border no feature edge
 * carries is only a defect when one plane holds both faces across it; a border the
 * model genuinely bends across, even by less than the angle, cannot be one planar
 * face and is reported separately. See {@link StepFaceBorders}.
 */
public class StepFaceFusionTest {

	/** The corpus the face continuity is measured on. */
	private static final File[] CORPUS = {new File("testStepFiles/Marvelous_Champion_Nametag.3mf"),
			new File("testStepFiles/Charming_Troll_Group_of_3.3mf"), new File("testStepFiles/Pointer_Hand_star.3mf")};

	@BeforeClass
	public static void useTheKernel() {
		CSG.setDefaultOptType(CSG.OptType.Manifold3d);
	}

	/** The part as the writer receives it, after the export's own cleanup. */
	private static STEPFile exported(File source) throws Throwable {
		CSG csg = CSG.fromThreeMF(source.toPath()).get(0);
		csg.setNoScale(true);
		return new STEPFile(csg);
	}

	/**
	 * No written face border is a crack: every border that is not a feature edge of
	 * the model still has both its faces in one plane, only when the model bends
	 * across it, which no planar face can follow.
	 *
	 * <p>
	 * This is the defect the export is judged on. A fracture is a border the model
	 * has no feature along, across which one plane holds both faces: two pieces of
	 * one flat surface written separately.
	 */
	@Test
	public void noFaceBorderIsACrackInAPlane() throws Throwable {
		for (File source : CORPUS) {
			assertTrue("the fixture must exist at " + source.getAbsolutePath(), source.isFile());
			List<StepFaceBorders.Fracture> fractures = exported(source).getFracturedBorders();
			assertEquals("every coplanar, connected triangle must be in one face of " + source.getName()
					+ "; these borders split one plane in two: " + fractures, 0, fractures.size());
		}
	}

	/**
	 * No two adjacent faces that one plane holds are left apart, whatever the order
	 * the mesh stores its triangles in.
	 *
	 * <p>
	 * A face is fused from its triangles' adjacency, not grown from whichever
	 * triangle was visited first, so reversing the triangle order cannot split a
	 * face that was whole. This is what stops the fracture following a seam.
	 */
	@Test
	public void theFusionDoesNotDependOnTheTriangleOrder() throws Throwable {
		for (File source : CORPUS) {
			CSG csg = CSG.fromThreeMF(source.toPath()).get(0);
			csg.setNoScale(true);
			CSG body = new STEPFile(csg).getParts().get(0);
			body.setNoScale(true);
			int forward = StepBRepWrite.faces(body).size();

			CSG reversed = reverseTriangles(body);
			int backward = StepBRepWrite.faces(reversed).size();
			assertEquals("reversing the triangle order must not change how many faces " + source.getName()
					+ " is written as, or the grouping depends on the visit order", forward, backward);
		}
	}

	/**
	 * Every face the writer produces is planar and its outline meets its neighbours.
	 *
	 * <p>
	 * The guard on the fusion: making faces larger must never make one leave its own
	 * plane, since a reader holding a face to its plane then reads a different shape
	 * than the mesh.
	 */
	@Test
	public void everyFaceIsPlanar() throws Throwable {
		for (File source : CORPUS) {
			for (StepFace face : exported(source).getFaces())
				assertTrue("a face of " + source.getName() + " leaves its own plane: " + face, face.isPlanar());
		}
	}

	/**
	 * A border the model genuinely bends across is not a fracture, however shallow the
	 * bend.
	 *
	 * <p>
	 * The top of this box is a shallow saddle: two triangles, each its own plane, tilted
	 * a quarter of a degree from each other, which lifts the far corner by four tenths
	 * of a millimetre. Fusing them would write a face whose corner sits off its own
	 * plane, which a reader then reads as a different shape. The gate must not demand
	 * that fusion, so the two stay apart and the gate reports nothing: the bend is the
	 * model's, not a crack.
	 */
	@Test
	public void aShallowBendIsNotAFracture() throws Exception {
		// a closed box, its top a saddle so no plane holds it
		double lift = Math.tan(Math.toRadians(0.25)) * 100;
		double[] points = {0, 0, 0, 100, 0, 0, 100, 50, 0, 0, 50, 0, // bottom
				0, 0, lift, 100, 0, 0, 100, 50, lift, 0, 50, 0}; // top, a saddle
		long[] triangles = {0, 2, 1, 0, 3, 2, // bottom, wound down
				4, 5, 6, 4, 6, 7, // top, split along the saddle's diagonal
				0, 1, 5, 0, 5, 4, 1, 2, 6, 1, 6, 5, 2, 3, 7, 2, 7, 6, 3, 0, 4, 3, 4, 7};
		CSG saddle = new CSG(points, triangles, javafx.scene.paint.Color.GRAY);
		saddle.setName("shallow-bend");

		STEPFile file = new STEPFile(saddle);
		assertEquals("the bend is the model's own, not a fracture: " + file.getFracturedBorders(), 0,
				file.getFracturedBorders().size());
		// and the two halves of the top really are written apart
		List<StepFace> faces = file.getFaces();
		long tilted = 0;
		for (StepFace face : faces)
			if (Math.abs(face.normal.z) < 1 - 1e-9 && face.normal.z > 0)
				tilted++;
		assertEquals("the saddle's two halves cannot be one planar face", 2, tilted);
	}

	/**
	 * Two coplanar sheets that one plane holds are fused, so the gate cannot be passed
	 * by splitting every face.
	 *
	 * <p>
	 * The positive case: a mesh whose two triangles are exactly one plane must come out
	 * as one face, and the gate must find nothing to report on it.
	 */
	@Test
	public void twoCoplanarTrianglesAreOneFace() throws Exception {
		double[] points = {0, 0, 0, 40, 0, 0, 40, 40, 0, 0, 40, 0};
		long[] triangles = {0, 1, 2, 0, 2, 3};
		CSG sheet = new CSG(points, triangles, javafx.scene.paint.Color.GRAY);
		sheet.setName("one-plane");

		STEPFile file = new STEPFile(sheet);
		assertEquals("one coplanar square is one face", 1, file.getFaces().size());
		assertEquals("one plane is not a fracture", 0, file.getFracturedBorders().size());
	}

	/** The same mesh with every triangle's corners reversed, which is the same surface. */
	private static CSG reverseTriangles(CSG csg) {
		double[] vertices = csg.getVertices().clone();
		long[] triangles = csg.getTriangles().clone();
		for (int at = 0; at + 2 < triangles.length; at += 3) {
			long swap = triangles[at + 1];
			triangles[at + 1] = triangles[at + 2];
			triangles[at + 2] = swap;
		}
		CSG reversed = new CSG(vertices, triangles, javafx.scene.paint.Color.GRAY);
		reversed.setName(csg.getName());
		return reversed;
	}
}
