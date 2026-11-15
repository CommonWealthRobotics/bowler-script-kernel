package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.neuronrobotics.bowlerstudio.scripting.FreecadLoader;
import com.neuronrobotics.bowlerstudio.threed.ManifoldMeshCorrector;
import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.sdk.common.Log;

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
		List<CSG> meshes = selected;
		int rounds = ManifoldMeshCorrector.MAX_ROUNDS;
		IOException lastFailure = null;
		for (int round = 0; round <= rounds; round++) {
			File step = File.createTempFile(getName() + (round == 0 ? "" : "_corrected" + round), ".step");
			step.deleteOnExit();
			new STEPFile(meshes).write(step);

			// an empty FreeCAD project, then the STEP added to it
			File attempt = new File(target.getParentFile(), getName() + (round == 0 ? "" : "_" + round) + ".FCStd");
			new FreecadLoader().getDefaultContents(attempt);
			FreecadLoader.addSTEPToFreecad(attempt, step, getName());

			// the document FreeCAD wrote is the verdict: a document with no solid
			// means the STEP came apart in the import, so the meshes are corrected
			// and the next round is written from them
			if (FreecadLoader.documentCarriesSolids(attempt)) {
				if (round > 0)
					Log.debug("ConvertToFreeCAD: the STEP carried after " + round + " correction round(s)");
				// keep the good document under the target's own name
				java.nio.file.Files.move(attempt.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				return;
			}
			lastFailure = new IOException("FreeCAD read no solid from the STEP on round " + round);
			Log.debug("ConvertToFreeCAD: " + lastFailure.getMessage());
			meshes = corrected(meshes);
		}
		throw lastFailure;
	}

	/** The meshes corrected one round harder, through the Manifold kernel. */
	private static List<CSG> corrected(List<CSG> meshes) {
		List<CSG> out = new ArrayList<>();
		for (CSG mesh : meshes)
			out.add(ManifoldMeshCorrector.correctHarder(mesh, STEPFile.BREP_RESOLUTION_MM,
					STEPFile.BREP_BOUNDS_BUDGET_MM));
		return out;
	}

	/**
	 * Writes one STEP from the given meshes, correcting them through the Manifold
	 * kernel first, and loads it into the FreeCAD document. True only when
	 * FreeCAD's own import read a solid back from the file.
	 */
}
