package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.HashMap;
import java.lang.reflect.Type;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.google.gson.Gson;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.manifold3d.NonManifoldShapeError;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.scene.paint.Color;

public class Build123dLoader implements IScriptingLanguage {

	private static HashMap<String, Object> map;

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
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException,
			NonManifoldShapeError, ColinearPointsException {
		Path tempDir = Files.createTempDirectory("build123d-");

		return toCSG(db, null, tempDir, params);
	}

	public static List<CSG> toCSG(CSGDatabaseInstance db, Path stl, ArrayList<Object> params)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException,
			NonManifoldShapeError, ColinearPointsException {
		return toCSG(db, null, stl, params);
	}

	public static List<CSG> toCSG(CSGDatabaseInstance db, File code, Path stl, ArrayList<Object> params)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException,
			NonManifoldShapeError, ColinearPointsException {
		toSTLFile(code, stl, params);

		ArrayList<CSG> back = new ArrayList<CSG>();
		for (File f : stl.toFile().listFiles()) {
			Log.debug("Loading " + f);
			if (f.getName().toLowerCase().endsWith(".stl")) {
				CSG b;
				try {
					b = Vitamins.get(db, true, f, true);
					b.setColor(Color.ANTIQUEWHITE);
				} catch (Throwable e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					b = new eu.mihosoft.vrl.v3d.Cube(20).toCSG().setColor(Color.PINK);
				}
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

	public static Map<String, Object> getTypeOptions(String... options) {
		if (map == null) {
			File exe = getConfigExecutable("build123d", null);
			File dir = getDestinationDir("build123d");
			ArrayList<String> args = new ArrayList<>();
			args.add(exe.getAbsolutePath());
			args.add("-m");
			args.add("build123d_cli");
			for (String s : options) {
				args.add(s);
			}
			args.add("--json-schema");
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PrintStream ps = new PrintStream(baos, true, "UTF-8");
				legacySystemRun(null, dir, ps, args);
				String result = baos.toString("UTF-8");
				// Log.debug(result);
				Gson gson = new Gson();

				Type mapType = new TypeToken<HashMap<Object, Object>>() {
				}.getType();
				map = gson.fromJson(result, mapType);
				if (map != null)
					return map;
			} catch (IOException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			map = new HashMap<String, Object>();
		}
		List<String> asList = Arrays.asList(options);
		if (asList.size() == 0) {
			return map;
		}
		Map<String, Object> hashMap = (Map<String, Object>) map.get(asList.get(0));
		if (asList.size() == 1) {
			if (hashMap != null)
				return hashMap;
		}
		if (asList.size() > 2) {
			Map<String, Object> hashMap2 = (Map<String, Object>) hashMap.get(asList.get(1));
			if (asList.size() == 2)
				return hashMap2;
			return (Map<String, Object>) hashMap2.get(asList.get(2));
		}

		return map;
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
		args.add("-m");
		args.add("build123d_cli");
		for (Object key : params) {
			String string = key.toString();
			if (string.contentEquals("gggears"))
				string = "py_gearworks";
			if (string.contentEquals("spurgear"))
				string = "SpurGear";
			args.add(string);
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
