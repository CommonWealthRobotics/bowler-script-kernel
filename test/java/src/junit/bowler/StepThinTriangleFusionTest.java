package junit.bowler;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.StepBRepWrite;
import com.neuronrobotics.bowlerstudio.threed.StepFace;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Plane;

/**
 * A stitching sliver must join the face it lies in instead of fracturing it.
 *
 * <p>
 * A boolean kernel leaves slivers wherever two sheets of a surface meet at a weld:
 * triangles whose three points are nearly collinear in the surface, with a tip a
 * few ulps off it. Their normal is the cross product of two nearly parallel edges,
 * so it sits at the scale of the coordinates' rounding and points wherever the
 * noise takes it — in practice across the surface, perpendicular to the face the
 * triangle lies in.
 *
 * <p>
 * A writer that decides a triangle's place by its own normal therefore rejects the
 * sliver, even though every one of its points is in the face's plane. The sliver is
 * then left as a face of its own, and because its neighbours pair across its edges,
 * the face around it cannot pair and is fractured with it. The rule the writer has
 * to follow is the plane: a triangle whose points are all within the JCSG plane
 * epsilon of the face's plane belongs to that face, whatever its own normal says.
 */
public class StepThinTriangleFusionTest {

	/** The JCSG plane epsilon: the resolution the kernel itself works to. */
	private static final double EPSILON = Plane.getEPSILON();

	/** Builds a CSG from raw geometry, so the triangles are exactly as written. */
	private static CSG mesh(double[] points, long[] triangles) {
		CSG csg = new CSG(points, triangles, javafx.scene.paint.Color.GRAY);
		csg.setName("thin-seam");
		return csg;
	}

	/** The faces the writer grouped the mesh into, as the file would carry them. */
	private static List<StepFace> faces(double[] points, long[] triangles) {
		return StepBRepWrite.faces(mesh(points, triangles));
	}

	/**
	 * A square sheet in z = 0 with four corners, plus a fifth point just off its
	 * corner that the needle below reaches. The needle runs along the sheet's own
	 * bottom edge, so it has two points on the sheet and one a hair off it: it is as
	 * thin as a sliver gets, and its normal is noise.
	 */
	private static double[] sheetWithNeedle(double lift) {
		return new double[]{0, 0, 0, // 0
				4, 0, 0, // 1
				4, 4, 0, // 2
				0, 4, 0, // 3
				4, 0, lift, // 4 the needle's tip, `lift` off the sheet's plane
		};
	}

	/** The needle first, so the broken writer seeds its face from the sliver. */
	private static long[] needleThenSheet() {
		return new long[]{1, 0, 4, // the needle along the sheet's bottom edge 0-1
				0, 1, 2, 0, 2, 3, // the sheet
		};
	}

	/** The sheet first, so the needle is the triangle the walk has to admit. */
	private static long[] sheetThenNeedle() {
		return new long[]{0, 1, 2, 0, 2, 3, // the sheet
				1, 0, 4, // the needle
		};
	}

	/**
	 * A sliver whose points are within the plane epsilon joins the sheet.
	 *
	 * <p>
	 * The needle's own normal is the noise of a near-zero cross product, so a writer
	 * that consults it leaves the needle out and the sheet comes out as two faces. The
	 * needle's three points are all within the epsilon of the sheet's plane, so by the
	 * plane rule the two are one face.
	 */
	@Test
	public void aSliverWithinThePlaneEpsilonJoinsTheSheet() {
		List<StepFace> written = faces(sheetWithNeedle(1e-9), needleThenSheet());
		assertEquals("the needle's points are all within the plane epsilon, so the sheet and needle are one face;"
				+ " it came out as " + written.size(), 1, written.size());
	}

	/**
	 * The same sliver joins the sheet even when the sheet was written first, so the
	 * rule cannot depend on which triangle a face grew from.
	 */
	@Test
	public void aSliverJoinsTheSheetWhicheverWasWrittenFirst() {
		List<StepFace> written = faces(sheetWithNeedle(1e-11), sheetThenNeedle());
		assertEquals("the plane rule must not depend on triangle order; it came out as " + written.size(), 1,
				written.size());
	}

	/**
	 * A needle whose tip is outside the plane epsilon keeps a face of its own.
	 *
	 * <p>
	 * The guard against the fix becoming a licence to absorb anything: a triangle
	 * genuinely off the face's plane is a separate surface, and taking it in would
	 * move the written face off its own plane.
	 */
	@Test
	public void aNeedleOutsideThePlaneEpsilonIsNotAbsorbed() {
		List<StepFace> written = faces(sheetWithNeedle(1e-3), needleThenSheet());
		assertEquals("a tip a thousand epsilons off the plane is not in it, so the needle is a face of its own; got "
				+ written.size(), 2, written.size());
	}

	/**
	 * An ordinary coplanar sheet is one face: the baseline the sliver cases are
	 * measured against, so a break in the ordinary case is not read as a fix.
	 */
	@Test
	public void anOrdinaryCoplanarSheetIsOneFace() {
		double[] points = {0, 0, 0, 4, 0, 0, 4, 4, 0, 0, 4, 0};
		long[] triangles = {0, 1, 2, 0, 2, 3};
		assertEquals("one coplanar square is one face", 1, faces(points, triangles).size());
	}

	/**
	 * Two coplanar sheets that meet only at a corner stay two faces.
	 *
	 * <p>
	 * A face is a connected surface. Joining two sheets that merely touch at a point
	 * would make one face whose outline crosses itself, which no reader can bound.
	 */
	@Test
	public void coplanarSheetsTouchingAtOnePointStaySeparate() {
		double[] points = {0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, // first square
				1, 1, 0, 2, 1, 0, 2, 2, 0, 1, 2, 0, // second, sharing only (1,1,0)
		};
		long[] triangles = {0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7};
		assertEquals("two coplanar sheets meeting at a corner are two faces", 2, faces(points, triangles).size());
	}
}
