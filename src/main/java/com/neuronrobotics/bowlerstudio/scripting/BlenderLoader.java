package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.delim;
import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.isMac;
import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.run;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

public class BlenderLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getShellType() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		// TODO Auto-generated method stub
		return null;
	}
	public void toSTLFile(File blenderfile,File stlout) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		File exe = DownloadManager.getRunExecutable("blender", null);
		File export = ScriptingEngine.fileFromGit(
				"https://github.com/CommonWealthRobotics/blender-bowler-cli.git", 
				"export.py");
		ArrayList<String> args = new ArrayList<>();
		if(isMac()) {
			args.add("open");
			args.add("-a");
		}
		if(stlout.exists())
			stlout.delete();
		args.add(exe.getAbsolutePath());
		args.add("--background");
		args.add("--python");
		args.add(export.getAbsolutePath());
		args.add("--");
		args.add(blenderfile.getAbsolutePath());
		args.add(stlout.getAbsolutePath());
		DownloadManager.legacySystemRun(null, stlout.getAbsoluteFile().getParentFile(), System.err, args);
		
	}
	@Override
	public void getDefaultContents(File source) {
		File exe = DownloadManager.getRunExecutable("blender", null);
		String absolutePath = source.getAbsolutePath();
		File parent = new File(absolutePath).getParentFile();
		if(source.exists()) {
			System.out.println("Blender file exists, being overwritten to blank "+absolutePath);
			source.delete();
		}
		ArrayList<String> args = new ArrayList<>();
		if(isMac()) {
			args.add("open");
			args.add("-a");
		}
		//blender --background --factory-startup --python-expr ""
		args.add(exe.getAbsolutePath());
		args.add("--background");
		args.add("--factory-startup");
		args.add("--python-expr");
		args.add("import bpy; bpy.ops.wm.save_as_mainfile(filepath='"+absolutePath+"')");
		try {
			DownloadManager.legacySystemRun(null, parent, System.err, args);
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
