package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.ManifoldMeshCorrector;
import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.sdk.common.TickToc;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * The whole export loop, closed on geometry: the CSG that goes into
 * {@link STEPFile} must come out of the written STEP as the same solid.
 *
 * <p>
 * The check is the one a CAD package performs when it sews the file: the
 * intersection of the source and the exported mesh must enclose the source's own
 * volume, and neither difference may contain more than a bounds-budget-thick skin
 * of the source's surface. Volume alone cannot see the defect this guards — a
 * correction that both adds and removes material can leave the net volume
 * unchanged — so the two differences are measured separately, one for material
 * the file lost and one for material it invented.
 *
 * <p>
 * The loop is measured end to end through {@link STEPFile}, not against the
 * correction directly, so a stage that drops or invents geometry anywhere between
 * the incoming CSG and the written file fails here.
 */
public class StepExportGeometryTest {

	/** The fixtures the corpus holds, which the app exports in its own tests. */
	private static final File CORPUS = new File("testStepFiles");

	/**
	 * How far the surface may move, in millimetres. The same budget
	 * {@link STEPFile#BREP_BOUNDS_BUDGET_MM} holds the correction to: a hundredth of a
	 * micron, below what a BRep reader resolves.
	 */
	private static final double BUDGET_MM = STEPFile.BREP_BOUNDS_BUDGET_MM;

	/**
	 * The relative volume the intersection must reach. A collapse that keeps the
	 * surface keeps the volume to within arithmetic noise on a part this size; the
	 * budget says so in the mesher's own terms rather than as a bare epsilon.
	 */
	private static final double INTERSECTION_RELATIVE = 1e-6;

	@BeforeClass
	public static void useTheKernel() {
		CSG.setDefaultOptType(CSG.OptType.Manifold3d);
		// the loop is timed: TickToc is opt-in, and the profile is how a change with a
		// cost that does not fit the export gets caught in review
		TickToc.setEnabled(true);
	}

	@Test
	public void nametagKeepsItsGeometryThroughTheExport() throws Throwable {
		throughTheExport("Marvelous_Champion_Nametag.3mf");
	}

	@Test
	public void pointerHandKeepsItsGeometryThroughTheExport() throws Throwable {
		throughTheExport("Pointer_Hand_star.3mf");
	}

	@Test
	public void charmingTrollKeepsItsGeometryThroughTheExport() throws Throwable {
		throughTheExport("Charming_Troll_Group_of_3.3mf");
	}

