package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import gnu.io.NRSerialPort;

public class ArduinoLoaderTest {

  private static final String portname = "/dev/ttyACM0";
  private boolean hasPort;

  @Test
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
      ScriptingEngine.gitScriptRun("https://github.com/madhephaestus/Blink.git", "Blink.ino", params);
    }
  }

}
