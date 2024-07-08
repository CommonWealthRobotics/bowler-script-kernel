/**
 * 
 */
package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.legacySystemRun;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

/**
 * 
 */
public class FreecadLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		throw new RuntimeException("Freecad file can not be instantiated from a string");
	}

	@Override
	public String getShellType() {
		return "FreeCAD";
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		ArrayList<String> ext = new ArrayList<>();
		ext.add("FCStd");
		return ext;
	}

	@Override
	public boolean getIsTextFile() {
		return false;
	}
	
	@Override
	public void getDefaultContents(File freecadGenFile) {
		File freecad = DownloadManager.getRunExecutable("freecad", null);
		try {
			File newFile = ScriptingEngine.fileFromGit(
					"https://github.com/CommonWealthRobotics/freecad-bowler-cli.git", 
					"newFile.py");
			ArrayList<String> args = new ArrayList<>();
	
			if(freecadGenFile.exists())
				freecadGenFile.delete();
			args.add(freecad.getAbsolutePath());
	
			args.add("-c");
			args.add(newFile.getAbsolutePath());
			args.add(freecadGenFile.getAbsolutePath());
			legacySystemRun(null, freecadGenFile.getAbsoluteFile().getParentFile(), System.out, args);
		}catch(Throwable t) {
			t.printStackTrace();
		}
		
	}
	public static void toSTLFile(File blenderfile,File stlout) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {

	}
	/**
	 * @param args
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws GitAPIException 
	 * @throws TransportException 
	 * @throws InvalidRemoteException 
	 */
	public static void main(String[] args) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		FreecadLoader l = new FreecadLoader();
		File test = new File("test.FCStd");
		if(!test.exists())
			l.getDefaultContents(test);
		l.toSTLFile(test, new File("testFreeccad.stl"));
		
	}

}
