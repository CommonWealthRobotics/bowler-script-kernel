package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

public class MobileBaseBuilderTest {

	@Test
	public void test() throws Exception {
		ScriptingEngine.login();
		MobileBaseBuilder builder = new MobileBaseBuilder(CSGDatabase.getInstance(),
				"https://github.com/madhephaestus/TestRepo.git", "BuiltRobot")
				.setXmlName("RobRobotExample.xml");
		
		MobileBase base = builder.build(CSGDatabase.getInstance());
		
		
	}

}
