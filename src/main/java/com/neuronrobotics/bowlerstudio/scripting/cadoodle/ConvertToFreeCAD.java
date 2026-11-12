package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.neuronrobotics.bowlerstudio.scripting.FreecadLoader;
import com.neuronrobotics.bowlerstudio.threed.STEPFile;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

/**
 * Converts the selected objects to a FreeCAD file, removes them, and adds the
 * FreeCAD file with the normal add-file procedure. Each solid's colour is
 * written to a manifest beside the temporary STEP so it survives the headless
 * FreeCAD pass, which has no view provider to hold colours.
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
		// the STEP carries the geometry, and the manifest beside it the colours
		File step = File.createTempFile(getName(), ".step");
		step.deleteOnExit();
		new STEPFile(selected).write(step);

		Map<String, String> manifest = new LinkedHashMap<>();
		for (int i = 0; i < selected.size(); i++) {
			Color color = selected.get(i).getColor();
			if (color == null)
				continue;
			manifest.put(String.valueOf(i), toHex(color));
		}
		File colors = File.createTempFile(getName() + "_colors", ".json");
		colors.deleteOnExit();
		Files.write(colors.toPath(), new Gson().toJson(manifest).getBytes(StandardCharsets.UTF_8));

		// an empty FreeCAD project, then the STEP added to it
		new FreecadLoader().getDefaultContents(target);
		FreecadLoader.addSTEPToFreecad(target, step, getName(), colors);
	}

	private static String toHex(Color color) {
		return "#%02X%02X%02X".formatted((int) Math.round(color.getRed() * 255),
				(int) Math.round(color.getGreen() * 255), (int) Math.round(color.getBlue() * 255));
	}
}
