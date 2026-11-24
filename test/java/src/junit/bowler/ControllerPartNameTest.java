package junit.bowler;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.neuronrobotics.bowlerstudio.creature.ControllerOption;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotController;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;

public class ControllerPartNameTest {
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void generatedPartsHaveReadableNamesOnCreationAndReplay() throws Exception {
		Fixture fixture = new Fixture();
		for (int replay = 0; replay < 2; replay++) {
			List<CSG> result = fixture.operation.process(Collections.singletonList(fixture.incoming));
			assertEquals(3, result.size());
			assertSame(fixture.incoming, result.get(0));
			assertEquals("Existing robot", result.get(0).getUserDefinedName());
			for (int i = 0; i < 2; i++) {
				CSG part = result.get(i + 1);
				assertTrue(part.isUserDefinedName());
				assertEquals("Controller part " + (i + 1), part.getUserDefinedName());
				assertEquals(fixture.operation.getName() + (i == 0 ? "" : "_" + i), part.getName());
				assertEquals("robot-id", part.getMobileBaseName().get());
			}
		}
		assertFalse(fixture.first.isUserDefinedName());
		assertFalse(fixture.second.isUserDefinedName());
		assertEquals("script-internal-97", fixture.first.getName());
		assertEquals("script-internal-42", fixture.second.getName());
		verify(fixture.builder, times(2)).addController(fixture.operation);
	}

	@Test
	public void scriptAuthoredNamesArePreservedAndCountTowardsOutputIndex() throws Exception {
		Fixture fixture = new Fixture();
		fixture.first.setUserDefinedName("Motor driver board");
		for (int replay = 0; replay < 2; replay++) {
			List<CSG> result = fixture.operation.process(Collections.singletonList(fixture.incoming));
			assertEquals("Motor driver board", result.get(1).getUserDefinedName());
			assertEquals("Controller part 2", result.get(2).getUserDefinedName());
			assertEquals(fixture.operation.getName(), result.get(1).getName());
			assertEquals(fixture.operation.getName() + "_1", result.get(2).getName());
		}
		assertEquals("Motor driver board", fixture.first.getUserDefinedName());
		assertFalse(fixture.second.isUserDefinedName());
	}

	@Test
	public void emptyControllerDoesNotRenameIncomingParts() throws Exception {
		Fixture fixture = new Fixture();
		when(fixture.controller.getVitaminNumber()).thenReturn(0);
		List<CSG> result = fixture.operation.process(Collections.singletonList(fixture.incoming));
		assertEquals(1, result.size());
		assertSame(fixture.incoming, result.get(0));
		assertEquals("Existing robot", result.get(0).getUserDefinedName());
	}

	private class Fixture {
		final CaDoodleFile file = new CaDoodleFile();
		final ControllerOption controller = mock(ControllerOption.class);
		final MobileBaseBuilder builder = mock(MobileBaseBuilder.class);
		final AddRobotController operation = new AddRobotController();
		final CSG incoming = new Cube(1).toCSG().setName("existing-id").setUserDefinedName("Existing robot");
		final CSG first = new Cube(1).toCSG().setName("script-internal-97");
		final CSG second = new Cube(2).toCSG().setName("script-internal-42");

		Fixture() throws Exception {
			file.setSelf(new File(temporary.newFolder(), "controller.doodle"));
			file.getRobots().put("robot-id", builder);
			incoming.setMobileBaseName("robot-id");
			operation.setCaDoodleFile(file);
			operation.setController(controller).setNames(Collections.singletonList(incoming.getName()));
			when(controller.getVitaminNumber()).thenReturn(2);
			when(controller.getVitaminCSG(file.getCsgDBinstance(), 0)).thenReturn(first);
			when(controller.getVitaminCSG(file.getCsgDBinstance(), 1)).thenReturn(second);
			when(controller.getVitaminPose(0)).thenReturn(new TransformNR());
			when(controller.getVitaminPose(1)).thenReturn(new TransformNR(10, 0, 0));
		}
	}
}
