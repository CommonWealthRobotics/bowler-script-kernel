package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mujoco.MuJoCoLib;

public class MuJoCoTest {

	@Test
	public void test() {
		com.neuronrobotics.sdk.common.Log.error("mujocoJNILoadTest");
		System.setProperty("org.bytedeco.javacpp.logger.debug", "true");
		MuJoCoLib lib = new MuJoCoLib();

		com.neuronrobotics.sdk.common.Log.error("Starting " + MuJoCoLib.mj_versionString().getString());
	}

}
