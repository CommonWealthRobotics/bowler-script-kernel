package com.neuronrobotics.bowlerstudio.scripting;
import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

public class OpenSCADLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, File code,
			ArrayList<Object> args) throws Exception {
		File stl = File.createTempFile(sanitizeString(code.getName()), ".stl");
		stl.deleteOnExit();
		HashMap<String, Double> params = new HashMap<String, Double>();
		if (args != null) {
			Object o = args.get(0);
			if (HashMap.class.isInstance(o)) {
				params = (HashMap<String, Double>) o;
			}
		}

		toSTLFile(code, stl, params);
		CSG back = Vitamins.get(db,true, stl, true);
		back.setColor(Color.YELLOW);
		return back;
	}

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, String code,
			ArrayList<Object> args) throws Exception {
		throw new RuntimeException("Blender can not run from a string");
	}

	@Override
	public String getShellType() {
		return "OpenSCAD";
	}

	@Override
	public ArrayList<String> getFileExtension() {
		ArrayList<String> ext = new ArrayList<>();
		ext.add("scad");
		ext.add("SCad");

		return ext;
	}

	public static void toSTLFile(File openscadfile, File stlout, HashMap<String, Double> params)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		File exe = getConfigExecutable("openscad", null);
		if (params == null)
			params = new HashMap<String, Double>();
		ArrayList<String> args = new ArrayList<>();

		if (stlout.exists())
			stlout.delete();
		args.add(exe.getAbsolutePath());
		for (String key : params.keySet()) {
			args.add("-D");
			args.add(key + "=" + params.get(key));
		}
		args.add("-o");
		args.add(stlout.getAbsolutePath());
		args.add(openscadfile.getAbsolutePath());
		legacySystemRun(null, stlout.getAbsoluteFile().getParentFile(), System.out, args);
	}

	@Override
	public String getDefaultContents() {
		return "cube([30, 20, 10]);";
	}

	@Override
	public boolean getIsTextFile() {
		return true;
	}

	public static void main(String[] args)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		OpenSCADLoader loader = new OpenSCADLoader();

		// create test file
		File testblend = new File("test.scad");
		if (!testblend.exists())
			loader.getDefaultContents(testblend);
		HashMap<String, Double> params = new HashMap<String, Double>();
		toSTLFile(testblend, new File("testscad.stl"), params);
	}

}
