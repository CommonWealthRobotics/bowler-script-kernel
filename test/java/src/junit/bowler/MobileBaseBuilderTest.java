package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

public class MobileBaseBuilderTest {

	@Test
	public void test() throws Exception {
		ScriptingEngine.login();
		MobileBaseBuilder builder = new MobileBaseBuilder(
				"https://github.com/madhephaestus/TestRepo.git", "BuiltRobot")
				.setXmlName("RobRobotExample.xml");
		
		MobileBase base = builder.build();
		
		
	}

}
