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
import com.neuronrobotics.bowlerstudio.sequence.TimeSequence;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

public class SequenceRunner implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db,File code, ArrayList<Object> args) throws Exception {
		String jsonString = null;
		InputStream inPut = null;
		inPut = FileUtils.openInputStream(code);
		jsonString = IOUtils.toString(inPut);
		return inlineScriptRun( db,jsonString, args);
	}

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db,String code, ArrayList<Object> args) throws Exception {

		new TimeSequence().execute(code);
		return null;
	}

	@Override
	public String getShellType() {
		return "Sequence";
	}

	@Override
	public boolean getIsTextFile() {
		return true;
	}

	/**
	 * Get the contents of an empty file
	 * 
	 * @return
	 */
	public String getDefaultContents() {
		return "{}";
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		// Auto-generated method stub
		return new ArrayList<>(Arrays.asList("sequence"));
	}
}
