package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.Test;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import java.lang.reflect.Type;

import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AddFromScript;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.Group;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.MoveCenter;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.Paste;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.Resize;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ToHole;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ToSolid;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.UnGroup;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

public class TestCaDoodleWorkflow {

	@Test
	public void test() throws Exception {
		CaDoodleFile cf = new CaDoodleFile()
					.setSelf(new File("Test.doodle"))
					.setProjectName("A Test Project");
		
		String jsonContent = cf.toJson();
		System.out.println(jsonContent);
		
		AddFromScript cube1 = new AddFromScript()
				.set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
						"sphere.groovy");
		AddFromScript cube2 = new AddFromScript()
				.set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
						"sphere.groovy");
		cf.addOpperation(cube1).join();
		List<CSG>back= cf.getCurrentState();
		if(back.size()!=1)
			fail("Adding a cube should have added one!");
		String nameOne = back.get(0).getName();
		cf.addOpperation(cube2).join();;
		back=cf.getCurrentState();
		if(back.size()!=2)
			fail("Adding a cube should have added one more!");
		String nameTwo = back.get(1).getName();
		if(nameOne.contentEquals(nameTwo))
			fail("Names must be unique!");
		System.out.println("Name one : "+nameOne );
		System.out.println("Name two : "+nameTwo );
		double distaance =10;
		MoveCenter move = new MoveCenter()
				.setLocation(new TransformNR(distaance,0,0))
				.setNames(Arrays.asList(nameTwo))
				;
		cf.addOpperation(move).join();;
		back=cf.getCurrentState();
		if(back.size()!=2)
			fail("Same number of objects after");
		if(back.get(1).getCenterX()!=distaance)
			fail("Move failed ");
		if(back.get(0).getCenterX()!=0)
			fail("Move misapplied ");
		jsonContent = cf.toJson();
		//System.out.println(jsonContent);
		cf.save();
		CaDoodleFile loaded = CaDoodleFile.fromFile(cf.getSelf());
		if(!MoveCenter.class.isInstance(loaded.getOpperations().get(2))) {
			fail("Third Opperation is supposed to be a move");
		}
		if(!AddFromScript.class.isInstance(loaded.getOpperations().get(1))) {
			fail(" Opperation is supposed to be a AddFromScript");
		}
		if(!AddFromScript.class.isInstance(loaded.getOpperations().get(0))) {
			fail(" Opperation is supposed to be a AddFromScript");
		}
		loaded.back();
		MoveCenter move2 = new MoveCenter()
				.setLocation(new TransformNR(distaance,distaance,0))
				.setNames(Arrays.asList(nameOne))
				;
		MoveCenter move3 = new MoveCenter()
				.setLocation(new TransformNR(0,0,0,new RotationNR(0,45,0)))
				.setNames(Arrays.asList(nameOne))
				;
		loaded.addOpperation(move3).join();;
		back=loaded.getCurrentState();
		loaded.addOpperation(move2).join();;
		back=loaded.getCurrentState();
		if(back.get(0).getCenterX()!=distaance)
			fail("Move failed ");
		if(back.get(0).getCenterY()!=distaance)
			fail("Move failed ");
		ToHole hole=  new ToHole().setNames(Arrays.asList(nameOne));
		loaded.addOpperation(hole).join();;
		back=loaded.getCurrentState();
		Group group = new Group().setNames(Arrays.asList(nameOne,nameTwo));
		loaded.addOpperation(group).join();;
		back=loaded.getCurrentState();
		if(back.size()!=3)
			fail("Group Failed ");
		if(!back.get(0).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if(!back.get(1).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if(back.get(2).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if(!back.get(2).isGroupResult()) {
			fail("THis should be aa group result");
		}
		String groupName = back.get(2).getName();
		System.out.println("Group Name : "+groupName);
		TransformNR height = new TransformNR(0,0,40);
		TransformNR leftFront = new TransformNR(40,80,0);
		TransformNR rightRear = new TransformNR(-10,10,0);
		Resize resize = new Resize()
					.setResize(height, leftFront, rightRear)
					.setNames(Arrays.asList(groupName))
				;
		loaded.addOpperation(resize).join();;
		back=loaded.getCurrentState();
		ToSolid solid = new ToSolid()
						.setNames(Arrays.asList(groupName))
						.setColor(Color.BLUE);
		loaded.addOpperation(solid).join();;
		back=loaded.getCurrentState();
		UnGroup ug = new UnGroup().setNames(Arrays.asList(groupName));
		loaded.addOpperation(ug).join();;
		back=loaded.getCurrentState();
		if(back.get(0).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if(back.get(1).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		

		loaded.addOpperation(
				new Group()
				.setNames(Arrays.asList(nameOne,nameTwo))
				).join();;
		back=loaded.getCurrentState();
		List<CSG> cacheOfGroup = loaded.getCurrentState();

		String newGroupName = cacheOfGroup.get(cacheOfGroup.size()-1).getName();
		
		loaded.addOpperation(
				new Paste().setNames(Arrays.asList(newGroupName))).join();;
		back=loaded.getCurrentState();
		ArrayList<String> selectAll = new  ArrayList<String>();
		for(CSG c:back) {
			if(c.isGroupResult())
				selectAll.add(c.getName());
		}
		loaded.addOpperation(
				new UnGroup().setNames(selectAll)).join();;
		back=loaded.getCurrentState();
		if(back.get(0).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if(back.get(1).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if(back.get(2).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if(back.get(3).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		ToHole h=  new ToHole().setNames(Arrays.asList(nameTwo));
		loaded.addOpperation(h).join();;
		back=loaded.getCurrentState();
		loaded.save();
		
		for(int i=0;i<3;i++) {
			loaded.back();
		}
		List<CSG> goBackResult = loaded.getCurrentState();
		back=goBackResult;
		if(goBackResult.size()!=3) {
			fail(" Number of elements after back incorrect!");
		}
		if(back.get(2).isInGroup()) {
			fail("THis should not be in a group anymore");
		}
		if(!back.get(2).isGroupResult()) {
			fail("THis should be a group result");
		}
		if(!back.get(0).isInGroup()) {
			fail("This should be in a group");
		}
		if(!back.get(1).isInGroup()) {
			fail("This should be in a group");
		}
		
		loaded.save();

		String before = loaded.toJson();
		loaded=CaDoodleFile.fromJsonString(before);
		String after =loaded.toJson();
		if(!before.contentEquals(after))
			fail("Load and export mismatch");
		loaded.setSelf(cf.getSelf());

		System.out.println(after);
		


	
	}

}
