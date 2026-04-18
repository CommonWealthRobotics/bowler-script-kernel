package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.manifold3d.NonManifoldShapeError;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.scene.paint.Color;

public class Build123dLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(CSGDatabaseInstance db, File code, ArrayList<Object> args) throws Exception {
		ArrayList<Object> params = new ArrayList<>();
		if (args != null) {
			Object o = args.get(0);
			if (HashMap.class.isInstance(o)) {
				params = (ArrayList<Object>) o;
			}
		}
		Path tempDir = Files.createTempDirectory("build123d-");

		List<CSG> back = toCSG(db, code, tempDir, params);
		return back;
	}

	public static List<CSG> toCSG(CSGDatabaseInstance db, ArrayList<Object> params)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException, NonManifoldShapeError, ColinearPointsException {
		Path tempDir = Files.createTempDirectory("build123d-");

		return toCSG(db, null, tempDir, params);
	}

	public static List<CSG> toCSG(CSGDatabaseInstance db, Path stl, ArrayList<Object> params)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException, NonManifoldShapeError, ColinearPointsException {
		return toCSG(db, null, stl, params);
	}

	public static List<CSG> toCSG(CSGDatabaseInstance db, File code, Path stl, ArrayList<Object> params)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException, NonManifoldShapeError, ColinearPointsException {
		toSTLFile(code, stl, params);

		ArrayList<CSG> back = new ArrayList<CSG>();
		for (File f : stl.toFile().listFiles()) {
			Log.debug("Loading " + f);
			if (f.getName().toLowerCase().endsWith(".stl")) {
				CSG b = Vitamins.get(db,true, f, true);
				b.setColor(Color.ANTIQUEWHITE);
				back.add(b);
			}
		}
		return back;
	}

	@Override
	public Object inlineScriptRun(CSGDatabaseInstance db, String code, ArrayList<Object> args) throws Exception {
		throw new RuntimeException("Build123d can not run from a string");
	}

	@Override
	public String getShellType() {
		return "Build123d";
	}

	@Override
	public ArrayList<String> getFileExtension() {
		ArrayList<String> ext = new ArrayList<>();
		ext.add("py");
		ext.add("build123d");
		return ext;
	}

	public static void toSTLFile(Path stlout, ArrayList<Object> params)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		toSTLFile(null, stlout, params);
	}

	public static void toSTLFile(File build123dScript, Path stlout, ArrayList<Object> params)
			throws IOException, InterruptedException {
		File exe = getConfigExecutable("build123d", null);
		File dir = getDestinationDir("build123d");
		if (params == null)
			params = new ArrayList<Object>();
		ArrayList<String> args = new ArrayList<>();

		if (!stlout.toFile().isDirectory())
			throw new RuntimeException("Output file should be a directory");
		args.add(exe.getAbsolutePath());
		args.add("run");
		if (build123dScript != null) {
			args.add("python");
			args.add(build123dScript.getAbsolutePath());
		} else
			args.add("build123d_cli");
		for (Object key : params) {
			args.add(key.toString());
		}

		args.add("export_directory");
		args.add(stlout.toFile().getAbsolutePath());
		legacySystemRun(null, dir, System.out, args);
	}

	@Override
	public String getDefaultContents() {
		return "from build123d import *\n" + "\n" + "cube = Box(10, 10, 10)";
	}

	@Override
	public boolean getIsTextFile() {
		return true;
	}

	public static void main(String[] args)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		Build123dLoader loader = new Build123dLoader();
		Log.enableDebugPrint();
		// create test file
		File testblend = new File("build123dTest.py");
		if (!testblend.exists())
			loader.getDefaultContents(testblend);
		ArrayList<Object> params = new ArrayList<Object>();
		toSTLFile(testblend, new File("build123dTest").toPath(), params);
	}

}
