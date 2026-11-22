package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.util.List;


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
		CSG.getProgressMoniter().progressUpdate(0, 100, "Converting CSG to Freecad", null);
		FreecadConversion.system().write(target, selected, getName());
		CSG.getProgressMoniter().progressUpdate(99, 100, "Verified FreeCAD conversion", null);
	}
}
