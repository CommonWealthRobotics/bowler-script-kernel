package com.neuronrobotics.bowlerstudio.scripting;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.*;

import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.DeviceManager;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

public class GroovyHelper implements IScriptingLanguage, IScriptingLanguageDebugger {

	private Object inline(String code, ArrayList<Object> args, CSGDatabaseInstance db2) throws Exception {
		CompilerConfiguration cc = new CompilerConfiguration();
		cc.addCompilationCustomizers(new ImportCustomizer().addStarImports(ScriptingEngine.getImports())

		);

		Binding binding = new Binding();

		binding.setVariable("args", args);
		binding.setVariable("csgdb", db2);
		GroovyShell shell = new GroovyShell(GroovyHelper.class.getClassLoader(), binding, cc);
		// com.neuronrobotics.sdk.common.Log.error(code + "\n\nStart\n\n");
		Script script;
		if(!code.contains("csgdb")) {
			code=code.replace("StringParameter(", "StringParameter(csgdb,");
			code=code.replace("LengthParameter(", "LengthParameter(csgdb,");
			code=code.replace("setParameter(", "setParameter(csgdb,");

			code=code.replace("import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase", "");
			code=code.replace("CSGDatabase", "csgdb");
		}
		
		script = shell.parse(code);

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
	public Object inlineScriptRun(CSGDatabaseInstance db, File code, ArrayList<Object> args) throws Exception {
		String jsonString = null;
		InputStream inPut = null;
		inPut = FileUtils.openInputStream(code);
		jsonString = IOUtils.toString(inPut);
		return inline(jsonString, args, db);
	}

	@Override
	public Object inlineScriptRun(CSGDatabaseInstance db, String code, ArrayList<Object> args) throws Exception {
		return inline(code, args, db);
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
