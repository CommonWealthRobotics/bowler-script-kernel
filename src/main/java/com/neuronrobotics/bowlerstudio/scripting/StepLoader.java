/**
 *
 */
package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import com.neuronrobotics.bowlerstudio.threed.STEPFile;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Imports a STEP file. A part comes back as one CSG, and an assembly as one CSG
 * per placed part, each at the place its occurrence puts it and carrying the
 * colour the file assigned it.
 */
public class StepLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, File code,
			ArrayList<Object> args) throws Exception {
		List<CSG> parts;
		try (FileInputStream in = new FileInputStream(code)) {
			parts = new STEPFile(in).getImported();
		}
		return parts.size() == 1 ? parts.get(0) : parts;
	}

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, String code,
			ArrayList<Object> args) throws Exception {
		throw new RuntimeException("A STEP file can not be instantiated from a string");
	}

	@Override
	public String getShellType() {
		return "step";
	}

	@Override
	public ArrayList<String> getFileExtension() {
		ArrayList<String> ext = new ArrayList<>();
		ext.add("step");
		ext.add("stp");
		ext.add("STEP");
		ext.add("STP");
		return ext;
	}

	@Override
	public boolean getIsTextFile() {
		return false;
	}

	@Override
	public void getDefaultContents(File stepGenFile) {

	}
}
