package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.FilenameUtils;

import com.neuronrobotics.bowlerstudio.scripting.BlenderLoader;
import com.neuronrobotics.bowlerstudio.scripting.FreecadLoader;
import com.neuronrobotics.manifold3d.NonManifoldShapeError;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.svg.SVGExporter;
import javafx.scene.transform.Affine;

public class CadFileExporter {

	IMobileBaseUI ui;

	public CadFileExporter(IMobileBaseUI myUI) {
		ui = myUI;
	}

	public CadFileExporter() {
		ui = new IMobileBaseUI() {

			@Override
			public void setSelectedCsg(Collection<CSG> selectedCsg) {
				// Auto-generated method stub

			}

			@Override
			public void setAllCSG(Collection<CSG> toAdd, File source) {
				// Auto-generated method stub

			}

			@Override
			public void highlightException(File fileEngineRunByName, Throwable ex) {
				// Auto-generated method stub

			}

			@Override
			public Set<CSG> getVisibleCSGs() {
				// Auto-generated method stub
				return null;
			}

			@Override
			public void addCSG(Collection<CSG> toAdd, File source) {
				// Auto-generated method stub

			}

			@Override
			public void setSelected(Affine rootListener) {
				// Auto-generated method stub

			}
		};
	}

	public ArrayList<File> generateManufacturingParts(List<CSG> totalAssembly, File baseDirForFiles)
			throws IOException {
		ArrayList<File> allCadStl = new ArrayList<>();
		if (!baseDirForFiles.isDirectory()) {
			String fileNameWithOutExt = FilenameUtils.removeExtension(baseDirForFiles.getAbsolutePath());
			baseDirForFiles = new File(fileNameWithOutExt);
			if (!baseDirForFiles.exists())
				baseDirForFiles.mkdirs();
		}
		File dir;

		dir = baseDirForFiles;
		if (!dir.exists())
			dir.mkdirs();

		int index = 0;
		ArrayList<CSG> svgParts = new ArrayList<>();
		ArrayList<CSG> blendParts = new ArrayList<>();
		ArrayList<CSG> freecadParts = new ArrayList<>();
		ArrayList<CSG> parts3mf = new ArrayList<>();
		String svgName = null;
		String blendName = null;
		String freecadName = null;
		String name3mf = null;
		String directoryWherePartsGo = "";
		for (CSG part : totalAssembly) {
			if (part.getNumberOfTriangles() == 0)
				continue;
			String name = part.getName();
			CSG manufactured = part.prepForManufacturing();
			if (manufactured == null) {
				continue;
			}
			manufactured.setName(part.getName());
			if (name.length() == 0)
				name = "Part-Num-" + index;
			directoryWherePartsGo = dir.getAbsolutePath() + "/" + name;
			index++;
			if (part.getExportFormats() == null) {
				try {
					allCadStl.add(makeStl(directoryWherePartsGo, manufactured));// default to stl
				} catch (Throwable t) {
					com.neuronrobotics.sdk.common.Log.error("Failed to generate " + part.getName());
					com.neuronrobotics.sdk.common.Log.error(t);
				}
			} else {

				for (String format : part.getExportFormats()) {
					if (format.toLowerCase().contains("obj")) {
						try {
							allCadStl.add(makeObj(directoryWherePartsGo, manufactured));
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						} //
						ui.setCsg(manufactured, null);
					}
					if (format.toLowerCase().contains("stl")) {
						allCadStl.add(makeStl(directoryWherePartsGo, manufactured));// default to stl
						ui.setCsg(manufactured, null);
					}
					if (format.toLowerCase().contains("svg")) {
						if (svgName == null) {
							svgName = part.toString();
						}
						svgParts.add(manufactured);
						ui.setAllCSG(svgParts, null);
					}
					if (format.toLowerCase().contains("blend")) {
						// allCadStl.add(makeBlender(nameBase,manufactured));//
						ui.setCsg(manufactured, null);
						if (blendName == null) {
							blendName = part.toString();
						}
						blendParts.add(manufactured);
					}
					if (format.toLowerCase().contains("freecad")) {
						// allCadStl.add(makeBlender(nameBase,manufactured));//
						ui.setCsg(manufactured, null);
						if (freecadName == null) {
							freecadName = part.toString();
						}
						freecadParts.add(manufactured);
					}
					if (format.toLowerCase().contains("3mf")) {
						// allCadStl.add(makeBlender(nameBase,manufactured));//
						ui.setCsg(manufactured, null);
						if (name3mf == null) {
							name3mf = part.toString();
						}
						parts3mf.add(manufactured);
					}
				}

			}
		}
		if (svgParts.size() > 0) {
			allCadStl.add(makeSvg(directoryWherePartsGo, svgParts));// default to stl
		}
		if (blendParts.size() > 0) {
			allCadStl.add(makeBlender(directoryWherePartsGo, blendParts));// default to stl
		}
		if (freecadParts.size() > 0) {
			allCadStl.add(makeFreecad(directoryWherePartsGo, freecadParts));// default to stl
		}
		if (parts3mf.size() > 0) {
			try {
				allCadStl.add(make3mf(directoryWherePartsGo, parts3mf));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ColinearPointsException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NonManifoldShapeError e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} // default to stl
		}
		com.neuronrobotics.sdk.common.Log.debug("Finished Export!");
		return allCadStl;
	}

