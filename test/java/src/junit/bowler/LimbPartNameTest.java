package junit.bowler;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.ModifyLimb;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;

public class LimbPartNameTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void addedPartsHaveReadableSequentialNamesOnEveryReplay() throws Exception {
		Fixture fixture = new Fixture();
		for (int replay = 0; replay < 2; replay++) {
			List<CSG> result = fixture.add.process(Collections.singletonList(fixture.untouched));
			assertParts(fixture, result, fixture.add.getName());
		}
	}

	@Test
	public void rebuiltPartsKeepReadableNamesAndRestartTheirOutputIndex() throws Exception {
		Fixture fixture = new Fixture();
		List<CSG> incoming = fixture.add.process(Collections.singletonList(fixture.untouched));
		ModifyLimb modify = fixture.modify(incoming.get(1));
		modify.setElbow(new TransformNR());
		for (int replay = 0; replay < 2; replay++)
			assertParts(fixture, modify.process(incoming), modify.getName());
	}

	@Test
	public void poseOnlyChangesPreserveUserRenamedParts() throws Exception {
		Fixture fixture = new Fixture();
		List<CSG> incoming = fixture.add.process(Collections.singletonList(fixture.untouched));
		CSG part = incoming.get(1).setUserDefinedName("My joint");
		ModifyLimb modify = fixture.modify(part);
		List<CSG> result = modify.process(incoming);
		assertSame(part, result.get(1));
		assertEquals("My joint", part.getUserDefinedName());
	}

	@Test
	public void cadAuthoredDisplayNamesArePreservedAndStillCountAsOutputs() throws Exception {
		Fixture fixture = new Fixture();
		when(fixture.manager.generateCad(fixture.file.getCsgDBinstance(), fixture.limb))
				.thenAnswer(invocation -> new ArrayList<>(
						Arrays.asList(new Cube(1).toCSG().setUserDefinedName("Elbow housing"), new Cube(2).toCSG())));
		List<CSG> added = fixture.add.process(Collections.singletonList(fixture.untouched));
		assertEquals("Elbow housing", added.get(1).getUserDefinedName());
		assertEquals("Limb part 2", added.get(2).getUserDefinedName());
		List<CSG> rebuilt = fixture.modify(added.get(1)).setElbow(new TransformNR()).process(added);
		assertEquals("Elbow housing", rebuilt.get(1).getUserDefinedName());
		assertEquals("Limb part 2", rebuilt.get(2).getUserDefinedName());
	}

	private void assertParts(Fixture fixture, List<CSG> result, String operationId) {
		assertEquals(3, result.size());
		assertSame(fixture.untouched, result.get(0));
		assertEquals("Keep me", result.get(0).getUserDefinedName());
		for (int i = 0; i < 2; i++) {
			CSG part = result.get(i + 1);
			assertTrue(part.isUserDefinedName());
			assertEquals("Limb part " + (i + 1), part.getUserDefinedName());
			assertEquals(operationId + (i == 0 ? "" : "_" + i), part.getName());
			assertEquals(fixture.add.getName(), part.getLimbName().get());
			assertEquals("robot-id", part.getMobileBaseName().get());
		}
	}

	private class Fixture {
		final CaDoodleFile file = new CaDoodleFile();
		final AddRobotLimb add = new AddRobotLimb();
		final DHParameterKinematics limb = mock(DHParameterKinematics.class);
		final MobileBaseCadManager manager = mock(MobileBaseCadManager.class);
		final CSG untouched = new Cube(1).toCSG().setName("untouched-id").setUserDefinedName("Keep me");

		Fixture() throws Exception {
			file.setSelf(new File(temporary.newFolder(), "limb.doodle"));
			MobileBaseBuilder builder = mock(MobileBaseBuilder.class);
			MobileBase base = mock(MobileBase.class);
			file.getRobots().put("robot-id", builder);
			add.setCaDoodleFile(file);
			add.setBuilderName("robot-id");
			when(builder.getMobileBase()).thenReturn(base);
			when(builder.getCadManager()).thenReturn(manager);
			when(base.getLimbByName(add.getName())).thenReturn(limb);
			when(limb.getRobotToFiducialTransform()).thenReturn(new TransformNR());
			when(limb.getCurrentJointSpaceTarget()).thenReturn(new double[]{0});
			when(manager.generateCad(file.getCsgDBinstance(), limb)).thenAnswer(
					invocation -> new ArrayList<>(Arrays.asList(new Cube(1).toCSG().setName("generated-id-17"),
							new Cube(2).toCSG().setName("generated-id-42"))));
		}

		ModifyLimb modify(CSG part) {
			ModifyLimb operation = new ModifyLimb().setNames(Collections.singletonList(part.getName()));
			operation.setCaDoodleFile(file);
			return operation;
		}
	}
}
