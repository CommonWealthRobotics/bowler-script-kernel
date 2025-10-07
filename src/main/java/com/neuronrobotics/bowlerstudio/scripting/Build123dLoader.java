package com.neuronrobotics.bowlerstudio.scripting;
import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.STL;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.scene.paint.Color;

public class Build123dLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(CSGDatabaseInstance db,File code, ArrayList<Object> args) throws Exception {
		File stl = File.createTempFile(sanitizeString(code.getName()), ".stl");
		stl.deleteOnExit();
		HashMap<String,Double> params=new HashMap<String, Double>();
		if(args!=null) {
			Object o = args.get(0);
			if(HashMap.class.isInstance(o)) {
				params=(HashMap<String,Double>)o;
			}
		}
		
		toSTLFile(code,stl,params);
		CSG back = Vitamins.get(db,stl,true);
		back.setColor(Color.ANTIQUEWHITE);
		return back;
	}

	@Override
	public Object inlineScriptRun(CSGDatabaseInstance db,String code, ArrayList<Object> args) throws Exception {
		throw new RuntimeException("Build123d can not run from a string");
	}

	@Override
	public String getShellType() {
		return "Build123d";
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		ArrayList<String> ext = new ArrayList<>();
		ext.add("py");
		ext.add("build123d");
		return ext;
	}




	public static void toSTLFile(File build123dScript,File stlout, HashMap<String,Double> params) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		File exe = getConfigExecutable("build123d", null);
		File dir = getDestinationDir("build123d");
		if(params==null)
			params=new HashMap<String, Double>();
		ArrayList<String> args = new ArrayList<>();

		if(stlout.exists())
			stlout.delete();
		args.add(exe.getAbsolutePath());
		args.add("run");
		args.add("python");
		for(String key:params.keySet()) {
			args.add("-D");
			args.add(key+"="+params.get(key));
		}
		args.add(build123dScript.getAbsolutePath());
//		args.add("-o");
//		args.add(stlout.getAbsolutePath());
		legacySystemRun(null, dir, System.out, args);
	}
	@Override
	public String getDefaultContents() {
		return "from build123d import *\n"
				+ "\n"
				+ "cube = Box(10, 10, 10)";
	}

	@Override
	public boolean getIsTextFile() {
		return true;
	}

	public static void main(String[] args) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		Build123dLoader loader = new Build123dLoader();
		Log.enableDebugPrint();
		// create test file
		File testblend = new File("build123dTest.py");
		if(!testblend.exists())
			loader.getDefaultContents(testblend);
		HashMap<String,Double> params = new HashMap<String, Double>();
		toSTLFile(testblend, new File("build123dTest.py.stl"),params);
	}

}
