package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;

import javafx.scene.control.Tab;

import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;

public class JythonHelper implements IScriptingLanguage {

	PythonInterpreter interp;

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, String code,
			ArrayList<Object> args) {
		Properties props = new Properties();
		PythonInterpreter.initialize(System.getProperties(), props, new String[]{""});
		if (interp == null) {
			interp = new PythonInterpreter();

			interp.exec("import sys");
		}

		// for (String pm : DeviceManager.listConnectedDevice(null)) {
		// BowlerAbstractDevice bad = DeviceManager.getSpecificDevice(null, pm);
		// // passing into the scipt
		// try {
		// interp.set(bad.getScriptingName(),
		// Class.forName(bad.getClass().getName())
		// .cast(bad));
		// } catch (ClassNotFoundException e) {
		// // Auto-generated catch block
		// com.neuronrobotics.sdk.common.Log.error(e);
		// }
		// com.neuronrobotics.sdk.common.Log.error("Device " + bad.getScriptingName() +
		// " is "
		// + bad);
		// }
		interp.set("args", args);
		interp.exec(code);
		ArrayList<Object> results = new ArrayList<>();

		PyObject localVariables = interp.getLocals();

		try {
			results.add(interp.get("csg", CSG.class));
		} catch (Exception e) {
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		try {
			results.add(interp.get("tab", Tab.class));
		} catch (Exception e) {
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		try {
			results.add(interp.get("device", BowlerAbstractDevice.class));
		} catch (Exception e) {
			com.neuronrobotics.sdk.common.Log.error(e);
		}

		Log.debug("Jython return = " + results);
		return results;
	}

	@Override
	public Object inlineScriptRun(eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance db, File code,
			ArrayList<Object> args) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(code.toPath());
			String s = new String(bytes, "UTF-8");
			return inlineScriptRun(db, s, args);
		} catch (IOException e1) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e1);
		}
		return null;
	}

	@Override
	public String getShellType() {
		return "Jython";
	}
	/**
	 * Get the contents of an empty file
	 *
	 * @return
	 */
	public String getDefaultContents() {
		return "print( 'Hello World')";
	}
	@Override
	public boolean getIsTextFile() {
		// Auto-generated method stub
		return true;
	}

	@Override
	public ArrayList<String> getFileExtension() {
		// Auto-generated method stub
		return new ArrayList<>(Arrays.asList("jy"));
	}

}