	/**
	 * Exports the named fixture, reads the written file back, and holds the mesh a
	 * reader gets to the source: the intersection and both differences, bounds and
	 * volume.
	 *
	 * <p>
	 * The comparison is over the file, not over the parts the writer holds in memory.
	 * The STEP carries a tessellation of the mesh beside the BRep, and that is what
	 * this reads back, so what is measured here is the geometry a downstream tool
	 * reproduces from the file — the same measure FreeCAD's own gate applies.
	 */
	private static void throughTheExport(String name) throws Throwable {
		File file = new File(CORPUS, name);
		assertTrue("the fixture must exist at " + file.getAbsolutePath(), file.isFile());
		CSG source = CSG.fromThreeMF(file.toPath()).get(0);
		source.setNoScale(true);
		source.setName(name.replaceAll("\\..*", ""));

		TickToc.tic("load " + name);
		STEPFile exported = new STEPFile(source);
		int handed = 0;
		for (CSG part : exported.getParts())
			handed += part.getTriCount();
		File step = File.createTempFile("export_geometry_" + source.getName(), ".step");
		step.deleteOnExit();
		exported.write(step);
		TickToc.tic("export " + name);

		List<CSG> read;
		try (java.io.FileInputStream in = new java.io.FileInputStream(step)) {
			read = new STEPFile(in).getImported();
		}
		TickToc.tic("read " + name);
		assertFalse("the written file carried no geometry for " + name, read.isEmpty());

		// every triangle the writer was handed is in the file. A stage that silently
		// drops triangles still closes a volume — it loses a little material and
		// invents a little elsewhere — so the volume checks below cannot see it; the
		// count can, and it is what fractured the faces before
		int carried = 0;
		for (CSG part : read)
			carried += part.getTriCount();
		assertEquals("every triangle the writer was handed must be in the file for " + name, handed, carried);

		double sourceVolume = CSG.getManifold().volume(source);
		double sourceArea = CSG.getManifold().surfaceArea(source);
		// the material a correction may move: a skin of the surface one budget thick.
		// This is the budget expressed as volume, so it does not have to be guessed for
		// a part of any size.
		double skin = Math.max(1e-12, sourceArea * BUDGET_MM);

		// the shapes read from the file cover the source as one body: their union is
		// what a reader reassembles from the file
		CSG roundTripped = read.size() == 1 ? read.get(0) : union(read);
		double writtenVolume = CSG.getManifold().volume(roundTripped);

		TickToc.tic("booleans " + name);
		double shared = CSG.getManifold().intersection(source, roundTripped).getVolume();
		double missing = CSG.getManifold().difference(source, roundTripped).getVolume();
		double invented = CSG.getManifold().difference(roundTripped, source).getVolume();

		String measured = name + ": source " + sourceVolume + " mm^3, written " + writtenVolume + " mm^3, intersection "
				+ shared + " mm^3, missing " + missing + " mm^3, invented " + invented + " mm^3, skin budget " + skin
				+ " mm^3";

		// the intersection is the source: no surface the source has was lost
		assertTrue("the intersection of the source and the export must enclose the source's volume: " + measured,
				shared >= sourceVolume * (1 - INTERSECTION_RELATIVE));
		assertTrue("the intersection may not enclose material the source does not have: " + measured,
				shared <= sourceVolume * (1 + INTERSECTION_RELATIVE));

		// and neither side has material the other lacks, beyond the budget's skin
		assertEquals("the export lost material the source has: " + measured, 0, missing, skin);
		assertEquals("the export invented material the source does not have: " + measured, 0, invented, skin);

		// the volumes agree, which is what a reader reports for the file
		assertEquals("the written volume must match the source's: " + measured, sourceVolume, writtenVolume,
				Math.max(skin, sourceVolume * INTERSECTION_RELATIVE));

		// the bounds are where the source's are: a solid that keeps its volume but
		// drifts is a different part in the same place, and the STEP header declares
		// this budget as the file's own distance accuracy
		eu.mihosoft.vrl.v3d.Bounds expected = source.getBounds();
		eu.mihosoft.vrl.v3d.Bounds actual = roundTripped.getBounds();
		assertEquals("minimum x of the export", expected.getMin().x, actual.getMin().x, BUDGET_MM);
		assertEquals("minimum y of the export", expected.getMin().y, actual.getMin().y, BUDGET_MM);
		assertEquals("minimum z of the export", expected.getMin().z, actual.getMin().z, BUDGET_MM);
		assertEquals("maximum x of the export", expected.getMax().x, actual.getMax().x, BUDGET_MM);
		assertEquals("maximum y of the export", expected.getMax().y, actual.getMax().y, BUDGET_MM);
		assertEquals("maximum z of the export", expected.getMax().z, actual.getMax().z, BUDGET_MM);

		TickToc.toc();
	}

	/** The parts as one solid, through the kernel's batch union. */
	private static CSG union(List<CSG> parts) throws Throwable {
		return CSG.getManifold().batchUnion(parts.toArray(new CSG[0]));
	}

	/**
	 * The correction on its own is held to the same measures, so a stage that starts
	 * moving the surface is named here rather than only through the written file.
	 */
	@Test
	public void correctionKeepsTheGeometry() throws Throwable {
		File file = new File(CORPUS, "Marvelous_Champion_Nametag.3mf");
		CSG source = CSG.fromThreeMF(file.toPath()).get(0);
		source.setNoScale(true);
		double sourceVolume = CSG.getManifold().volume(source);
		double skin = CSG.getManifold().surfaceArea(source) * BUDGET_MM;

		CSG corrected = ManifoldMeshCorrector.correct(source, BUDGET_MM);
		double shared = CSG.getManifold().intersection(source, corrected).getVolume();
		double missing = CSG.getManifold().difference(source, corrected).getVolume();
		double invented = CSG.getManifold().difference(corrected, source).getVolume();
		String measured = "correction: source " + sourceVolume + " mm^3, intersection " + shared + " mm^3, missing "
				+ missing + " mm^3, invented " + invented + " mm^3, skin budget " + skin + " mm^3";

		assertEquals("the correction lost material: " + measured, 0, missing, skin);
		assertEquals("the correction invented material: " + measured, 0, invented, skin);
		assertEquals("the correction's volume must match the source's: " + measured, sourceVolume,
				CSG.getManifold().volume(corrected), Math.max(skin, sourceVolume * INTERSECTION_RELATIVE));
	}
}
