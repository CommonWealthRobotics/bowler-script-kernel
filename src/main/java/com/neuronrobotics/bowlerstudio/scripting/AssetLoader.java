package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;


public class AssetLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, File code,
			ArrayList<Object> args) throws Exception {

		return code;
	}

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, String code,
			ArrayList<Object> args) throws Exception {
		throw new RuntimeException("This engine only supports files");
	}

	@Override
	public String getShellType() {
		// Auto-generated method stub
		return "Asset";
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
		return new ArrayList<>(Arrays.asList("css", "png", "jpg", "jpeg", "html", "js"));
	}

}
