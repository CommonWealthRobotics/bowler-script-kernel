package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;

import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleOperation;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.ModifyLimb;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;

/**
 * Doodle-208 regression test. Loads
 * testStepFiles/Doodle-208-Mirthful_Gravicycle.doodle, which holds a robot
 * with a controller, a wheel limb and two limb base moves, then drags the
 * wheel once more the way the base handle does. Stepping the undo stack back
 * has to roll the wheel base through every location in reverse, ending at the
 * spot the limb was added at, and redo has to re-apply the moves in order.
 */
public class Doodle208ModifyLimbUndoTest {
	// base locations committed by the ModifyLimb operations in the fixture
	private static final TransformNR ADD_LOCATION = new TransformNR(90, 57, 9);
	private static final TransformNR FIRST_MOVE = new TransformNR(21, 59, 9);
	private static final TransformNR SECOND_MOVE = new TransformNR(22, 17, 9);
	// task space targets stored alongside those moves
	private static final TransformNR FIRST_MOVE_TIP = new TransformNR(44, 59, 9);
	private static final TransformNR SECOND_MOVE_TIP = new TransformNR(45, 17, 9);
	private static final double TOLERANCE_MM = 0.1;

	private static CaDoodleFile cf;
	private static MobileBase robotBase;
	private static String limbName;

	@Before
	public void setup() throws Exception {
		BowlerKernel.startupProcedures();
		JavaFXInitializer.go();
	}

	@Test
	public void test() throws Exception {
		Log.enableDebugPrint();
		File fixture = new File("testStepFiles/Doodle-208-Mirthful_Gravicycle.doodle");
		assertTrue("Fixture missing: " + fixture.getAbsolutePath(), fixture.exists());
		cf = CaDoodleFile.fromFile(fixture);

		// the fixture shape: one limb added, two moves recorded on it
		AddRobotLimb addLimb = null;
		int modifyCount = 0;
		for (CaDoodleOperation op : cf.getOperations()) {
			if (AddRobotLimb.class.isInstance(op))
				addLimb = (AddRobotLimb) op;
			if (ModifyLimb.class.isInstance(op))
				modifyCount++;
		}
		assertNotNull("The fixture has no limb", addLimb);
		assertEquals("The fixture should hold two limb moves", 2, modifyCount);
		limbName = addLimb.getName();
		MobileBaseBuilder builder = cf.getRobots().get(addLimb.getBuilderName());
		assertNotNull("The robot builder is missing after load", builder);
		robotBase = builder.getMobileBase();
		DHParameterKinematics limb = robotBase.getLimbByName(limbName);
		assertNotNull("The wheel limb is missing after load", limb);
		ArrayList<String> wheelParts = new ArrayList<String>();
		for (CSG c : cf.getCurrentState())
			if (c.getLimbName().isPresent() && c.getLimbName().get().contentEquals(limbName))
				wheelParts.add(c.getName());
		assertFalse("The wheel CAD is missing from the loaded state", wheelParts.isEmpty());

		// 1: loading the file leaves the limb at the last stored move
		DHParameterKinematics loaded = robotBase.getLimbByName(limbName);
		assertBase("after load", SECOND_MOVE, loaded);
		awaitTip(loaded, SECOND_MOVE_TIP, "load");
		assertTip("after load", SECOND_MOVE_TIP, loaded);

		// 2: moving the limb the way the base handle does sets the base
		TransformNR beforeMove = loaded.getRobotToFiducialTransform().copy();
		TransformNR moveTarget = new TransformNR(beforeMove.getX() + 10, beforeMove.getY() + 15, beforeMove.getZ())
				.times(new TransformNR(beforeMove.getRotation()));
		TransformNR moveTargetTip = new TransformNR(SECOND_MOVE_TIP.getX() + 10, SECOND_MOVE_TIP.getY() + 15,
				SECOND_MOVE_TIP.getZ());
		ModifyLimb move = new ModifyLimb().setLimb(loaded).setNames(new ArrayList<String>(wheelParts))
				.setBase(moveTarget);
		cf.addOperation(move).join();
		DHParameterKinematics moved = robotBase.getLimbByName(limbName);
		assertBase("after the move", moveTarget, moved);
		awaitTip(moved, moveTargetTip, "move");
		assertTip("after the move", moveTargetTip, moved);

		// 3: one undo moves the base back to where it was before the move
		cf.back();
		DHParameterKinematics afterUndo = robotBase.getLimbByName(limbName);
		assertBase("after undoing the move", beforeMove, afterUndo);
		awaitTip(afterUndo, SECOND_MOVE_TIP, "move undo");
		assertTip("after undoing the move", SECOND_MOVE_TIP, afterUndo);

		// 4: redo re-applies the move
		cf.forward();
		DHParameterKinematics afterRedo = robotBase.getLimbByName(limbName);
		assertBase("after redoing the move", moveTarget, afterRedo);
		awaitTip(afterRedo, moveTargetTip, "move redo");

		// 5: undoing past the fixture's own two moves must keep releasing
		// the base: each step has to hand the location back to the previous
		// operation instead of stalling on the undone one
		cf.back();
		assertBase("after undoing the move again", SECOND_MOVE, robotBase.getLimbByName(limbName));
		cf.back();
		DHParameterKinematics afterUndoSecond = robotBase.getLimbByName(limbName);
		assertBase("after undoing the fixture's second move", FIRST_MOVE, afterUndoSecond);
		awaitTip(afterUndoSecond, FIRST_MOVE_TIP, "second move undo");
		assertTip("after undoing the fixture's second move", FIRST_MOVE_TIP, afterUndoSecond);
		cf.back();
		DHParameterKinematics afterUndoFirst = robotBase.getLimbByName(limbName);
		assertNotNull("The limb must survive undoing its moves", afterUndoFirst);
		assertBase("after undoing the fixture's first move", ADD_LOCATION, afterUndoFirst);

		// 6: redo the whole way forward through every location
		cf.forward();
		assertBase("after redoing the fixture's first move", FIRST_MOVE, robotBase.getLimbByName(limbName));
		cf.forward();
		assertBase("after redoing the fixture's second move", SECOND_MOVE, robotBase.getLimbByName(limbName));
		cf.forward();
		assertBase("after redoing the move", moveTarget, robotBase.getLimbByName(limbName));

		for (MobileBase mb : cf.getMobileBases())
			mb.disconnect();
		cf.close();
	}

