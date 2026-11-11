package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.neuronrobotics.bowlerstudio.scripting.FreecadLoader;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Converts the selected objects to a FreeCAD file, removes them, and adds the
 * FreeCAD file with the normal add-file procedure.
 */
public class ConvertToFreeCAD extends ConvertToFileOperation {

	@Override
	public String getExtension() {
		return "FCStd";
	}

	@Override
	public ConvertToFreeCAD setNames(List<String> names) {
		return (ConvertToFreeCAD) super.setNames(names);
	}

	@Override
	protected void writeFile(File target, List<CSG> selected) throws IOException {
		// an empty FreeCAD project, then each CSG added to it
		new FreecadLoader().getDefaultContents(target);
		for (CSG c : selected)
			FreecadLoader.addCSGToFreeCAD(target, c);
	}
}
