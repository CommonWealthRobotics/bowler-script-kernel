/**
 *
 */
package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.util.ArrayList;
import eu.mihosoft.vrl.v3d.CSG;

/**
 *
 */
public class ThreeMFLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, File code,
			ArrayList<Object> args) throws Exception {
		return CSG.fromThreeMF(code.toPath());
	}

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, String code,
			ArrayList<Object> args) throws Exception {
		throw new RuntimeException("Freecad file can not be instantiated from a string");
	}

	@Override
	public String getShellType() {
		return "3mf";
	}

	@Override
	public ArrayList<String> getFileExtension() {
		ArrayList<String> ext = new ArrayList<>();
		ext.add("3mf");
		ext.add("3Mf");
		ext.add("3MF");
		return ext;
	}

	@Override
	public boolean getIsTextFile() {
		return false;
	}

	@Override
	public void getDefaultContents(File freecadGenFile) {

	}

}
