package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class JsonRunner implements IScriptingLanguage {

  //Create the type, this tells GSON what datatypes to instantiate when parsing and saving the json
  private static Type TT_mapStringString = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
  }.getType();
  //chreat the gson object, this is the parsing factory
  private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

  @Override
  public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
    String jsonString = null;
    InputStream inPut = null;
    inPut = FileUtils.openInputStream(code);
    jsonString = IOUtils.toString(inPut);
    return inlineScriptRun(jsonString, args);
  }

  @Override
  public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {

    // perfoem the GSON parse
    HashMap<String, HashMap<String, Object>> database = gson.fromJson(code, TT_mapStringString);
    return database;
  }

  @Override
  public String getShellType() {
    return "JSON";
  }

  @Override
  public boolean getIsTextFile() {
    return true;
  }

  @Override
  public ArrayList<String> getFileExtenetion() {
    // TODO Auto-generated method stub
    return new ArrayList<>(Arrays.asList("json"));
  }
}
