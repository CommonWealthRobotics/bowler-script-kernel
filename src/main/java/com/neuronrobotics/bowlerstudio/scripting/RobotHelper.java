package com.neuronrobotics.bowlerstudio.scripting;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseLoader;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;


public class RobotHelper implements IScriptingLanguage {

  @Override
  public Object inlineScriptRun(File code, ArrayList<Object> args) {
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(code.toPath());
      String s = new String(bytes, "UTF-8");
      MobileBase mb;
      try {
        mb = new MobileBase(IOUtils.toInputStream(s, "UTF-8"));
        mb.setGitSelfSource(ScriptingEngine.findGitTagFromFile(code));
        return MobileBaseLoader.get(mb).getBase();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
        return null;
      }
    } catch (IOException e1) {
      // TODO Auto-generated catch block
      e1.printStackTrace();
    }
    //System.out.println("Clojure returned of type="+ret.getClass()+" value="+ret);
    return null;
  }

  @Override
  public Object inlineScriptRun(String code, ArrayList<Object> args) {

    MobileBase mb = null;
    try {
      mb = new MobileBase(IOUtils.toInputStream(code, "UTF-8"));
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return null;
    }

    return mb;
  }

  @Override
  public String getShellType() {
    return "MobilBaseXML";
  }

  @Override
  public boolean getIsTextFile() {
    // TODO Auto-generated method stub
    return true;
  }

  @Override
  public ArrayList<String> getFileExtenetion() {
    // TODO Auto-generated method stub
    return new ArrayList<>(Arrays.asList("xml"));
  }

}

