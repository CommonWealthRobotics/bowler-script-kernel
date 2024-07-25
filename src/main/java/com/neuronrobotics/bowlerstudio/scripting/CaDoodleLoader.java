package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;

import eu.mihosoft.vrl.v3d.CSG;

public class CaDoodleLoader implements IScriptingLanguage {
	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		CaDoodleFile loaded = CaDoodleFile.fromFile(code);
		return process(loaded);
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		CaDoodleFile loaded = CaDoodleFile.fromJsonString(code);
		return process(loaded);
	}

	public static Object process(CaDoodleFile loaded) {
		List<CSG> incoming = loaded.getCurrentState();
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		for(CSG c: incoming) {
			if(c.isInGroup()) {
				back.remove(c);
			}
			if(c.isHide()) {
				back.remove(c);
			}
		}
		return back;
	}

	@Override
	public String getShellType() {
		return "CaDoodle";
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
		return "{\n"
				+ "  \"opperations\": [],\n"
				+ "  \"currentIndex\": 0,\n"
				+ "  \"projectName\": \"A Test Project\"\n"
				+ "}";
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		return new ArrayList<>(Arrays.asList("doodle","cadoodle"));
	}
}
