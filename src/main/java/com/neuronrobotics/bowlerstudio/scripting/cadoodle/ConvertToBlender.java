package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.neuronrobotics.bowlerstudio.scripting.BlenderLoader;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Converts the selected objects to a Blender file, removes them, and adds the
 * Blender file with the normal add-file procedure.
 */
public class ConvertToBlender extends ConvertToFileOperation {

	@Override
	public String getExtension() {
		return "blend";
	}

	@Override
	public ConvertToBlender setNames(List<String> names) {
		return (ConvertToBlender) super.setNames(names);
	}

	@Override
	protected void writeFile(File target, List<CSG> selected) throws IOException {
		for (CSG c : selected)
			BlenderLoader.toBlenderFile(getDb(), c, target);
	}
}