	private File makeFreecad(String nameBase, List<CSG> current) throws IOException {
		File blend = new File(nameBase + ".FCStd");
		com.neuronrobotics.sdk.common.Log.debug("FreeCAD Writing " + blend.getAbsolutePath());
		for (CSG tmp : current)
			FreecadLoader.addCSGToFreeCAD(blend, tmp);
		return blend;
	}

	private File make3mf(String nameBase, List<CSG> current)
			throws IOException, ColinearPointsException, NonManifoldShapeError {
		File blend = new File(nameBase + ".3mf");
		com.neuronrobotics.sdk.common.Log.debug("3mf Writing " + blend.getAbsolutePath());
		CSG.toThreeMF(current, true, blend.toPath());
		return blend;
	}

	private File makeStl(String nameBase, CSG tmp) throws IOException {
		File stl = new File(nameBase + ".stl");
		// boolean manifold=CSG.isPreventNonManifoldTriangles();
		// CSG.setPreventNonManifoldTriangles(false);
		tmp.toStl(Paths.get(stl.getAbsolutePath()));
		// CSG.setPreventNonManifoldTriangles(manifold);
		com.neuronrobotics.sdk.common.Log.debug("STL Writing " + stl.getAbsolutePath());
		return stl;
	}

	private File makeObj(String nameBase, CSG tmp) throws Exception {
		File stl = new File(nameBase + ".obj");

		FileUtil.write(Paths.get(stl.getAbsolutePath()), tmp.toObjString());
		com.neuronrobotics.sdk.common.Log.debug("Obj Writing " + stl.getAbsolutePath());
		return stl;
	}

	private File makeBlender(String nameBase, List<CSG> current) throws IOException {
		File blend = new File(nameBase + ".blend");
		com.neuronrobotics.sdk.common.Log.debug("Blender Writing " + blend.getAbsolutePath());
		for (CSG tmp : current)
			BlenderLoader.toBlenderFile(null, tmp, blend);
		return blend;
	}

	private File makeSvg(String nameBase, List<CSG> currentCsg) throws IOException {
		File stl = new File(nameBase + ".svg");

		for (CSG csg : currentCsg) {
			if (csg.getSlicePlanes() == null) {
				csg.addSlicePlane(new Transform());
			}
		}
		try {
			try {
				SVGExporter.export(currentCsg, stl);
			} catch (Exception e) {
				ArrayList<CSG> movedDown = new ArrayList<>();
				for (CSG csg : currentCsg) {
					CSG movez = csg.toZMin().movez(-0.01);
					if (movez.getSlicePlanes() == null)
						movez.addSlicePlane(new Transform());
					movez.setName(csg.getName());
					movedDown.add(movez);
				}
				SVGExporter.export(movedDown, stl);

			}

			com.neuronrobotics.sdk.common.Log.debug("Writing " + stl.getAbsolutePath());
		} catch (Throwable t) {
			com.neuronrobotics.sdk.common.Log.error("ERROR, NO pixelization engine available for slicing");
			com.neuronrobotics.sdk.common.Log.error(t);
		}

		return stl;
	}

}
