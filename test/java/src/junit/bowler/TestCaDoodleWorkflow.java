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
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.MoveCenter;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

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
						"cube.groovy");
		AddFromScript cube2 = new AddFromScript()
				.set("https://github.com/madhephaestus/CaDoodle-Example-Objects.git",
						"cube.groovy");
		List<CSG>back= cf.addOpperation(cube1);
		if(back.size()!=1)
			fail("Adding a cube should have added one!");
		String nameOne = back.get(0).getName();
		back=cf.addOpperation(cube2);
		if(back.size()!=2)
			fail("Adding a cube should have added one more!");
		String nameTwo = back.get(1).getName();
		if(nameOne.contentEquals(nameTwo))
			fail("Names must be unique!");
		System.out.println("Name one : "+nameOne );
		System.out.println("Name two : "+nameTwo );
		double distaance =100;
		MoveCenter move = new MoveCenter()
				.setLocation(new TransformNR(distaance,0,0))
				.setNames(Arrays.asList(nameTwo))
				;
		back= cf.addOpperation(move);
		if(back.size()!=2)
			fail("Same number of objects after");
		if(back.get(1).getCenterX()!=distaance)
			fail("Move failed ");
		if(back.get(0).getCenterX()!=0)
			fail("Move misapplied ");
		jsonContent = cf.toJson();
		System.out.println(jsonContent);
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
		loaded.addOpperation(move2);
		System.out.println(loaded.toJson());

	}

}
