package junit.bowler;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import gnu.io.NRSerialPort;

public class ArduinoLoaderTest {

	private static final String portname = "/dev/ttyACM0";
	private boolean hasPort;

	@Test
	@Ignore
	public void test() throws Exception {
		hasPort = false;
		for (String s : NRSerialPort.getAvailableSerialPorts()) {
			if (s.contentEquals(portname))
				hasPort = true;
		}
		if (hasPort) {
			String board = "uno";
			ArrayList<Object> params = new ArrayList<>();
			params.add(board);
			params.add(portname);
			ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(), "https://github.com/madhephaestus/Blink.git",
					"Blink.ino", params);
		}
	}

}
