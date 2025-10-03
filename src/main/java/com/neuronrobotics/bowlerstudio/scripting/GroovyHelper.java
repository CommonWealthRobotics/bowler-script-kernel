package com.neuronrobotics.bowlerstudio.scripting;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.*;

import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.DeviceManager;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

public class GroovyHelper implements IScriptingLanguage, IScriptingLanguageDebugger {

	private Object inline(Object code, ArrayList<Object> args) throws Exception {
		CompilerConfiguration cc = new CompilerConfiguration();
		cc.addCompilationCustomizers(new ImportCustomizer().addStarImports(ScriptingEngine.getImports())

		);

		Binding binding = new Binding();
//    for (String pm : DeviceManager.listConnectedDevice()) {
//      BowlerAbstractDevice bad =  DeviceManager.getSpecificDevice(null, pm);
//      try {
//        // groovy needs the objects cas to thier actual type befor
//        // passing into the scipt
//
//        binding.setVariable(bad.getScriptingName(),
//            Class.forName(bad.getClass().getName())
//                .cast(bad));
//      } catch (Throwable e) {
//        //throw e;
//      }
////			com.neuronrobotics.sdk.common.Log.error("Device " + bad.getScriptingName() + " is "
////					+ bad);
//    }

		binding.setVariable("args", args);
		File code2 = null;
		if (File.class.isInstance(code)) {
			 code2 = (File)code;
			if (!code2.getName().toLowerCase().contentEquals("csgdatabase.json")) {
				File p = code2.getParentFile();
				for (String s : p.list()) {
					if (s.toLowerCase().contentEquals("csgdatabase.json")) {
						CSGDatabaseInstance db =new CSGDatabaseInstance(new File(p.getAbsoluteFile()+DownloadManager.delim()+s));
						binding.setVariable("csgdb", db);
					}
				}
			}
		}
		GroovyShell shell = new GroovyShell(GroovyHelper.class.getClassLoader(), binding, cc);
		// com.neuronrobotics.sdk.common.Log.error(code + "\n\nStart\n\n");
		Script script;
		
		if(code2==null) {
			script = shell.parse((String) code);
		} else  {
			script = shell.parse(code2);
		}
		return script.run();

	}

	@Override
	public String getShellType() {
		return "Groovy";
	}

	/**
	 * Get the contents of an empty file
	 * 
	 * @return
	 */
	public String getDefaultContents() {
		return "// code here";
	}

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		return inline(code, args);
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		return inline(code, args);
	}

	@Override
	public boolean getIsTextFile() {
		// Auto-generated method stub
		return true;
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		// Auto-generated method stub
		return new ArrayList<>(Arrays.asList("groovy", "java"));
	}

	@Override
	public IDebugScriptRunner compileDebug(File f) {
		// Auto-generated method stub
		return new IDebugScriptRunner() {

			@Override
			public String[] step() {
				// Auto-generated method stub
				return new String[] { "fileame.groovy", "345" };
			}
		};
	}

}
