package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

public class ArduinoLoader implements IScriptingLanguage {
	
	private static  String ARDUINO = "arduino";

	HashMap<String,HashMap<String,Object>> database;
	
	private static String defaultPort = null;
	private static String defaultBoard = null;
	
	@SuppressWarnings("unchecked")
	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		if(args==null){
			args =  new ArrayList<>();
		}
		if (database==null){
			database =(HashMap<String,HashMap<String,Object>>) ScriptingEngine
					.gitScriptRun("https://github.com/madhephaestus/Arduino-Boards-JSON.git", 
							"boards.json", null);
		}
		String execString = getARDUINOExec();
		
		if(args.size()>0){
			setDefaultBoard((String) args.get(0));
		}
		if(getDefaultBoard()!=null){
			execString += " --board "+getDefaultBoard();
			if(args.size()>1){
				setDefaultPort((String) args.get(1));
			}
		}
		if(getDefaultPort()!=null){
			execString += " --port "+getDefaultPort();
		}
		HashMap<String,Object> configs = database.get(getDefaultBoard());
		File ino   = findIno(code);
		if(ino==null){
			System.out.println("Error: no .ino file found!");
			return null;
		}
		execString += " --upload "+ino.getAbsolutePath();
		
		System.out.println("Arduino Load: \n"+execString);
		run(getARDUINOExec()+" --install-library BowlerCom");
		//run(getARDUINOExec()+" --get-pref ");
		run(execString);
		
		return null;
	}
	
	public void run(String execString)throws Exception {
		 // Get runtime
        java.lang.Runtime rt = java.lang.Runtime.getRuntime();
        // Start a new process
        java.lang.Process p = rt.exec(execString);
        // You can or maybe should wait for the process to complete
        p.waitFor();
        // Get process' output: its InputStream
        java.io.InputStream is = p.getInputStream();
        java.io.BufferedReader reader = new java.io.BufferedReader(new InputStreamReader(is));
        // And print each line
        String s = null;
        while ((s = reader.readLine()) != null) {
            System.out.println(s);
        }
        is.close();
	}
	
	private File findIno(File start){
		if(start==null)
			return null;
		if(start.getName().endsWith(".ino")){
			return start;
		}else{
			File dir = start.getParentFile();
			if(dir!=null){
				for(File f : dir.listFiles()){
					if (findIno(f)!=null){
						return f;
					}
				}
			}
		}
		return null;
		
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getShellType() {
		return "Arduino";
	}

	@Override
	public boolean isSupportedFileExtenetion(String filename) {
		if(	filename.toLowerCase().endsWith(".c")||
			filename.toLowerCase().endsWith(".h")||
			filename.toLowerCase().endsWith(".hpp")||
			filename.toLowerCase().endsWith(".cpp")||
			filename.toLowerCase().endsWith(".ino")	
				)
			return true;
		return false;
	}

	@Override
	public boolean getIsTextFile() {
		return true;
	}

	public static String getDefaultPort() {
		return defaultPort;
	}

	public static void setDefaultPort(String defaultPort) {
		ArduinoLoader.defaultPort = defaultPort;
	}

	public static String getDefaultBoard() {
		return defaultBoard;
	}

	public static void setDefaultBoard(String defaultBoard) {
		ArduinoLoader.defaultBoard = defaultBoard;
	}

	public static String getARDUINOExec() {
		return ARDUINO;
	}

	public static void setARDUINOExec(String aRDUINO) {
		ARDUINO = aRDUINO;
	}

}
