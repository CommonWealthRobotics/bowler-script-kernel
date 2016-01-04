package com.neuronrobotics.bowlerstudio.scripting;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.DeviceManager;

public class GroovyHelper implements IScriptingLanguage{


	private Object inline(Object code, ArrayList<Object> args) throws Exception {
		CompilerConfiguration cc = new CompilerConfiguration();
		cc.addCompilationCustomizers(new ImportCustomizer()
				.addStarImports(ScriptingEngine.getImports())
				.addStaticStars(
						"com.neuronrobotics.sdk.util.ThreadUtil",
						"eu.mihosoft.vrl.v3d.Transform"));

		Binding binding = new Binding();
		for (String pm : DeviceManager.listConnectedDevice(null)) {
			BowlerAbstractDevice bad = DeviceManager.getSpecificDevice(null, pm);
			try {
				// groovy needs the objects cas to thier actual type befor
				// passing into the scipt
				
				binding.setVariable(bad.getScriptingName(),
						Class.forName(bad.getClass().getName())
								.cast(bad));
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
//			System.err.println("Device " + bad.getScriptingName() + " is "
//					+ bad);
		}
		binding.setVariable("args", args);

		GroovyShell shell = new GroovyShell(GroovyHelper.class
				.getClassLoader(), binding, cc);
		//System.out.println(code + "\n\nStart\n\n");
		Script script;
		if(String.class.isInstance(code))
			script = shell.parse((String)code);
		else if(File.class.isInstance(code))
			script = shell.parse((File)code);
		else 
			return null;
		return script.run();

	}
	
	

	@Override
	public ShellType getShellType() {
		return ShellType.GROOVY;
	}

	@Override
	public boolean isSupportedFileExtenetion(String name) {
		if (name.toString().toLowerCase().endsWith(".java")
				|| name.toString().toLowerCase().endsWith(".groovy")) {
			return true;
		}
		return false;
	}



	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		return inline(code, args);
	}



	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		return inline(code, args);
	}

}
