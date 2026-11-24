package junit.bowler;

import static org.junit.Assert.*;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.*;
import com.neuronrobotics.manifold3d.MeshProcessing;
import com.neuronrobotics.manifold3d.CSGManifold3d;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import javafx.scene.paint.Color;

public class SimplifyTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	private <T extends Simplify> T attach(T operation) throws Exception {
		operation.setCaDoodleFile(new CaDoodleFile().setSelf(new File(temporary.newFolder(), "test.doodle")));
		return operation;
	}

	@Test
	public void defaultsSimplifyOnlyWithAdjustableRefinement() throws Exception {
		Simplify operation = attach(new Simplify());
		MeshProcessing.Settings defaults = operation.getSettings();
		assertEquals(.5, defaults.simplify(), 0);
		assertEquals(0, defaults.calculateNormals());
		assertEquals(0, defaults.smoothByNormals());
		assertEquals(0, defaults.smoothOut(), 0);
		assertEquals(0, defaults.refine());
		assertEquals(0, defaults.refineToLength(), 0);
		assertEquals(0, defaults.refineToTolerance(), 0);
		operation.refineParameter().setMM(2);
		operation.refineToLengthParameter().setMM(4);
		operation.refineToToleranceParameter().setMM(.1);
		MeshProcessing.Settings adjusted = operation.getSettings();
		assertEquals(.5, adjusted.simplify(), 0);
		assertEquals(2, adjusted.refine());
		assertEquals(4, adjusted.refineToLength(), 0);
		assertEquals(.1, adjusted.refineToTolerance(), .0001);
	}

	@Test
	public void parserRoundTripPreservesSettingsAndSelection() {
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
				.registerTypeAdapterFactory(new CaDoodleJsonOperationAdapterFactory()).create();
		Simplify original = new Simplify().setNames(Arrays.asList("selected")).setSimplify(.125).setCalculateNormals(1)
				.setSmoothByNormals(2).setNormalAngle(45.5).setSmoothOut(72.5).setSmoothness(.4).setRefine(3)
				.setRefineToLength(2.5).setRefineToTolerance(.2);
		Simplify copy = (Simplify) gson.fromJson(gson.toJson(original, CaDoodleOperation.class),
				CaDoodleOperation.class);
		assertEquals(original.getName(), copy.getName());
		assertEquals(original.getNamesAddedInThisOperation(), copy.getNamesAddedInThisOperation());
		assertEquals(.125, copy.getSimplify(), 0);
		assertEquals(1, copy.getCalculateNormals());
		assertEquals(2, copy.getSmoothByNormals());
		assertEquals(45.5, copy.getNormalAngle(), 0);
		assertEquals(72.5, copy.getSmoothOut(), 0);
		assertEquals(.4, copy.getSmoothness(), 0);
		assertEquals(3, copy.getRefine());
		assertEquals(2.5, copy.getRefineToLength(), 0);
		assertEquals(.2, copy.getRefineToTolerance(), 0);
	}

	@Test
	public void disabledStagesSkipNativeAndLeaveUnselectedUntouched() throws Throwable {
		Simplify op = attach(new Simplify().setNames(Arrays.asList("selected", "selected", "missing")).setSimplify(-1)
				.setSmoothOut(-1).setCalculateNormals(-1).setSmoothByNormals(-1).setRefine(-1).setRefineToLength(-1)
				.setRefineToTolerance(-1));
		CSG selected = new Cube(10).toCSG().setName("selected").setColor(Color.RED).setIsHole(true);
		CSG other = new Cube(5).toCSG().setName("other");
		List<CSG> result = op.process(Arrays.asList(other, selected));
		assertEquals(2, result.size());
		assertSame(other, result.get(0));
		assertEquals(selected.getName(), result.get(1).getName());
		assertEquals(Color.RED, result.get(1).getColor());
		assertTrue(result.get(1).isHole());
		assertEquals(selected.getNumberOfTriangles(), result.get(1).getNumberOfTriangles());
		assertFalse(op.getSettings().enabled());
		assertSame(selected, MeshProcessing.apply(selected, op.getSettings()));
	}

	@Test
	public void parametersRegenerateFromOriginalNotPriorResult() throws Exception {
		class Recording extends Simplify {
			CSG source;
			double tolerance;
			@Override
			protected CSG applyGeometry(CSG incoming, MeshProcessing.Settings settings) {
				source = incoming;
				tolerance = settings.simplify();
				return new Cube(settings.simplify() + 1).toCSG();
			}
		}
		Recording op = attach(new Recording());
		op.setNames(Arrays.asList("selected"));
		CSG original = new Cube(10).toCSG().setName("selected");
		CSG first = op.process(Collections.singletonList(original)).get(0);
		op.simplifyParameter().setMM(.5);
		CSG second = first.regenerate();
		assertSame(original, op.source);
		assertEquals(.5, op.tolerance, 0);
		assertEquals(1.5, second.getBounds().getTotalX(), .0001);
		assertEquals(2, op.simplifyParameter().getOptions().size());
		assertTrue(op.refineParameter().getOptions().size() > 2);
		assertEquals(9, second.getParameters(op.getDb()).size());
		assertTrue(Simplify.isParameterKey(op.simplifyParameter().getName()));
		assertTrue(op.ownsParameterKey(op.simplifyParameter().getName()));
		assertFalse(new Simplify().ownsParameterKey(op.simplifyParameter().getName()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void fractionalIntegerControlRejected() throws Exception {
		Simplify op = attach(new Simplify());
		op.refineParameter().setMM(1.5);
		op.getSettings();
	}

	@Test
	public void nativeStagesProduceMeshes() throws Throwable {
		CSG source = new Cube(10).toCSG();
		CSGManifold3d bridge = new CSGManifold3d();
		for (int stage = 0; stage < 7; stage++) {
			MeshProcessing.Settings settings = new MeshProcessing.Settings(stage == 0 ? .01 : 0, stage == 1 ? 1 : 0,
					stage == 2 ? 2 : 0, 120, stage == 3 ? 120 : 0, 0, stage == 4 ? 2 : 0, stage == 5 ? 4 : 0,
					stage == 6 ? .1 : 0);
			CSG result = MeshProcessing.apply(source, settings);
			assertFalse("stage " + stage, result.getPolygons().isEmpty());
			if (stage >= 1 && stage <= 3)
				assertTrue("smoothing changes surface, not just tessellation",
						Math.abs(bridge.volume(result) - 1000) > 1);
			else
				assertEquals("cleanup/refinement preserves volume", 1000, bridge.volume(result), .01);
			if (stage >= 1 && stage <= 5)
				assertTrue("stage " + stage + " refines geometry",
						result.getNumberOfTriangles() > source.getNumberOfTriangles());
		}
	}

	@Test
	public void zeroControlsSkipAndEmptySelectionNeedsNoDocument() throws Throwable {
		CSG source = new Cube(10).toCSG();
		assertSame(source, MeshProcessing.apply(source, new MeshProcessing.Settings(0, 0, 0, 60, 0, 0, 0, 0, 0)));
		assertSame(source, new Simplify().process(Collections.singletonList(source)).get(0));
	}

	@Test
	public void combinedPipelineAppliesToBothSelectedObjectsOnce() throws Throwable {
		Simplify op = attach(new Simplify().setNames(Arrays.asList("first", "second", "first")).setCalculateNormals(1)
				.setSmoothByNormals(2).setNormalAngle(120).setSmoothOut(120).setSmoothness(.25).setRefine(2)
				.setRefineToLength(4).setRefineToTolerance(.1));
		CSG first = new Cube(10).toCSG().setName("first");
		CSG second = new Cube(10).toCSG().setName("second");
		CSG unselected = new Cube(10).toCSG().setName("third");
		List<CSG> result = op.process(Arrays.asList(first, unselected, second));
		assertEquals(3, result.size());
		assertEquals("first", result.get(0).getName());
		assertSame(unselected, result.get(1));
		assertEquals("second", result.get(2).getName());
		assertEquals(result.get(0).getNumberOfTriangles(), result.get(2).getNumberOfTriangles());
		assertTrue(result.get(0).getNumberOfTriangles() > first.getNumberOfTriangles());
		assertEquals(result.get(0).getParameters(op.getDb()), result.get(2).getParameters(op.getDb()));
	}

	@Test
	public void documentDatabaseRestoresEditedParametersAfterJsonReload() throws Exception {
		File self = new File(temporary.newFolder(), "saved.doodle");
		Simplify original = new Simplify();
		original.setCaDoodleFile(new CaDoodleFile().setSelf(self));
		original.simplifyParameter().setMM(.75);
		original.refineParameter().setMM(4);
		original.getDb().saveDatabase();
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
				.registerTypeAdapterFactory(new CaDoodleJsonOperationAdapterFactory()).create();
		Simplify restored = (Simplify) gson.fromJson(gson.toJson(original, CaDoodleOperation.class),
				CaDoodleOperation.class);
		restored.setCaDoodleFile(new CaDoodleFile().setSelf(self));
		assertEquals(.75, restored.getSettings().simplify(), 0);
		assertEquals(4, restored.getSettings().refine());
	}

}
