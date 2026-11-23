package junit.bowler;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neuronrobotics.bowlerstudio.creature.ImagePorviderInterface;
import com.neuronrobotics.bowlerstudio.creature.LimbOption;
import com.neuronrobotics.bowlerstudio.creature.LimbType;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleOperation;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ICaDoodleStateUpdate;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.ModifyLimb;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

/** Offline model regression; no CAD scripts, startup, login, or remote repositories. */
public class OfflineLimbRedoTest {
	private static final File FIXTURE = new File("testStepFiles/limb-redo");
	private static final Gson GSON = new Gson();

	@Test
	public void initializeRestoresLoggedCursorAndSupportsLimbRedo() throws Exception {
		exercise(11);
	}

	@Test
	public void initializeRestoresSavedCursorAndSupportsLimbRedo() throws Exception {
		exercise(9);
	}

	@Test
	public void initializeAtHistoryEndSupportsLimbRedo() throws Exception {
		exercise(16);
	}

	@Test
	public void initializeBeforeLimbAdditionsSupportsLimbRedo() throws Exception {
		exercise(5);
	}

	private void exercise(int startingCursor) throws Exception {
		String json = new String(Files.readAllBytes(new File(FIXTURE, "history.doodle").toPath()),
				StandardCharsets.UTF_8);
		JsonArray entries = new JsonParser().parse(json).getAsJsonObject().getAsJsonArray("opperations");
		// Deserialize before replacing the remote-only setup operations; never execute those scripts.
		CaDoodleFile file = CaDoodleFile.fromJsonString(json, null, null, false);
		Files.createDirectories(new File("build").toPath());
		Path output = Files.createTempDirectory(new File("build").toPath(), "offline-limb-redo-");
		file.setSelf(output.resolve("history.doodle").toFile());
		java.lang.reflect.Field imageField = CaDoodleFile.class.getDeclaredField("imageEngine");
		imageField.setAccessible(true);
		ImagePorviderInterface originalImageEngine = (ImagePorviderInterface) imageField.get(null);
		MobileBaseCadManager renderer = mock(MobileBaseCadManager.class,
				(org.mockito.stubbing.Answer<Object>) invocation -> {
					if (invocation.getMethod().getName().equals("generateCad")) {
						ArrayList<CSG> shapes = new ArrayList<>();
						// Fixture selections reference limb mesh suffixes through _7.
						for (int i = 0; i < 8; i++)
							shapes.add(new Cube(1).toCSG());
						return shapes;
					}
					return null;
				});
		MobileBaseBuilder builder = new MobileBaseBuilder(file.getCsgDBinstance(), output.toAbsolutePath().toString(),
				"offline-robot") {
			@Override
			public MobileBaseCadManager getCadManager() {
				return renderer;
			}
		};
		builder.setXmlName("robot.xml");
		AddRobotLimb wheel = (AddRobotLimb) file.getOperations().get(5);
		AddRobotLimb leg = (AddRobotLimb) file.getOperations().get(8);
		file.getRobots().put(wheel.getBuilderName(), builder);
		List<String> errors = new ArrayList<>();
		List<DHParameterKinematics> created = new ArrayList<>();
		try {
			CaDoodleFile.setImageEngine(mock(ImagePorviderInterface.class));
			for (CaDoodleOperation operation : file.getOperations())
				operation.setCaDoodleFile(file);
			configure(wheel, data(entries, 6), "drivable", LimbType.wheel, created);
			configure(leg, data(entries, 9), "leg", LimbType.leg, created);
			// Replace only remote shape/controller setup; real limb process/undo/redo remains intact.
			for (int i = 0; i < 5; i++) {
				file.getOperations().set(i,
						mock(CaDoodleOperation.class, (org.mockito.stubbing.Answer<Object>) invocation -> {
							String method = invocation.getMethod().getName();
							if (method.equals("process"))
								return invocation.getArguments()[0];
							if (method.equals("getNamesAddedInThisOperation"))
								return java.util.Collections.emptyList();
							if (method.equals("getType"))
								return "offline setup";
							return null;
						}));
			}
			file.setCurrentIndex(startingCursor);
			file.initialize();
			assertEquals("Restored history cursor", startingCursor, file.getCurrentIndex());
			for (int i = 5; i < file.getOperations().size(); i++) {
				CaDoodleOperation op = file.getOperations().get(i);
				if (op instanceof ModifyLimb)
					assertNotNull("Modification was processed at index " + i, ((ModifyLimb) op).getLimb());
			}
			inspect("initialized cursor " + startingCursor, file, builder, entries, errors);
			// Observers inspect the real model; neither callback repairs or changes it.
			file.addListener(mock(ICaDoodleStateUpdate.class, (org.mockito.stubbing.Answer<Object>) invocation -> {
				String event = invocation.getMethod().getName();
				if (event.equals("onUpdate") || event.equals("onSaveSuggestion"))
					inspect(event + " cursor " + file.getCurrentIndex(), file, builder, entries, errors);
				return null;
			}));
			DHParameterKinematics oldWheel = wheel.getKinematics();
			DHParameterKinematics oldLeg = leg.getKinematics();
			double[] removedWheel = pose(oldWheel);
			double[] removedLeg = pose(oldLeg);
			for (int cursor = startingCursor - 1; cursor >= 5; cursor--) {
				file.back();
				inspect("undo " + cursor, file, builder, entries, errors);
				if (cursor == 8)
					removedLeg = pose(oldLeg);
				if (cursor == 5)
					removedWheel = pose(oldWheel);
				if (cursor <= 8)
					compare("undo " + cursor + " detached leg", removedLeg, pose(oldLeg), errors);
			}
			for (int cursor = 6; cursor <= file.getOperations().size(); cursor++) {
				file.forward();
				inspect("redo " + cursor, file, builder, entries, errors);
				compare("redo " + cursor + " detached wheel", removedWheel, pose(oldWheel), errors);
				compare("redo " + cursor + " detached leg", removedLeg, pose(oldLeg), errors);
				if (cursor == 6 && oldWheel == builder.getMobileBase().getLimbByName(wheel.getName()))
					errors.add("redo 6 reused deleted wheel");
				if (cursor == 9 && oldLeg == builder.getMobileBase().getLimbByName(leg.getName()))
					errors.add("redo 9 reused deleted leg");
			}
			assertTrue("Builder must write only to its local directory",
					Files.isRegularFile(output.resolve("robot.xml")));
			assertTrue(String.join("\n", errors), errors.isEmpty());
		} finally {
			for (DHParameterKinematics limb : created)
				limb.disconnect();
			builder.getMobileBase().disconnect();
			CaDoodleFile.setImageEngine(originalImageEngine);
			try (java.util.stream.Stream<Path> paths = Files.walk(output)) {
				for (Path path : paths.sorted(java.util.Comparator.reverseOrder())
						.collect(java.util.stream.Collectors.toList()))
					Files.deleteIfExists(path);
			}
		}
	}

