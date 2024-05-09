package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.sdk.addons.gamepad.BowlerJInputDevice;

public class JinputTest {

	@Test
	public void test() {
		BowlerJInputDevice.getControllers();
	}

}
