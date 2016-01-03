package com.neuronrobotics.bowlerstudio.scripting;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;


/**
 * Class containing static utility methods for Java to Clojure interop
 * 
 * @author Mike https://github.com/mikera/clojure-utils/blob/master/src/main/java/mikera/cljutils/Clojure.java
 *
 */
public class RobotHelper implements IScriptingLanguage{

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(code.toPath());
			String s = new String(bytes, "UTF-8");
			MobileBase mb;
			try {
				mb = new MobileBase(IOUtils.toInputStream(s, "UTF-8"));
				
				mb.setSelfSource(ScriptingEngine.findGistTagFromFile(code));
				return mb;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		//System.out.println("Clojure returned of type="+ret.getClass()+" value="+ret);
		return null;
	}
	
	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) {
		
		MobileBase mb=null;
		try {
			mb = new MobileBase(IOUtils.toInputStream(code, "UTF-8"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		return mb;
	}
	@Override
	public ShellType getShellType() {
		return ShellType.ROBOT;
	}

	@Override
	public boolean isSupportedFileExtenetion(String filename) {
		if (filename.toLowerCase().endsWith(".xml")) {
			return true;
		}		
		return false;
	}
	
}

