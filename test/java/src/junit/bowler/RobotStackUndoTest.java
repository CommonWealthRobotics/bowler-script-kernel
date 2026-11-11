package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.ControllerOption;
import com.neuronrobotics.bowlerstudio.creature.LimbOption;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AddFromScript;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.NameMissingException;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotController;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.MakeRobot;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.ModifyLimb;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;

/**
 * End to end test of the robotics stack: doodle file, object, robot,
 * controller, wheel limb, move the wheel, move its set point, then undo past
 * the limb add, verify the wheel is gone, redo forward and verify the final
 * base and tip locations match the pre-undo state.
 */
public class RobotStackUndoTest {
	private static final double TOLERANCE_MM = 0.1;

	@Before
	public void setup() throws Exception {
		BowlerKernel.startupProcedures();
		JavaFXInitializer.go();
	}

	@Test
	public void test() throws Exception {
		Log.enableDebugPrint();
		CaDoodleFile cf = new CaDoodleFile().setSelf(new File("doodle/RobotStackUndoTest.doodle"))
				.setProjectName("Robot Stack Undo Test");
		cf.initialize();

		// 1: make a CaDoodle file with an object in it
		AddFromScript body = new AddFromScript().set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
				"sphere.groovy");
		cf.addOperation(body).join();
		ArrayList<String> selected = new ArrayList<String>(body.getNamesAddedInThisOperation());
		assertEquals("Adding the body should have added one object", 1, selected.size());
		log("STEP1 body added, object=" + selected.get(0) + " ops=" + cf.getOperations().size());

		// 2: make that object a robot
		MakeRobot makeRobot = new MakeRobot();
		makeRobot.setNames(selected);
		cf.addOperation(makeRobot).join();
		String robotName = makeRobot.getName();
		assertNotNull("MakeRobot should register a builder", cf.getRobots().get(robotName));
		assertEquals("The robot starts with no limbs", 0,
				cf.getRobots().get(robotName).getMobileBase().getAllDHChains().size());
		log("STEP2 robot=" + robotName + " ops=" + cf.getOperations().size());

		// 3: add a microbit controller so the wheel is powered
		ScriptingEngine.pull(ControllerOption.URL_OF_OPTIONS);
		ControllerOption microbit = null;
		for (ControllerOption o : ControllerOption.getOptions()) {
			if (o.getProvides().getServoChannels() >= 2) {
				microbit = o;
				break;
			}
		}
		if (microbit == null)
			fail("No controller with two servo channels found in the controller options");
		AddRobotController addController = new AddRobotController().setController(microbit).setNames(selected);
		cf.addOperation(addController).join();
		log("STEP3 controller=" + microbit.getType() + " servoChannels=" + microbit.getProvides().getServoChannels()
				+ " ops=" + cf.getOperations().size());

		// 4: add a wheel limb, forced on so the test does not depend on the
		// feature metadata published by the controller options file
		ScriptingEngine.pull("https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git");
		LimbOption wheelOption = null;
		for (LimbOption o : LimbOption.getOptions()) {
			if ("wheel".contentEquals(o.getType().toString()) && "Servo Fixed Wheel".contentEquals(o.getName())) {
				wheelOption = o;
				break;
			}
		}
		if (wheelOption == null)
			fail("Servo Fixed Wheel option not found in the limb options");
		TransformNR limbLocation = new TransformNR(20, 0, 0).times(LimbOption.LimbRotationOffset);
		AddRobotLimb addLimb = new AddRobotLimb().setLimb(wheelOption).setLocation(limbLocation).setNames(selected);
		addLimb.forceLoad = true;
		cf.addOperation(addLimb).join();
		ArrayList<String> limbPartNames = new ArrayList<String>(addLimb.getNamesAddedInThisOperation());
		assertFalse("Adding a wheel should add CAD for it", limbPartNames.isEmpty());
		log("STEP4 wheel=" + addLimb.getName() + " parts=" + limbPartNames + " ops=" + cf.getOperations().size());
		MobileBase withLimb = cf.getRobots().get(robotName).getMobileBase();
		DHParameterKinematics wheel = withLimb.getLimbByName(addLimb.getName());
		assertNotNull("The wheel limb is missing after it was added", wheel);
		log("STEP4 wheel base=" + wheel.getRobotToFiducialTransform().toSimpleString() + " tip="
				+ wheel.getCurrentTaskSpaceTransform().toSimpleString() + " joints="
				+ Arrays.toString(wheel.getCurrentJointSpaceTarget()));
		for (String n : limbPartNames) {
			CSG part = CaDoodleFile.getByName(cf.getCurrentState(), n);
			assertTrue("Wheel part " + n + " should be tagged with the limb", part.getLimbName().isPresent());
		}

