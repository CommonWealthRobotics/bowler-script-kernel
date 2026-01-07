package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

public class MobileBaseBuilderTest {

	@Test
	@Ignore
	public void test() throws Exception {
		JavaFXInitializer.go();
		MobileBaseBuilder builder = new MobileBaseBuilder(CSGDatabase.getInstance(),
				"https://github.com/madhephaestus/TestRepo.git", "BuiltRobot")
				.setXmlName("RobRobotExample.xml");
		
		MobileBase base = builder.build(CSGDatabase.getInstance());
		base.disconnect();
		
	}

}
