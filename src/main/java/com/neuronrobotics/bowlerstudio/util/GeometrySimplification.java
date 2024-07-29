package com.neuronrobotics.bowlerstudio.util;

import java.io.File;

import com.neuronrobotics.bowlerstudio.scripting.BlenderLoader;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;

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
}
