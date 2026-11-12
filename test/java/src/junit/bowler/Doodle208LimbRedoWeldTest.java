package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;
import javafx.scene.transform.Affine;

/**
 * Doodle-208 regression test for the display weld. In the app the render
 * loop gives every limb a root Affine and the limb CAD rides it (the mesh
 * transforms hold that Affine, shared with the timeline cache through the
 * CSG manipulator map). Undoing past the limb add destroys the limb object
 * and redo creates a new one; the displayed wheel has to end up welded to
 * the LIVE limb's Affine again, or every later undo/redo freezes the mesh
 * on screen while the kinematics keep moving invisibly.
 *
 * The headless environment has no render loop, so this test installs the
 * Affine and welds the wheel the way MobileBaseCadManager does, then
 * requires the weld to survive the destroy/recreate cycle.
 */
public class Doodle208LimbRedoWeldTest {

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
		File fixture = new File("testStepFiles/Doodle-208-Mirthful_Gravicycle.doodle");
		assertTrue("Fixture missing: " + fixture.getAbsolutePath(), fixture.exists());
		cf = CaDoodleFile.fromFile(fixture);

		AddRobotLimb addLimb = null;
		for (CaDoodleOperation op : cf.getOperations())
			if (AddRobotLimb.class.isInstance(op))
				addLimb = (AddRobotLimb) op;
		assertNotNull("The fixture has no limb", addLimb);
		limbName = addLimb.getName();
		MobileBaseBuilder builder = cf.getRobots().get(addLimb.getBuilderName());
		assertNotNull("The robot builder is missing after load", builder);
		robotBase = builder.getMobileBase();
		DHParameterKinematics limb1 = robotBase.getLimbByName(limbName);
		assertNotNull("The wheel limb is missing after load", limb1);

		// what the app's render loop does: the limb root rides an Affine and
		// the wheel CAD is welded to it
		Affine root1 = new Affine();
		limb1.setRootListener(root1);
		ArrayList<CSG> wheels = wheelParts();
		assertFalse("The wheel CAD is missing from the loaded state", wheels.isEmpty());
		for (CSG c : wheels)
			c.setManipulator(root1);

		// 1: undo past the add: the wheel leaves the state and the limb
		// object is destroyed
		while (cf.isBackAvailable()) {
			cf.back();
			if (wheelParts().isEmpty())
				break;
		}
		assertTrue("The wheel must be gone after undoing its add", wheelParts().isEmpty());
		assertEquals("The limb must be removed on undo", 0, robotBase.getAllDHChains().size());

		// 2: redo forward: a new limb object comes back, and the displayed
		// wheel must be welded to the LIVE limb again
		while (cf.isForwardAvailable())
			cf.forward();
		DHParameterKinematics limb2 = robotBase.getLimbByName(limbName);
		assertNotNull("The wheel limb did not come back on redo", limb2);
		// one render pulse like the app's render wrangler: it only mints an
		// Affine when the limb has none, so the handover decides what the
		// wheel rides from here on
		if (limb2.getRootListener() == null)
			limb2.setRootListener(new Affine());
		assertWeldedToLive("after redo forward", limb2);

		// 3: a move committed after the redo, then undo and redo of it, must
		// keep the displayed wheel on the live limb and move the base
		TransformNR before = limb2.getRobotToFiducialTransform().copy();
		TransformNR target = new TransformNR(before.getX() + 10, before.getY(), before.getZ())
				.times(new TransformNR(before.getRotation()));
		ModifyLimb move = new ModifyLimb().setLimb(limb2).setNames(new ArrayList<String>(wheelPartNames()))
				.setBase(target);
		cf.addOperation(move).join();
		DHParameterKinematics moved = robotBase.getLimbByName(limbName);
		assertBase("after the post-redo move", target, moved);
		assertWeldedToLive("after the post-redo move", moved);

		cf.back();
		DHParameterKinematics undone = robotBase.getLimbByName(limbName);
		assertBase("after undoing the post-redo move", before, undone);
		assertWeldedToLive("after undoing the post-redo move", undone);

		cf.forward();
		DHParameterKinematics redone = robotBase.getLimbByName(limbName);
		assertBase("after redoing the post-redo move", target, redone);
		assertWeldedToLive("after redoing the post-redo move", redone);

		for (MobileBase mb : cf.getMobileBases())
			mb.disconnect();
		cf.close();
	}

	// every wheel CSG in the displayed state must ride the live limb's root
	// Affine: that identity is what makes the mesh move in the app
	private static void assertWeldedToLive(String when, DHParameterKinematics live) {
		Object root = live.getRootListener();
		assertNotNull(when + ": the live limb has no render Affine to weld to", root);
		ArrayList<CSG> wheels = wheelParts();
		assertFalse(when + ": wheel parts missing from the state", wheels.isEmpty());
		for (CSG c : wheels) {
			Affine manip = null;
			try {
				manip = c.getManipulator();
			} catch (MissingManipulatorException e) {
				fail(when + ": wheel part " + c.getName() + " lost its manipulator weld");
			}
			assertEquals(when + ": wheel part " + c.getName() + " is welded to a dead limb Affine", root, manip);
		}
	}

	private static ArrayList<CSG> wheelParts() {
		ArrayList<CSG> back = new ArrayList<CSG>();
		for (CSG c : cf.getCurrentState())
			if (c.getLimbName().isPresent() && c.getLimbName().get().contentEquals(limbName))
				back.add(c);
		return back;
	}

	private static ArrayList<String> wheelPartNames() {
		ArrayList<String> back = new ArrayList<String>();
		for (CSG c : wheelParts())
			back.add(c.getName());
		return back;
	}

	private static void assertBase(String when, TransformNR expected, DHParameterKinematics limb) {
		TransformNR actual = limb.getRobotToFiducialTransform();
		assertEquals(when + " base X", expected.getX(), actual.getX(), 0.1);
		assertEquals(when + " base Y", expected.getY(), actual.getY(), 0.1);
		assertEquals(when + " base Z", expected.getZ(), actual.getZ(), 0.1);
	}

}
