package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mujoco.MuJoCoLib;

import com.neuronrobotics.bowlerstudio.BowlerKernel;

public class MuJoCoTest {

	@Test
	@Ignore
	public void test() {
		com.neuronrobotics.sdk.common.Log.error("mujocoJNILoadTest");
		System.setProperty("org.bytedeco.javacpp.logger.debug", "true");
		MuJoCoLib lib = new MuJoCoLib();

		com.neuronrobotics.sdk.common.Log.error("Starting " + MuJoCoLib.mj_versionString().getString());
		
	}

}