		// 5: move the wheel object. Wheel CAD is motion locked, so in the app
		// this gesture is the limb base handle, which commits a ModifyLimb
		// with a new base, not a MoveCenter operation
		TransformNR wheelBaseBeforeMove = wheel.getRobotToFiducialTransform().copy();
		TransformNR wheelTipBeforeMove = wheel.getCurrentTaskSpaceTransform().copy();
		TransformNR baseAfter = new TransformNR(wheelBaseBeforeMove.getX(), wheelBaseBeforeMove.getY() + 15,
				wheelBaseBeforeMove.getZ()).times(new TransformNR(wheelBaseBeforeMove.getRotation()));
		ModifyLimb moveBase = new ModifyLimb().setLimb(wheel).setNames(new ArrayList<String>(limbPartNames))
				.setBase(baseAfter);
		cf.addOperation(moveBase).join();
		DHParameterKinematics movedWheel = cf.getRobots().get(robotName).getMobileBase()
				.getLimbByName(addLimb.getName());
		TransformNR wheelBaseAfterMove = movedWheel.getRobotToFiducialTransform().copy();
		TransformNR wheelTipAfterMove = movedWheel.getCurrentTaskSpaceTransform().copy();
		log("STEP5 base after=" + wheelBaseAfterMove.toSimpleString() + " tip after="
				+ wheelTipAfterMove.toSimpleString());
		assertPosition("wheel base after the move", baseAfter, wheelBaseAfterMove);
		assertEquals("The tip should slide 15mm in Y with the base", 15,
				wheelTipAfterMove.getY() - wheelTipBeforeMove.getY(), TOLERANCE_MM);
		assertEquals("The tip should keep its height while the base slides", wheelTipBeforeMove.getZ(),
				wheelTipAfterMove.getZ(), TOLERANCE_MM);

		// 6: move the wheel set point the way the tip handle does: state the
		// target as the forward kinematics of a joint-space vector driven off
		// the current target, so it is reachable by construction
		double[] jointsBefore = movedWheel.getCurrentJointSpaceTarget();
		double[] jointsTarget = new double[jointsBefore.length];
		for (int i = 0; i < jointsBefore.length; i++)
			jointsTarget[i] = jointsBefore[i] + 20;
		TransformNR target = movedWheel.forwardOffset(movedWheel.forwardKinematics(jointsTarget));
		ModifyLimb modify = new ModifyLimb().setLimb(movedWheel).setNames(new ArrayList<String>(limbPartNames))
				.setTip(target);
		cf.addOperation(modify).join();
		DHParameterKinematics afterTipMove = cf.getRobots().get(robotName).getMobileBase()
				.getLimbByName(addLimb.getName());
		// the virtual device interpolates to the set point asynchronously, so
		// wait for convergence before taking any measurements
		awaitTip(afterTipMove, target, "set point");
		TransformNR tipAfterSetpoint = afterTipMove.getCurrentTaskSpaceTransform().copy();
		assertTrue("The tip should reach toward the moved set point, tip=" + tipAfterSetpoint,
				Math.abs(tipAfterSetpoint.getZ() - target.getZ()) < 1);
		double tipTravel = Math.abs(tipAfterSetpoint.getZ() - wheelTipAfterMove.getZ());
		assertTrue("The tip must stay on the wheel rim while the set point rotates it, travel=" + tipTravel,
				tipTravel < 1);
		double xyTravel = Math.abs(tipAfterSetpoint.getX() - wheelTipAfterMove.getX())
				+ Math.abs(tipAfterSetpoint.getY() - wheelTipAfterMove.getY());
		assertTrue("The rotated set point must actually move the tip in X/Y, travel=" + xyTravel, xyTravel > 1);
		double jointTravel = 0;
		double[] jointsAfter = afterTipMove.getCurrentJointSpaceTarget();
		for (int i = 0; i < jointsTarget.length; i++)
			jointTravel += Math.abs(jointsBefore[i] - jointsAfter[i]);
		assertTrue("The wheel joints must track the set point, travel=" + jointTravel, jointTravel > 1);
		log("STEP6 target=" + target.toSimpleString() + " joints before=" + Arrays.toString(jointsBefore) + " target="
				+ Arrays.toString(jointsTarget) + " after=" + Arrays.toString(jointsAfter));

