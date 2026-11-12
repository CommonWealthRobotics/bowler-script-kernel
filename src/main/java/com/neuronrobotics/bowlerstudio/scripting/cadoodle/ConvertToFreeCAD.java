package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.neuronrobotics.bowlerstudio.scripting.FreecadLoader;
import com.neuronrobotics.bowlerstudio.threed.STEPFile;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Converts the selected objects to a FreeCAD file, removes them, and adds the
 * FreeCAD file with the normal add-file procedure. The STEP carries the
 * geometry; headless FreeCAD's built-in importer and exporter do the rest.
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
		File step = File.createTempFile(getName(), ".step");
		step.deleteOnExit();
		new STEPFile(selected).write(step);

		// an empty FreeCAD project, then the STEP added to it
		new FreecadLoader().getDefaultContents(target);
		FreecadLoader.addSTEPToFreecad(target, step, getName());
	}
}