	private static void assertBase(String when, TransformNR expected, DHParameterKinematics limb) {
		TransformNR actual = limb.getRobotToFiducialTransform();
		assertEquals(when + " base X", expected.getX(), actual.getX(), TOLERANCE_MM);
		assertEquals(when + " base Y", expected.getY(), actual.getY(), TOLERANCE_MM);
		assertEquals(when + " base Z", expected.getZ(), actual.getZ(), TOLERANCE_MM);
		log(when + " base=" + actual.toSimpleString());
	}

	private static void assertTip(String when, TransformNR expected, DHParameterKinematics limb) {
		TransformNR actual = limb.getCurrentTaskSpaceTransform();
		assertEquals(when + " tip X", expected.getX(), actual.getX(), TOLERANCE_MM);
		assertEquals(when + " tip Y", expected.getY(), actual.getY(), TOLERANCE_MM);
		assertEquals(when + " tip Z", expected.getZ(), actual.getZ(), TOLERANCE_MM);
	}

	// polls until the limb's tip converges on the target or times out; the
	// virtual device interpolates to set points asynchronously
	private static void awaitTip(DHParameterKinematics limb, TransformNR target, String what)
			throws InterruptedException {
		long deadline = System.currentTimeMillis() + 10_000;
		double best = Double.MAX_VALUE;
		while (System.currentTimeMillis() < deadline) {
			TransformNR current = limb.getCurrentTaskSpaceTransform();
			best = Math.abs(current.getX() - target.getX()) + Math.abs(current.getY() - target.getY())
					+ Math.abs(current.getZ() - target.getZ());
			if (best < TOLERANCE_MM)
				return;
			Thread.sleep(50);
		}
		log(what + " tip never converged, residual=" + best + " target=" + target.toSimpleString() + " current="
				+ limb.getCurrentTaskSpaceTransform().toSimpleString());
	}

	private static void log(String message) {
		com.neuronrobotics.sdk.common.Log.debug(message);
		System.out.println("TEST " + message);
	}

}
