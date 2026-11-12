package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import com.neuronrobotics.manifold3d.NonManifoldShapeError;

/**
 * Converts the selected objects to an OpenSCAD file, removes them, and adds the
 * OpenSCAD file with the normal add-file procedure. The geometry travels in a
 * 3MF file beside the script; the script only imports that 3MF. Unlike the
 * Blender and FreeCAD conversions the 3MF is re-written from the source shapes
 * every time the operation runs, so the OpenSCAD file always operates on live
 * data and stays valid after timeline replays.
 */
public class ConvertToOpenSCAD extends ConvertToFileOperation {

	@Override
	public String getExtension() {
		return "scad";
	}

	@Override
	public boolean rewritesFileEachRun() {
		return true;
	}

	@Override
	public ConvertToOpenSCAD setNames(List<String> names) {
		return (ConvertToOpenSCAD) super.setNames(names);
	}

	@Override
	protected void writeFile(File target, List<CSG> selected) throws IOException {
		// the 3MF beside the script carries the live geometry, colours included
		File threeMF = newThreeMFFile();
		try {
			CSG.toThreeMF(selected, true, threeMF.toPath());
		} catch (ColinearPointsException | NonManifoldShapeError e) {
			throw new IOException("Failed to export the shapes to " + threeMF, e);
		}
		if (!threeMF.exists())
			throw new IOException("Failed to export the shapes to " + threeMF);

		// the script is only an import of that 3MF, so it survives every rewrite
		String script = "import(\"" + threeMF.getName() + "\");\n";
		Files.write(target.toPath(), script.getBytes(StandardCharsets.UTF_8));
	}

	private File newThreeMFFile() {
		File self = getCaDoodleFile().getSelf();
		File doodleDir = new File(self.getAbsolutePath()).getParentFile();
		return new File(doodleDir, getName() + ".3mf");
	}

}
