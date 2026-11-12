package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

public class OpenSCADLoader implements IScriptingLanguage {

	private static final Pattern THREE_MF_IMPORT = Pattern
			.compile("import\\s*\\(\\s*\"([^\"]*\\.3[mM][fF])\"\\s*\\)\\s*;?");

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, File code,
			ArrayList<Object> args) throws Exception {
		// a script that is a plain import of 3MF files needs no OpenSCAD
		// binary: the 3MFs are read natively so the per-object colours
		// survive and the live geometry is picked up on every regeneration
		List<File> threeMFs = importedThreeMFs(code);
		if (threeMFs != null) {
			ArrayList<CSG> loaded = new ArrayList<>();
			for (File threeMF : threeMFs)
				loaded.addAll(CSG.fromThreeMF(threeMF.toPath()));
			return loaded;
		}

		Path tempDir = Paths.get(ScriptingEngine.getWorkspace().getAbsolutePath(), "tmp");
		Files.createDirectories(tempDir);
		File stl = File.createTempFile(sanitizeString(code.getName()), ".stl", tempDir.toFile());

		HashMap<String, Double> params = new HashMap<String, Double>();
		if (args != null) {
			Object o = args.get(0);
			if (HashMap.class.isInstance(o)) {
				params = (HashMap<String, Double>) o;
			}
		}

		toSTLFile(code, stl, params);

		CSG back;
		if (!stl.exists())
			back = new eu.mihosoft.vrl.v3d.Cube(20).toCSG().setColor(Color.PINK);
		else
			try {
				back = Vitamins.get(db, true, stl, true);
				back.setColor(Color.YELLOW);
			} catch (Throwable e) {
				Log.error(e);
				back = new eu.mihosoft.vrl.v3d.Cube(20).toCSG().setColor(Color.LIGHTPINK);

			}
		stl.delete();
		return back;
	}

	/**
	 * Returns the 3MF files a script imports when every non-empty line is a
	 * 3MF import, or null when the script is anything else.
	 */
	public static List<File> importedThreeMFs(File script) throws IOException {
		ArrayList<File> back = new ArrayList<>();
		for (String line : Files.readAllLines(script.toPath())) {
			String trimmed = line.trim();
			if (trimmed.isEmpty())
				continue;
			Matcher matcher = THREE_MF_IMPORT.matcher(trimmed);
			if (!matcher.matches())
				return null;
			back.add(new File(script.getAbsoluteFile().getParentFile(), matcher.group(1)));
		}
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
