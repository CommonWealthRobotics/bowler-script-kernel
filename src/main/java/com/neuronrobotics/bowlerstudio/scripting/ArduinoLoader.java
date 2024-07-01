package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class ArduinoLoader implements IScriptingLanguage {


  private static String defaultPort = null;
  private static String defaultBoard = null;

  @SuppressWarnings("unchecked")
  @Override
  public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
    if (args == null) {
      args = new ArrayList<>();
    }

    ArrayList<String> commands = new ArrayList<> ();
    commands.add( getARDUINOExec());

    if (args.size() > 0) {
      setDefaultBoard((String) args.get(0));
    }
    if (getDefaultBoard() != null) {
      commands.add("board");
      commands.add(getDefaultBoard());
      if (args.size() > 1) {
        setDefaultPort((String) args.get(1));
      }
    }
    if (getDefaultPort() != null) {
      commands.add("port");
      commands.add(getDefaultPort());
    }
    File ino = findIno(code);
    if (ino == null) {
      //System.out.println("Error: no .ino file found!");
      return null;
    }
    
    commands.add("upload");
    commands.add(ino.getAbsolutePath());

    //System.out.println("Arduino Load: \n"+execString);
    Thread ret = DownloadManager.run(null, ino.getParentFile(), System.out, commands);
    ret.join();

    return null;
  }

//  public static void installBoard(String product, String arch) throws Exception {
//    run(getARDUINOExec() + " --install-boards " + product + ":" + arch);
//  }
//
//  public static void installLibrary(String lib) throws Exception {
//    run(getARDUINOExec() + " --install-library " + lib);
//  }


  private File findIno(File start) {
    if (start == null) {
      return null;
    }
    if (start.getName().endsWith(".ino")) {
      return start;
    } else {
      File dir = start.getParentFile();
      if (dir != null) {
        for (File f : dir.listFiles()) {
          if (findIno(f) != null) {
            return f;
          }
        }
      }
    }
    return null;

  }

  @Override
  public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getShellType() {
    return "Arduino";
  }

  @Override
  public boolean getIsTextFile() {
    return true;
  }

  public static String getDefaultPort() {
    return defaultPort;
  }

  public static void setDefaultPort(String defaultPort) {
    ArduinoLoader.defaultPort = defaultPort;
  }

  public static String getDefaultBoard() {
    return defaultBoard;
  }

  public static void setDefaultBoard(String defaultBoard) {
    ArduinoLoader.defaultBoard = defaultBoard;
  }
  /**
   * Get the contents of an empty file
   * @return
   */
  public String getDefaultContents() {
	  return "/*\n"
	  		+ "  Blink\n"
	  		+ "\n"
	  		+ "  Turns an LED on for one second, then off for one second, repeatedly.\n"
	  		+ "\n"
	  		+ "  Most Arduinos have an on-board LED you can control. On the UNO, MEGA and ZERO\n"
	  		+ "  it is attached to digital pin 13, on MKR1000 on pin 6. LED_BUILTIN is set to\n"
	  		+ "  the correct LED pin independent of which board is used.\n"
	  		+ "  If you want to know what pin the on-board LED is connected to on your Arduino\n"
	  		+ "  model, check the Technical Specs of your board at:\n"
	  		+ "  https://www.arduino.cc/en/Main/Products\n"
	  		+ "\n"
	  		+ "  modified 8 May 2014\n"
	  		+ "  by Scott Fitzgerald\n"
	  		+ "  modified 2 Sep 2016\n"
	  		+ "  by Arturo Guadalupi\n"
	  		+ "  modified 8 Sep 2016\n"
	  		+ "  by Colby Newman\n"
	  		+ "\n"
	  		+ "  This example code is in the public domain.\n"
	  		+ "\n"
	  		+ "  https://www.arduino.cc/en/Tutorial/BuiltInExamples/Blink\n"
	  		+ "*/\n"
	  		+ "\n"
	  		+ "// the setup function runs once when you press reset or power the board\n"
	  		+ "void setup() {\n"
	  		+ "  // initialize digital pin LED_BUILTIN as an output.\n"
	  		+ "  pinMode(LED_BUILTIN, OUTPUT);\n"
	  		+ "}\n"
	  		+ "\n"
	  		+ "// the loop function runs over and over again forever\n"
	  		+ "void loop() {\n"
	  		+ "  digitalWrite(LED_BUILTIN, HIGH);  // turn the LED on (HIGH is the voltage level)\n"
	  		+ "  delay(1000);                      // wait for a second\n"
	  		+ "  digitalWrite(LED_BUILTIN, LOW);   // turn the LED off by making the voltage LOW\n"
	  		+ "  delay(1000);                      // wait for a second\n"
	  		+ "}\n"
	  		+ "";
  }

  public static String getARDUINOExec() {
    return DownloadManager.getRunExecutable("arduino-cli", null).getAbsolutePath();
  }


  @Override
  public ArrayList<String> getFileExtenetion() {
    // TODO Auto-generated method stub
    return new ArrayList<>(Arrays.asList( ".ino",".c", ".h", ".cpp", ".hpp"));
  }

}
