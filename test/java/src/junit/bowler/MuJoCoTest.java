package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;
import org.mujoco.MuJoCoLib;

public class MuJoCoTest {

	@Test
	public void test() {
		System.out.println("mujocoJNILoadTest");
		System.out.println(System.getProperty("org.bytedeco.javacpp.logger.debug"));
		System.setProperty("org.bytedeco.javacpp.logger.debug", "true");
		MuJoCoLib lib = new MuJoCoLib();

		System.out.println("Starting " + MuJoCoLib.mj_versionString().getString());
	}

}