		// snapshot the redo target state before undoing
		TransformNR wheelBaseFinal = afterTipMove.getRobotToFiducialTransform().copy();
		TransformNR wheelTipFinal = afterTipMove.getCurrentTaskSpaceTransform().copy();
		double[] jointTargetFinal = afterTipMove.getCurrentJointSpaceTarget();
		List<CSG> stateFinal = cf.getCurrentState();
		log("SNAPSHOT base=" + wheelBaseFinal.toSimpleString() + " tip=" + wheelTipFinal.toSimpleString() + " joints="
				+ Arrays.toString(jointTargetFinal) + " objects=" + stateFinal.size() + " cursor="
				+ cf.getCurrentIndex() + " of " + cf.getOperations().size());

		// 7: undo back to before the wheel was added
		while (cf.isBackAvailable()) {
			cf.back();
			boolean limbStillThere = false;
			for (CSG c : cf.getCurrentState())
				if (limbPartNames.contains(c.getName()))
					limbStillThere = true;
			if (!limbStillThere)
				break;
		}
		log("STEP7 after undo cursor=" + cf.getCurrentIndex() + " of " + cf.getOperations().size() + " limbs="
				+ cf.getRobots().get(robotName).getMobileBase().getAllDHChains().size());
		// the cursor must rest on the wheel add: everything after it is undone
		assertEquals("Undo must step all the way back past the wheel add", cf.opToIndex(addLimb), cf.getCurrentIndex());
		for (String n : limbPartNames) {
			try {
				CaDoodleFile.getByName(cf.getCurrentState(), n);
				fail("The wheel CAD must be gone from the state after undo: " + n);
			} catch (NameMissingException expected) {
				// the part is correctly missing
			}
		}
		assertEquals("The wheel limb must be removed from the robot on undo", 0,
				cf.getRobots().get(robotName).getMobileBase().getAllDHChains().size());

		// 8: redo forward and verify the robot comes back where it was
		while (cf.isForwardAvailable()) {
			cf.forward();
		}
		log("STEP8 after redo cursor=" + cf.getCurrentIndex() + " of " + cf.getOperations().size());
		MobileBase redone = cf.getRobots().get(robotName).getMobileBase();
		DHParameterKinematics redoneWheel = redone.getLimbByName(addLimb.getName());
		assertNotNull("The wheel limb did not come back on redo", redoneWheel);
		// the virtual device interpolates to the set point asynchronously, so
		// wait for the tip to converge like the live render loop does
		awaitTip(redoneWheel, wheelTipFinal, "redo");
		assertPosition("wheel base after redo", wheelBaseFinal, redoneWheel.getRobotToFiducialTransform());
		assertPosition("wheel tip after redo", wheelTipFinal, redoneWheel.getCurrentTaskSpaceTransform());
		double[] jointTargetRedone = redoneWheel.getCurrentJointSpaceTarget();
		assertEquals("Joint target count must match", jointTargetFinal.length, jointTargetRedone.length);
		for (int i = 0; i < jointTargetFinal.length; i++) {
			assertEquals("Joint " + i + " set point must match the pre-undo value", jointTargetFinal[i],
					jointTargetRedone[i], TOLERANCE_MM);
		}
		List<CSG> stateRedone = cf.getCurrentState();
		assertEquals("Object count must match the pre-undo state", stateFinal.size(), stateRedone.size());
		for (int i = 0; i < limbPartNames.size(); i++) {
			String n = limbPartNames.get(i);
			CSG part = CaDoodleFile.getByName(stateRedone, n);
			assertNotNull("Wheel part " + n + " is missing after redo", part);
		}

		for (MobileBase mb : cf.getMobileBases()) {
			mb.disconnect();
		}
		cf.close();
	}

	private void assertPosition(String what, TransformNR expected, TransformNR actual) {
		assertEquals(what + " X", expected.getX(), actual.getX(), TOLERANCE_MM);
		assertEquals(what + " Y", expected.getY(), actual.getY(), TOLERANCE_MM);
		assertEquals(what + " Z", expected.getZ(), actual.getZ(), TOLERANCE_MM);
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
