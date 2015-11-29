package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Properties;

import javafx.scene.control.Tab;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.DeviceManager;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;

public class JythonHelper implements IScriptingLanguage{
	PythonInterpreter interp;
	

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) {
		Properties props = new Properties();
		PythonInterpreter.initialize(System.getProperties(), props,
				new String[] { "" });
		if(interp==null){
			interp = new PythonInterpreter();
	
			interp.exec("import sys");
		}

		for (String pm : DeviceManager.listConnectedDevice(null)) {
			BowlerAbstractDevice bad = DeviceManager.getSpecificDevice(null, pm);
				// passing into the scipt
			try{
				interp.set(bad.getScriptingName(),
						Class.forName(bad.getClass().getName())
								.cast(bad));
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.err.println("Device " + bad.getScriptingName() + " is "
					+ bad);
		}
		interp.set("args", args);
		interp.exec(code);
		ArrayList<Object> results = new ArrayList<>();
		
		PyObject localVariables = interp.getLocals();
		
		try{
			results.add(interp.get("csg",CSG.class));
		}catch(Exception e){
			e.printStackTrace();
		}
		try{
			results.add(interp.get("tab",Tab.class));
		}catch(Exception e){
			e.printStackTrace();
		}
		try{
			results.add(interp.get("device",BowlerAbstractDevice.class));
		}catch(Exception e){
			e.printStackTrace();
		}

		Log.debug("Jython return = "+results);
		return results;
	}

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(code.toPath());
			String s = new String(bytes, "UTF-8");
			return inlineScriptRun(s, args);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return null;
	}
	
	

	@Override
	public ShellType getShellType() {
		return ShellType.JYTHON;
	}

	@Override
	public boolean isSupportedFileExtenetion(String filename) {
		if (filename.toString().toLowerCase().endsWith(".py")
				|| filename.toString().toLowerCase().endsWith(".jy")) {
			return true;
		}
		return false;
	}



}
