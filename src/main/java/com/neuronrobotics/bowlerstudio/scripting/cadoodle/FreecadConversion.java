package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.neuronrobotics.bowlerstudio.scripting.FreecadLoader;
import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.bowlerstudio.threed.STEPFile.ExportMode;
import com.neuronrobotics.manifold3d.CSGManifold3d;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;

/** Primary conversion followed by one validated edge-snapping compatibility attempt. */
final class FreecadConversion {
	interface Backend {
		void writeStep(List<CSG> source, File step, ExportMode mode) throws IOException;

		void importStep(File step, File document, String name) throws IOException;

		void exportThreeMF(File document, File mesh) throws IOException;

		List<CSG> readThreeMF(File mesh) throws IOException;
	}

	private final Backend backend;

	FreecadConversion(Backend backend) {
		this.backend = backend;
	}

	static FreecadConversion system() {
		return new FreecadConversion(new SystemBackend());
	}

	ExportMode write(File target, List<CSG> source, String name) throws IOException {
		Path work = Files.createTempDirectory("cadoodle-freecad-conversion-");
		work.toFile().deleteOnExit();
		try {
			publish(attempt(work, source, name, ExportMode.PRIMARY), target);
			return ExportMode.PRIMARY;
		} catch (MeshLoadFailure primaryFailure) {
			progress(0, 2, "FreeCAD: retrying with edge snapping");
			Log.error("FreeCAD primary 3MF load failed; trying edge snapping");
			Log.error(primaryFailure);
			try {
				publish(attempt(work, source, name, ExportMode.EDGE_SNAPPING), target);
				return ExportMode.EDGE_SNAPPING;
			} catch (IOException | RuntimeException fallbackFailure) {
				IOException failure = new IOException(
						"FreeCAD conversion failed with both primary and edge-snapping export; artifacts: " + work,
						fallbackFailure);
				failure.addSuppressed(primaryFailure);
				throw failure;
			}
		}
	}

	private File attempt(Path work, List<CSG> source, String name, ExportMode mode) throws IOException {
		File step = work.resolve(mode.name() + ".step").toFile();
		File document = work.resolve(mode.name() + ".FCStd").toFile();
		File mesh = work.resolve(mode.name() + ".3mf").toFile();
		step.deleteOnExit();
		document.deleteOnExit();
		mesh.deleteOnExit();
		progress(0, 4, "FreeCAD: preparing STEP geometry");
		backend.writeStep(source, step, mode);
		progress(1, 4, "FreeCAD: importing STEP document");
		backend.importStep(step, document, name);
		try {
			progress(2, 4, "FreeCAD: exporting solid meshes");
			backend.exportThreeMF(document, mesh);
		} catch (FreecadLoader.MeshExportFailure failure) {
			throw new MeshLoadFailure("Cannot export " + mode + " FreeCAD solid meshes: " + document, failure);
		}
		try {
			progress(3, 4, "FreeCAD: loading exported meshes");
			validate(source, backend.readThreeMF(mesh));
		} catch (IOException | RuntimeException failure) {
			throw new MeshLoadFailure("Cannot load " + mode + " FreeCAD 3MF: " + mesh, failure);
		}
		return document;
	}

	private static void publish(File document, File target) throws IOException {
		if (!document.isFile() || document.length() == 0)
			throw new IOException("FreeCAD produced no document: " + document);
		progress(0, 2, "FreeCAD: saving verified document");
		Files.move(document.toPath(), target.toPath());
		progress(1, 2, "FreeCAD: verified document saved");
	}

	private static void validate(List<CSG> source, List<CSG> loaded) throws IOException {
		if (loaded.isEmpty())
			throw new IOException("FreeCAD 3MF contains no solids");
		CSGManifold3d nativeApi = CSG.getManifold();
		boolean owned = nativeApi == null;
		try {
			if (owned)
				nativeApi = new CSGManifold3d();
			double actualVolume = 0;
			int solidIndex = 0;
			int total = loaded.size() + source.size();
			for (CSG solid : loaded) {
				progress(solidIndex++, total, "FreeCAD: checking exported solid");
				if (solid.getTriCount() == 0)
					throw new IOException("FreeCAD 3MF contains an empty solid");
				double volume = nativeApi.volume(solid);
				if (!Double.isFinite(volume) || volume <= 0)
					throw new IOException("FreeCAD 3MF contains a solid with invalid volume: " + volume);
				actualVolume += volume;
			}
			double sourceVolume = 0;
			for (CSG solid : source) {
				progress(solidIndex++, total, "FreeCAD: checking source volume");
				sourceVolume += nativeApi.volume(solid);
			}
			if (!Double.isFinite(sourceVolume) || !Double.isFinite(actualVolume)
					|| Math.abs(actualVolume - sourceVolume) > Math.max(1e-5, Math.abs(sourceVolume) * 1e-6))
				throw new IOException("FreeCAD round-trip volume changed from " + sourceVolume + " to " + actualVolume);
		} catch (IOException failure) {
			throw failure;
		} catch (Error fatal) {
			throw fatal;
		} catch (Throwable failure) {
			throw new IOException("FreeCAD 3MF is not a valid oriented manifold", failure);
		} finally {
			if (owned && nativeApi != null)
				nativeApi.close();
		}
	}

	private static void progress(int index, int total, String message) {
		CSG.getProgressMoniter().progressUpdate(index, total, message, null);
	}

	private static final class MeshLoadFailure extends IOException {
		private static final long serialVersionUID = 1L;

		MeshLoadFailure(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private static final class SystemBackend implements Backend {
		@Override
		public void writeStep(List<CSG> source, File step, ExportMode mode) throws IOException {
			new STEPFile(source, mode).write(step);
		}

		@Override
		public void importStep(File step, File document, String name) throws IOException {
			new FreecadLoader().getDefaultContents(document);
			FreecadLoader.addSTEPToFreecad(document, step, name);
			if (!document.isFile() || document.length() == 0)
				throw new IOException("FreeCAD produced no document: " + document);
		}

		@Override
		public void exportThreeMF(File document, File mesh) throws IOException {
			try {
				FreecadLoader.to3MFFile(document, mesh);
			} catch (FreecadLoader.MeshExportFailure failure) {
				throw failure;
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IOException("FreeCAD 3MF export interrupted", interrupted);
			} catch (Exception failure) {
				throw new IOException("FreeCAD 3MF export failed", failure);
			}
		}

		@Override
		public List<CSG> readThreeMF(File mesh) throws IOException {
			try {
				return CSG.fromThreeMF(mesh.toPath());
			} catch (Error fatal) {
				throw fatal;
			} catch (Throwable failure) {
				throw new IOException("Cannot read FreeCAD 3MF: " + mesh, failure);
			}
		}
	}
}
