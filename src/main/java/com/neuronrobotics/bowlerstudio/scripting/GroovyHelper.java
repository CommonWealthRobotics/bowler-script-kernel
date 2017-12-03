package com.neuronrobotics.bowlerstudio.scripting;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.*;

import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.DeviceManager;

public class GroovyHelper implements IScriptingLanguage, IScriptingLanguageDebugger {


  private Object inline(Object code, ArrayList<Object> args) throws Exception {
    CompilerConfiguration cc = new CompilerConfiguration();
    cc.addCompilationCustomizers(new ImportCustomizer()
        .addStarImports(ScriptingEngine.getImports())
        .addStaticStars(
            "com.neuronrobotics.sdk.util.ThreadUtil",
            "eu.mihosoft.vrl.v3d.Transform",
            "com.neuronrobotics.bowlerstudio.vitamins.Vitamins")
    );

    Binding binding = new Binding();
    for (String pm : DeviceManager.listConnectedDevice()) {
      BowlerAbstractDevice bad = DeviceManager.getSpecificDevice(null, pm);
      try {
        // groovy needs the objects cas to thier actual type befor
        // passing into the scipt

        binding.setVariable(bad.getScriptingName(),
            Class.forName(bad.getClass().getName())
                .cast(bad));
      } catch (Throwable e) {
        //throw e;
      }
//			System.err.println("Device " + bad.getScriptingName() + " is "
//					+ bad);
    }
    binding.setVariable("args", args);

    GroovyShell shell = new GroovyShell(GroovyHelper.class
        .getClassLoader(), binding, cc);
    //System.out.println(code + "\n\nStart\n\n");
    Script script;
    if (String.class.isInstance(code)) {
      script = shell.parse((String) code);
    } else if (File.class.isInstance(code)) {
      script = shell.parse((File) code);
    } else {
      return null;
    }
    return script.run();

  }


  @Override
  public String getShellType() {
    return "Groovy";
  }


  @Override
  public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
    return inline(code, args);
  }


  @Override
  public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
    return inline(code, args);
  }


  @Override
  public boolean getIsTextFile() {
    // TODO Auto-generated method stub
    return true;
  }

  @Override
  public ArrayList<String> getFileExtenetion() {
    // TODO Auto-generated method stub
    return new ArrayList<>(Arrays.asList("java", "groovy"));
  }

  @Override
  public IDebugScriptRunner compileDebug(File f) {
    // TODO Auto-generated method stub
    return new IDebugScriptRunner() {

      @Override
      public String[] step() {
        // TODO Auto-generated method stub
        return new String[]{"fileame.groovy", "345"};
      }
    };
  }

}