	private static void configure(AddRobotLimb add, JsonObject firstMove, String tag, LimbType type,
			List<DHParameterKinematics> created) throws Exception {
		Element template = (Element) DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new File(FIXTURE, "robot.xml")).getElementsByTagName(tag).item(0);
		for (String engine : new String[]{"cadEngine", "kinematics"}) {
			org.w3c.dom.Node node = template.getElementsByTagName(engine).item(0);
			if (node != null)
				node.getParentNode().removeChild(node);
		}
		TransformNR addedBase = GSON.fromJson(firstMove.get("basePrevious"), TransformNR.class);
		add.forceLoad = true;
		add.setLimb(new LimbOption() {
			@Override
			public LimbType getType() {
				return type;
			}

			@Override
			public DHParameterKinematics getLimb(CSGDatabaseInstance db, String name) {
				DHParameterKinematics limb = new DHParameterKinematics(null, (Element) template.cloneNode(true));
				limb.setScriptingName(name);
				// Saved XML contains the final registration; reconstruct the as-added registration.
				limb.setRobotToFiducialTransform(add.getLocation().inverse().times(addedBase));
				created.add(limb);
				return limb;
			}
		});
	}

	private static JsonObject data(JsonArray entries, int index) {
		return entries.get(index).getAsJsonObject().getAsJsonObject("data");
	}

	private static void inspect(String step, CaDoodleFile file, MobileBaseBuilder builder, JsonArray entries,
			List<String> errors) {
		int cursor = file.getCurrentIndex();
		for (int addIndex : new int[]{5, 8}) {
			AddRobotLimb add = (AddRobotLimb) file.getOperations().get(addIndex);
			DHParameterKinematics live = builder.getMobileBase().getLimbByName(add.getName());
			if (cursor <= addIndex) {
				if (live != null)
					errors.add(step + " limb should be absent: " + add.getName());
				continue;
			}
			if (live == null) {
				errors.add(step + " limb missing: " + add.getName());
				continue;
			}
			TransformNR base = GSON.fromJson(data(entries, addIndex + 1).get("basePrevious"), TransformNR.class);
			double[] joints = GSON.fromJson(data(entries, addIndex + 1).get("jointHomeValuesPrevious"), double[].class);
			for (int i = addIndex + 1; i < cursor; i++) {
				JsonObject d = data(entries, i);
				if (!d.has("limbName") || !d.get("limbName").getAsString().equals(add.getName()))
					continue;
				if (d.has("base"))
					base = GSON.fromJson(d.get("base"), TransformNR.class);
				if (d.has("jointHomeValues"))
					joints = GSON.fromJson(d.get("jointHomeValues"), double[].class);
			}
			// The next edit's snapshot includes interactive changes made before that edit was committed.
			for (int i = Math.max(cursor, addIndex + 1); i < entries.size(); i++) {
				JsonObject d = data(entries, i);
				if (!d.has("limbName") || !d.get("limbName").getAsString().equals(add.getName()))
					continue;
				if (d.has("basePrevious"))
					base = GSON.fromJson(d.get("basePrevious"), TransformNR.class);
				if (d.has("jointHomeValuesPrevious"))
					joints = GSON.fromJson(d.get("jointHomeValuesPrevious"), double[].class);
				break;
			}
			double[] expected = new double[3 + joints.length];
			expected[0] = base.getX();
			expected[1] = base.getY();
			expected[2] = base.getZ();
			System.arraycopy(joints, 0, expected, 3, joints.length);
			compare(step + " live " + add.getName(), expected, pose(live), errors);
		}
	}

	private static double[] pose(DHParameterKinematics limb) {
		TransformNR base = limb.getRobotToFiducialTransform();
		double[] joints = limb.getCurrentJointSpaceTarget();
		double[] result = new double[3 + joints.length];
		result[0] = base.getX();
		result[1] = base.getY();
		result[2] = base.getZ();
		System.arraycopy(joints, 0, result, 3, joints.length);
		return result;
	}

	private static void compare(String step, double[] expected, double[] actual, List<String> errors) {
		if (expected.length != actual.length) {
			errors.add(step + " vector lengths differ: " + expected.length + " != " + actual.length);
			return;
		}
		for (int i = 0; i < expected.length; i++) {
			if (!Double.isFinite(actual[i]) || !Double.isFinite(expected[i])
					|| Math.abs(expected[i] - actual[i]) > 1e-5) {
				errors.add(step + " expected=" + Arrays.toString(expected) + " actual=" + Arrays.toString(actual));
				return;
			}
		}
	}

}
