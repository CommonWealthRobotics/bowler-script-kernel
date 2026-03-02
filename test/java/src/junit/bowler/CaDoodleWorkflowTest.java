package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.ControllerOption;
import com.neuronrobotics.bowlerstudio.creature.LimbOption;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import java.lang.reflect.Type;

import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AddFromScript;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleOperation;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleParameters;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.Group;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ModelNotes;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.MoveCenter;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.Paste;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.Resize;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ToHole;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ToSolid;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.UnGroup;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotController;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.MakeRobot;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import javafx.scene.paint.Color;

public class CaDoodleWorkflowTest {
	  @Before
	  public void setup() throws InvalidRemoteException, TransportException, IOException, GitAPIException, Exception {
		  BowlerKernel.startupProcedures();
	  }
	@Test
	public void test() throws Exception {
		Log.enableDebugPrint();
		JavaFXInitializer.go();
		CaDoodleFile cf = new CaDoodleFile()
					.setSelf(new File("doodle/Test.doodle"))
					.setProjectName("A Test Project");
		cf.initialize();
		String jsonContent = cf.toJson();
		com.neuronrobotics.sdk.common.Log.debug("Starting file contents:\n"+jsonContent);
		
		AddFromScript cube1 = new AddFromScript()
				.set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
						"sphere.groovy");
		AddFromScript cube2 = new AddFromScript()
				.set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
						"sphere.groovy");
		cf.addOperation(cube1).join();
		List<CSG>back= cf.getCurrentState();
		if (back.size()!=1)
			fail("Adding a cube should have added one!");
		String nameOne = back.get(0).getName();
		cf.addOperation(cube2).join();;
		back=cf.getCurrentState();
		if (back.size()!=2)
			fail("Adding a cube should have added one more!");
		String nameTwo = back.get(1).getName();
		if (nameOne.contentEquals(nameTwo))
			fail("Names must be unique!");
		com.neuronrobotics.sdk.common.Log.error("Name one : "+nameOne );
		com.neuronrobotics.sdk.common.Log.error("Name two : "+nameTwo );
		CaDoodleParameters params = cf.getParameters();
		params.set("var1", 10.0);
		params.set("var2", 1.5);
		params.set("var3", "var1 + var2 + 0.25");
		params.set("var4", "(var3 / var2) + 0.25");
		for(String k:cf.getParameters().keys()){
			com.neuronrobotics.sdk.common.Log.debug("Key "+k+" value = "+cf.getParameters().getValue(k));
		}
		double distaance =10;
		MoveCenter move = new MoveCenter()
				.setLocation(new TransformNR(distaance,0,0))
				.setNames(Arrays.asList(nameTwo),cf)
				;
		cf.addOperation(move).join();;
		back=cf.getCurrentState();
		if (back.size()!=2)
			fail("Same number of objects after");
		double centerX = back.get(1).getCenterX();
		if (centerX!=distaance)
			fail("Move failed ");
		if (back.get(0).getCenterX()!=0)
			fail("Move misapplied ");
		jsonContent = cf.toJson();
		//com.neuronrobotics.sdk.common.Log.error(jsonContent);
		cf.save();
		File self = cf.getSelf();
		cf.close();
		if (!self.exists())
			fail("Doodle file does not exist, save failed! "+self.getAbsolutePath());
		CaDoodleFile loaded = CaDoodleFile.fromFile(self);
		if (!MoveCenter.class.isInstance(loaded.getOperations().get(2))) {
			fail("Third Operation is supposed to be a move");
		}
		if (!AddFromScript.class.isInstance(loaded.getOperations().get(1))) {
			fail(" Operation is supposed to be a AddFromScript");
		}
		if (!AddFromScript.class.isInstance(loaded.getOperations().get(0))) {
			fail(" Operation is supposed to be a AddFromScript");
		}
		loaded.back();
		MoveCenter move2 = new MoveCenter()
				.setLocation(new TransformNR(distaance,distaance,0))
				.setNames(Arrays.asList(nameOne),loaded)
				;
		MoveCenter move3 = new MoveCenter()
				.setLocation(new TransformNR(0,0,0,new RotationNR(0,45,0)))
				.setNames(Arrays.asList(nameOne),loaded)
				;
		loaded.addOperation(move3).join();;
		back=loaded.getCurrentState();
		loaded.addOperation(move2).join();;
		back=loaded.getCurrentState();
		double centerX2 = back.get(1).getCenterX();
		if (centerX2!=distaance)
			fail("Move failed ");
		if (back.get(1).getCenterY()!=distaance)
			fail("Move failed ");
		ToHole hole=  new ToHole().setNames(Arrays.asList(nameOne));
		loaded.addOperation(hole).join();;
		back=loaded.getCurrentState();
		Group group = new Group().setNames(Arrays.asList(nameOne,nameTwo));
		loaded.addOperation(group).join();;
		back=loaded.getCurrentState();
		if (back.size()!=3)
			fail("Group Failed ");
		if (!back.get(0).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if (!back.get(1).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if (back.get(2).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if (!back.get(2).isGroupResult()) {
			fail("THis should be aa group result");
		}
		String groupName = back.get(2).getName();
		com.neuronrobotics.sdk.common.Log.error("Group Name : "+groupName);
		TransformNR height = new TransformNR(0,0,40);
		TransformNR leftFront = new TransformNR(40,80,0);
		TransformNR rightRear = new TransformNR(-10,10,0);
		Resize resize = new Resize()
					.setResize(height, leftFront, rightRear)
					.setNames(Arrays.asList(groupName))
				;
		loaded.addOperation(resize).join();;
		back=loaded.getCurrentState();
		ToSolid solid = new ToSolid()
						.setNames(Arrays.asList(groupName))
						.setColor(Color.BLUE);
		loaded.addOperation(solid).join();;
		back=loaded.getCurrentState();
		UnGroup ug = new UnGroup().setNames(Arrays.asList(groupName));
		loaded.addOperation(ug).join();;
		back=loaded.getCurrentState();
		if (back.get(0).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if (back.get(1).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		

		loaded.addOperation(
				new Group()
				.setNames(Arrays.asList(nameOne,nameTwo))
				).join();;
		back=loaded.getCurrentState();
		List<CSG> cacheOfGroup = loaded.getCurrentState();

		String newGroupName = cacheOfGroup.get(cacheOfGroup.size()-1).getName();
		
		loaded.addOperation(
				new Paste().setNames(Arrays.asList(newGroupName))).join();;
		back=loaded.getCurrentState();
		ArrayList<String> selectAll = new  ArrayList<String>();
		for (CSG c:back) {
			if (c.isGroupResult())
				selectAll.add(c.getName());
		}
		loaded.addOperation(
				new UnGroup().setNames(selectAll)).join();;
		back=loaded.getCurrentState();
		if (back.get(0).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if (back.get(1).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if (back.get(2).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if (back.get(3).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		ToHole h=  new ToHole().setNames(Arrays.asList(nameTwo));
		loaded.addOperation(h).join();;
		back=loaded.getCurrentState();
		loaded.save();
		
		for (int i = 0; i < 3; i++) {
			loaded.back();
		}
		List<CSG> goBackResult = loaded.getCurrentState();
		back=goBackResult;
		if (goBackResult.size()!=3) {
			fail(" Number of elements after back incorrect!");
		}
		if (back.get(2).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if (!back.get(2).isGroupResult()) {
			fail("THis should be a group result");
		}
		if (!back.get(0).isInGroup()) {
			fail("This should be in a group");
		}
		if (!back.get(1).isInGroup()) {
			fail("This should be in a group");
		}
		
		loaded.save();

		String before = loaded.toJson();
		loaded.close();
		loaded=CaDoodleFile.fromJsonString(before);
		String after =loaded.toJson();
		if (!before.contentEquals(after))
			fail("Load and export mismatch");
		File self2 = cf.getSelf();
		loaded.setSelf(self2);

		com.neuronrobotics.sdk.common.Log.error(after);
		while (loaded.isForwardAvailable()) {
			System.out.println("Moving forward the Doodle");
			loaded.forward();
		}
		selectAll = new  ArrayList<String>();
		for (CSG c:loaded.getCurrentState()) {
			selectAll.add(c.getName());
		}
		MakeRobot mr = new MakeRobot();
		mr.setNames(selectAll);

		loaded.addOperation(mr).join();
		ModelNotes setText = new ModelNotes()
				.setLocation(new TransformNR(0, 0, 20))
				.setText("A note is here");
		loaded.addOperation(setText).join();
		
		loaded.save();
		ScriptingEngine.pull(ControllerOption.URL_OF_OPTIONS);
		ArrayList<ControllerOption> controllers = ControllerOption.getOptions();
		for (ControllerOption o:controllers) {
			System.out.println("Adding a Controller: "+o);
			AddRobotController con = new AddRobotController()
					.setNames(selectAll)
					.setController(o);
			loaded.addOperation(con).join();
			System.out.println("Added!");
		}
		loaded.save();
		ScriptingEngine.pull("https://github.com/madhephaestus/carl-the-hexapod.git");
		ArrayList<LimbOption> limbs = LimbOption.getOptions();
		TransformNR tf = new TransformNR();
		for (LimbOption o:limbs) {
			if (mr.getBuilder().checkOptionSupported(o)) {
				System.out.println(o);
				tf = new TransformNR(0, 0, 30).times(tf);
				AddRobotLimb limb = new AddRobotLimb()
						.setLimb(o)
						.setNames(selectAll)
						.setLocation(tf);
				loaded.addOperation(limb).join();
			}else {
				System.out.println("Unsupported limb "+o);
			}
			//break;
		}
		for (MobileBase mb:loaded.getMobileBases()) {
			System.out.println("Base "+mb);
			mb.disconnect();
		}
		System.out.println("Saving");
		loaded.save();
		CaDoodleParameters parameters = loaded.getParameters();
		for(String k:parameters.keys()){
			com.neuronrobotics.sdk.common.Log.debug("Key "+k+" value = "+parameters.getValue(k)+" "+parameters.getString(k));
		}
		parameters.set("var2", 0.85);
		for(String k:parameters.keys()){
			com.neuronrobotics.sdk.common.Log.debug("Key "+k+" value = "+parameters.getValue(k)+" "+parameters.getString(k));
		}
		assertEquals(parameters.getValue("var4"), 13.3088235294, 0.0001);
		loaded.close();
		System.out.println("Save finished");
		Thread.sleep(200);

	}

}
