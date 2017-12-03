package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.util.ArrayList;

import javafx.scene.Group;

/**
 * Adding additional language support to bowler studio THis interface is for adding new scripting
 * languages Add the new langauge in the Static declaration of ScriptingEngine or dynamically via:
 *
 * ScriptingEngine.addScriptingLanguage(new IScriptingLanguage());
 *
 * @author hephaestus
 */
public interface IScriptingLanguage {

  /**
   * This interface is for adding additional language support.
   *
   * @param code file content of the code to be executed
   * @param args the incoming arguments as a list of objects
   * @return the objects returned form the code that ran
   */
  public abstract Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception;

  /**
   * This interface is for adding additional language support.
   *
   * @param code the text content of the code to be executed
   * @param args the incoming arguments as a list of objects
   * @return the objects returned form the code that ran
   */
  public abstract Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception;

  /**
   * Returns the HashMap key for this language
   */
  public abstract String getShellType();

  /**
   * Returns the list of supported file extentions Convention is to provide just the leters that
   * make up the file extention
   */
  public abstract ArrayList<String> getFileExtenetion();

  /**
   * This function should return true is the filename provided is of a supported file extension.
   * This function may never be called if this language is only used internally.
   *
   * @param filename the filename of the file to be executed
   * @return true if the file extension is supported, false otherwise.
   */
  default boolean isSupportedFileExtenetion(String filename) {
    for (String s : getFileExtenetion()) {
      if (filename.toLowerCase().endsWith(s.toLowerCase())) {
        return true;
      }
    }
    return false;
  }

  /**
   * This function returns if this is a binary file or a text file
   *
   * @return true if the file is a text file.
   */
  public boolean getIsTextFile();

}
