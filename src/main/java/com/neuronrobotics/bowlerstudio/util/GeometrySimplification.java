package com.neuronrobotics.bowlerstudio.util;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.legacySystemRun;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import com.neuronrobotics.bowlerstudio.scripting.BlenderLoader;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;

import eu.mihosoft.vrl.v3d.CSG;

public class GeometrySimplification {
	/**
	 * Re-mesh the CSG to a voxelated mesh
	 * @param incoming the incoming CSG
	 * @param MMVoxel the mm dimentions of the voxel
	 * @return a re-meshed CSG
	 * @throws Exception
	 */
	public static CSG remesh(CSG incoming, double MMVoxel) throws Exception {
		return BlenderLoader.remesh(incoming, MMVoxel);
	}
	/**
	 * RE-mesh an STL file in place
	 * This modifys the original STL
	 * @param stlout the file in which he STL is stored
	 * @param MMVoxel the mm dimention of the voxels
	 * @throws Exception
	 */
	public static void remeshSTLFile(File stlout,double MMVoxel) throws Exception {
		BlenderLoader.remeshSTLFile(stlout, MMVoxel);
	}
	/**
	 * Simplify an SVG file
	 * @param incoming the incoming SVG file
	 * @param threshhold the threshhold value  (default is 0.002
	 * @return A new SVG file that is changed
	 */
	public static File simplifySVG(File incoming) {
		return simplifySVG(incoming,0.002);
	}
	/**
	 * Simplify an SVG file
	 * @param incoming the incoming SVG file
	 * @param threshhold the threshhold value  (default is 0.002
	 * @return A new SVG file that is changed
	 */
	public static File simplifySVG(File incoming, double threshhold) {
		try {
			File inkscape = DownloadManager.getConfigExecutable("inkscape", null);
			File svg = File.createTempFile(incoming.getName(), ".svg");
			List <String >args = Arrays.asList(
					inkscape.getAbsolutePath(),
		            "--actions",
		            "\"select-all:all;path-simplify:threshold=" + threshhold+ ";;export-overwrite;export-do;quit-inkscape\"",
		            incoming.getAbsolutePath()
					);
			legacySystemRun(null, inkscape.getAbsoluteFile().getParentFile(), System.out, args);
			args = Arrays.asList(
					inkscape.getAbsolutePath(),
					"--export-plain-svg",
		            "--export-type=svg",
		            "--vacuum-defs",
		            "--export-filename="+svg.getAbsolutePath(),
		            incoming.getAbsolutePath()
					);
			legacySystemRun(null, inkscape.getAbsoluteFile().getParentFile(), System.out, args);
			return svg;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return incoming;
	}
}
