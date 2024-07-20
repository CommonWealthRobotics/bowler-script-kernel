package com.neuronrobotics.bowlerstudio.scripting;
import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.STL;
import javafx.scene.paint.Color;

public class OpenSCADLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		File stl = File.createTempFile(sanitizeString(code.getName()), ".stl");
		stl.deleteOnExit();
		toSTLFile(code,stl);
		CSG back = Vitamins.get(stl,true);
		back.setColor(Color.YELLOW);
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
		ext.add("scad");
		ext.add("SCad");
		
		return ext;
	}




	public static void toSTLFile(File openscadfile,File stlout) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		File exe = getConfigExecutable("openscad", null);

		ArrayList<String> args = new ArrayList<>();

		if(stlout.exists())
			stlout.delete();
		args.add(exe.getAbsolutePath());
		args.add("-o");
		args.add(stlout.getAbsolutePath());
		args.add(openscadfile.getAbsolutePath());
		legacySystemRun(null, stlout.getAbsoluteFile().getParentFile(), System.out, args);
	}
	@Override
	public String getDefaultContents() {
		return "cube([3, 2, 1]);";
	}

	@Override
	public boolean getIsTextFile() {
		return true;
	}

	public static void main(String[] args) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		OpenSCADLoader loader = new OpenSCADLoader();
		
		// create test file
		File testblend = new File("test.scad");
		if(!testblend.exists())
			loader.getDefaultContents(testblend);
		toSTLFile(testblend, new File("testscad.stl"));
	}

}
