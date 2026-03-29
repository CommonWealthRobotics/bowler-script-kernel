package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;

import eu.mihosoft.vrl.v3d.CSG;

public class StlLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, File code,
			ArrayList<Object> args) throws Exception {
		CSG sllLoaded = Vitamins.get(db, code);
		return sllLoaded;
	}

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, String code,
			ArrayList<Object> args) throws Exception {
		throw new RuntimeException("This engine only supports files");
	}

	@Override
	public String getShellType() {
		// Auto-generated method stub
		return "Stl";
	}

	@Override
	public boolean getIsTextFile() {
		// Auto-generated method stub
		return false;
	}

	/**
	 * Get the contents of an empty file
	 *
	 * @return
	 */
	public String getDefaultContents() {
		return null;
	}

	@Override
	public ArrayList<String> getFileExtension() {
		// Auto-generated method stub
		return new ArrayList<>(Arrays.asList("stl", "STL", "Stl"));
	}

}
