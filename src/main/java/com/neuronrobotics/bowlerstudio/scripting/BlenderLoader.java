package com.neuronrobotics.bowlerstudio.scripting;
import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.STL;

public class BlenderLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		File stl = File.createTempFile(code.getName(), ".stl");
		stl.deleteOnExit();
		toSTLFile(code,stl);
		CSG back = Vitamins.get(stl,true);
		return back;
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		throw new RuntimeException("Blender can not run from a string");
	}

	@Override
	public String getShellType() {
		return "Blender";
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		ArrayList<String> ext = new ArrayList<>();
		ext.add("blend");
		return ext;
	}
	public static void toBlenderFile(File stlIn,File blenderfile) {
		System.out.println("Converting to Blender file before loading");
		File dir = stlIn.getAbsoluteFile().getParentFile();

		try {
			File importFile = ScriptingEngine.fileFromGit(
					"https://github.com/CommonWealthRobotics/blender-bowler-cli.git", 
					"import.py");
			if(!blenderfile.exists()) {
				//blender --background --python import_stl_to_blend.py -- /path/to/input/file.stl /path/to/output/file.blend
				ArrayList<String> args = new ArrayList<>();

				args.add(DownloadManager.getConfigExecutable("blender", null).getAbsolutePath());

				args.add("--background");
				args.add("--python");
				args.add(importFile.getAbsolutePath());
				args.add("--");
				args.add(stlIn.getAbsolutePath());
				args.add(blenderfile.getAbsolutePath());
				Thread t=run(null, dir, System.out, args);
				t.join();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}
	public static void toSTLFile(File blenderfile,File stlout) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		File exe = getConfigExecutable("blender", null);
		File export = ScriptingEngine.fileFromGit(
				"https://github.com/CommonWealthRobotics/blender-bowler-cli.git", 
				"export.py");
		ArrayList<String> args = new ArrayList<>();

		if(stlout.exists())
			stlout.delete();
		args.add(exe.getAbsolutePath());

		args.add("--background");
		args.add("--python");
		args.add(export.getAbsolutePath());
		args.add("--");
		args.add(blenderfile.getAbsolutePath());
		args.add(stlout.getAbsolutePath());
		legacySystemRun(null, stlout.getAbsoluteFile().getParentFile(), System.out, args);
		
	}
	@Override
	public void getDefaultContents(File source) {
		File exe = getConfigExecutable("blender", null);
		String absolutePath = source.getAbsolutePath();
		File parent = new File(absolutePath).getParentFile();
		if(source.exists()) {
			System.out.println("Blender file exists, being overwritten to blank "+absolutePath);
			source.delete();
		}
		ArrayList<String> args = new ArrayList<>();

		//blender --background --factory-startup --python-expr ""
		args.add(exe.getAbsolutePath());

		args.add("--background");
		args.add("--factory-startup");
		args.add("--python-expr");
		args.add("import bpy; bpy.ops.wm.save_as_mainfile(filepath='"+absolutePath+"')");
		try {
			DownloadManager.legacySystemRun(null, parent, System.out, args);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Override
	public boolean getIsTextFile() {
		// TODO Auto-generated method stub
		return false;
	}

	public static void main(String[] args) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		BlenderLoader loader = new BlenderLoader();
		
		// create test file
		File testblend = new File("test.blend");
		if(!testblend.exists())
			loader.getDefaultContents(testblend);
		loader.toSTLFile(testblend, new File("testBlender.stl"));
	}

}
