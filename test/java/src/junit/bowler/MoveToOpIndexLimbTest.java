package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleOperation;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.JavaFXInitializer;

/**
 * The timeline strip, the component tree and the MCP API navigate with
 * moveToOpIndex, not with back()/forward(). Stepping onto a timeline entry
 * has to leave the robot in exactly the state that entry produced: the op
 * on the cursor applied, everything after it undone.
 */
public class MoveToOpIndexLimbTest {
	// base locations committed by the fixture's two ModifyLimb operations
	private static final TransformNR ADD_LOCATION = new TransformNR(90, 57, 9);
	private static final TransformNR FIRST_MOVE = new TransformNR(21, 59, 9);
	private static final TransformNR SECOND_MOVE = new TransformNR(22, 17, 9);
	private static final double TOLERANCE_MM = 0.1;

	private CaDoodleFile cf;
	private MobileBase robotBase;
	private String limbName;
	private int addLimbIndex;
	private int firstMoveIndex;
	private int secondMoveIndex;

	@Before
	public void setup() throws Exception {
		BowlerKernel.startupProcedures();
		JavaFXInitializer.go();
		File fixture = new File("testStepFiles/Doodle-208-Mirthful_Gravicycle.doodle");
		assertTrue("Fixture missing: " + fixture.getAbsolutePath(), fixture.exists());
		cf = CaDoodleFile.fromFile(fixture);
		AddRobotLimb addLimb = null;
		for (int i = 0; i < cf.getOperations().size(); i++) {
			CaDoodleOperation op = cf.getOperations().get(i);
			if (AddRobotLimb.class.isInstance(op)) {
				addLimb = (AddRobotLimb) op;
				addLimbIndex = i;
			}
			if (op instanceof com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.ModifyLimb) {
				if (firstMoveIndex == 0 && secondMoveIndex == 0)
					firstMoveIndex = i;
				else
					secondMoveIndex = i;
			}
		}
		assertNotNull("The fixture has no limb", addLimb);
		limbName = addLimb.getName();
		MobileBaseBuilder builder = cf.getRobots().get(addLimb.getBuilderName());
		assertNotNull("The robot builder is missing after load", builder);
		robotBase = builder.getMobileBase();
	}

	// stepping onto the first move op must show exactly that move: both the
	// old loop bounds undershoot/overshoot by one entry
	private void assertAtFirstMove(String when) {
		assertEquals(when + ": cursor on the first move", firstMoveIndex + 1, cf.getCurrentIndex());
		assertBase(when, FIRST_MOVE);
	}

	@Test
	public void test() throws Exception {
		// walk back onto the first move op: the base must land on FIRST_MOVE
		cf.moveToOpIndex(firstMoveIndex);
		assertAtFirstMove("timeline jump back to the first move");

		// walking forward onto the second move op must apply it exactly
		cf.moveToOpIndex(secondMoveIndex);
		assertEquals("cursor on the second move", secondMoveIndex + 1, cf.getCurrentIndex());
		assertBase("timeline jump forward to the second move", SECOND_MOVE);

		// back to the limb add: the base must return to the as-added spot
		cf.moveToOpIndex(addLimbIndex);
		assertEquals("cursor on the limb add", addLimbIndex + 1, cf.getCurrentIndex());
		assertBase("timeline jump back to the limb add", ADD_LOCATION);

		for (MobileBase mb : cf.getMobileBases())
			mb.disconnect();
		cf.close();
	}

	private void assertBase(String when, TransformNR expected) {
		DHParameterKinematics limb = robotBase.getLimbByName(limbName);
		assertNotNull(when + ": the limb is missing", limb);
		TransformNR actual = limb.getRobotToFiducialTransform();
		assertEquals(when + " base X", expected.getX(), actual.getX(), TOLERANCE_MM);
		assertEquals(when + " base Y", expected.getY(), actual.getY(), TOLERANCE_MM);
		assertEquals(when + " base Z", expected.getZ(), actual.getZ(), TOLERANCE_MM);
	}

}
