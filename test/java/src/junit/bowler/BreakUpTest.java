package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.*;
import com.neuronrobotics.manifold3d.CSGManifold3d;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import javafx.scene.paint.Color;

public class BreakUpTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	private BreakUp attach(BreakUp operation) throws Exception {
		operation.setCaDoodleFile(new CaDoodleFile().setSelf(new File(temporary.newFolder(), "test.doodle")));
		return operation;
	}

	private CSG disconnected(String name) {
		return new Cube(2).toCSG().movex(10).union(new Cube(4).toCSG().movex(30)).setName(name);
	}

	private List<String> names(List<CSG> geometry) {
		List<String> result = new ArrayList<>();
		for (CSG c : geometry)
			result.add(c.getName());
		return result;
	}

	@Test
	public void replacesWithNativeComponentsAtOriginalPositions() throws Throwable {
		CSG source = disconnected("source").setColor(Color.ORANGE).setIsHole(true).setUserDefinedName("Assembly");
		source.getStorage().set("custom", "metadata");
		source.setRegenerate(previous -> source);
		CSG other = new Cube(1).toCSG().setName("other");
		BreakUp operation = attach(new BreakUp().setNames(Arrays.asList("source", "source", "missing")));
		List<CSG> result = operation.process(Arrays.asList(other, source));
		assertEquals(3, result.size());
		assertSame(other, result.get(0));
		assertFalse(result.contains(source));
		CSGManifold3d nativeGeometry = new CSGManifold3d();
		double volume = 0;
		List<Double> centers = new ArrayList<>();
		for (CSG part : result.subList(1, 3)) {
			volume += nativeGeometry.volume(part);
			centers.add(part.getBounds().getCenterX());
			assertEquals(Color.ORANGE, part.getColor());
			assertTrue(part.isHole());
			assertEquals("Assembly", part.getUserDefinedName());
			assertEquals("metadata", part.getStorage().getValue("custom").get());
			assertSame(part, part.regenerate());
			assertEquals(1, nativeGeometry.decompose(part).size());
		}
		Collections.sort(centers);
		assertEquals(10, centers.get(0), .0001);
		assertEquals(30, centers.get(1), .0001);
		assertEquals(72, volume, .0001);
		assertEquals(names(result.subList(1, 3)), operation.getNamesAddedInThisOperation());
		assertEquals(2, nativeGeometry.decompose(source).size());
	}

	@Test
	public void selectionAndEmptyGeometryDoNotCreateUnintendedObjects() throws Exception {
		CSG solid = new Cube(2).toCSG().setName("solid");
		CSG empty = new CSG().setName("empty");
		BreakUp operation = attach(new BreakUp());
		assertSame(solid, operation.process(Arrays.asList(solid)).get(0));
		operation.setNames(Arrays.asList("missing"));
		assertSame(solid, operation.process(Arrays.asList(solid)).get(0));
		operation.setNames(Arrays.asList("solid", "solid", "empty"));
		assertEquals(1, operation.process(Arrays.asList(solid, empty)).size());
		assertEquals(1, operation.getNamesAddedInThisOperation().size());
		operation.setNames(Arrays.asList("empty"));
		assertTrue(operation.process(Arrays.asList(empty)).isEmpty());
		assertTrue(operation.getNamesAddedInThisOperation().isEmpty());
		operation.setNames(Collections.emptyList());
		assertTrue(operation.process(Collections.emptyList()).isEmpty());
	}

	@Test
	public void uniqueNamesSurviveReplaySelectionOrderAndJson() throws Exception {
		CSG first = disconnected("first");
		CSG second = disconnected("second");
		List<String> selection = new ArrayList<>(Arrays.asList("second", "first"));
		BreakUp operation = attach(new BreakUp().setNames(selection));
		selection.clear();
		CSG collision = new Cube(1).toCSG().setName(operation.getName() + "_1");
		List<CSG> input = Arrays.asList(first, collision, second);
		List<String> expected = names(operation.process(input));
		assertEquals(5, new HashSet<>(expected).size());
		assertEquals(expected, names(operation.process(input)));
		assertEquals(4, operation.getNamesAddedInThisOperation().size());
		operation.setNames(Arrays.asList("first", "second", "first"));
		assertEquals(expected, names(operation.process(input)));
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
				.registerTypeAdapterFactory(new CaDoodleJsonOperationAdapterFactory()).create();
		String json = gson.toJson(operation, CaDoodleOperation.class);
		BreakUp restored = attach((BreakUp) gson.fromJson(json, CaDoodleOperation.class));
		assertEquals("BreakUp", restored.getType());
		assertEquals(operation.getName(), restored.getName());
		assertTrue(restored.getNamesAddedInThisOperation().isEmpty());
		assertEquals(expected, names(restored.process(input)));
		assertEquals(operation.getNamesAddedInThisOperation(), restored.getNamesAddedInThisOperation());
		operation.setNames(Arrays.asList("second"));
		List<CSG> fewer = operation.process(input);
		assertSame(first, fewer.get(0));
		assertEquals(expected.subList(3, 5), operation.getNamesAddedInThisOperation());
	}

	@Test
	public void groupConstituentsDisappearAndPartsCannotRecreateGroups() throws Throwable {
		CSG leaf = new Cube(2).toCSG().setName("leaf").addGroupMembership("nested");
		CSG nested = new Cube(2).toCSG().setName("nested").addIsGroupResult("nested").addGroupMembership("group");
		CSG group = disconnected("group").addIsGroupResult("group");
		group.setRegenerate(previous -> group);
		CSG unrelated = new Cube(1).toCSG().setName("unrelated");
		BreakUp operation = attach(new BreakUp().setNames(Arrays.asList("group", "leaf", "nested")));
		List<CSG> result = operation.process(Arrays.asList(leaf, nested, group, unrelated));
		assertEquals(3, result.size());
		assertSame(unrelated, result.get(2));
		for (CSG part : result.subList(0, 2)) {
			assertFalse(part.isGroupResult());
			assertFalse(part.isInGroup());
			assertSame(part, part.regenerate());
		}
		assertTrue(group.isGroupResult());
		assertTrue(nested.checkGroupMembership("group"));
		assertTrue(leaf.checkGroupMembership("nested"));
		List<CSG> deleted = new Delete().setNames(operation.getNamesAddedInThisOperation()).process(result);
		assertEquals(Collections.singletonList(unrelated), deleted);
	}

	@Test
	public void realGroupAndConnectedSolidBecomeIndependentParts() throws Throwable {
		BreakUp operation = attach(new BreakUp());
		CSG first = new Cube(2).toCSG().movex(10).setName("first");
		CSG second = new Cube(4).toCSG().movex(30).setName("second");
		Group group = new Group().setNames(Arrays.asList("first", "second"));
		group.setCaDoodleFile(operation.getCaDoodleFile());
		List<CSG> grouped = group.process(Arrays.asList(first, second));
		operation.setNames(Arrays.asList(group.getGroupID()));
		List<CSG> parts = operation.process(grouped);
		assertEquals(2, parts.size());
		for (CSG part : parts) {
			assertFalse(part.isGroupResult());
			assertFalse(part.isInGroup());
			assertFalse(part.isHole());
		}
		operation.setNames(Arrays.asList("first"));
		CSG connected = operation.process(Arrays.asList(first)).get(0);
		assertEquals(8, new CSGManifold3d().volume(connected), .0001);
		assertEquals(10, connected.getBounds().getCenterX(), .0001);
	}

	@Test
	public void savedBeforeProcessingKeepsIdentityAndReplayAvoidsNewCollisions() throws Exception {
		Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
				.registerTypeAdapterFactory(new CaDoodleJsonOperationAdapterFactory()).create();
		BreakUp operation = attach(new BreakUp().setNames(Arrays.asList("source")));
		BreakUp restored = attach(
				(BreakUp) gson.fromJson(gson.toJson(operation, CaDoodleOperation.class), CaDoodleOperation.class));
		CSG source = disconnected("source");
		List<String> expected = names(operation.process(Arrays.asList(source)));
		assertEquals(expected, names(restored.process(Arrays.asList(source))));
		CSG collision = new Cube(1).toCSG().setName(expected.get(0));
		List<CSG> result = restored.process(Arrays.asList(source, collision));
		assertSame(collision, result.get(2));
		assertEquals(3, new HashSet<>(names(result)).size());
		assertEquals(names(result), names(restored.process(Arrays.asList(source, collision))));
	}

	@Test
	public void upstreamParameterCallbacksAreNotInheritedByParts() throws Exception {
		BreakUp operation = attach(new BreakUp().setNames(Arrays.asList("source")));
		CSG source = disconnected("source");
		eu.mihosoft.vrl.v3d.parametrics.LengthParameter parameter = new eu.mihosoft.vrl.v3d.parametrics.LengthParameter(
				operation.getDb(), "upstream", 2.0, new ArrayList<>(Arrays.asList(1.0, 10.0)));
		source.setParameter(operation.getDb(), parameter);
		for (CSG part : operation.process(Arrays.asList(source))) {
			assertTrue(part.getParameters(operation.getDb()).isEmpty());
			assertSame(part, part.regenerate());
		}
		assertTrue(source.getParameters(operation.getDb()).contains("upstream"));
	}

}
