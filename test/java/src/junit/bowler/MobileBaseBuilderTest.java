package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

public class MobileBaseBuilderTest {

	@Test
	@Ignore
	public void test() throws Exception {
		JavaFXInitializer.go();
		MobileBaseBuilder builder = new MobileBaseBuilder(CSGDatabase.getInstance(),
				"https://github.com/madhephaestus/TestRepo.git", "BuiltRobot").setXmlName("RobRobotExample.xml");

		MobileBase base = builder.build(CSGDatabase.getInstance());
		base.disconnect();

	}

}
