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

import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseLoader;
import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.DeviceManager;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

public class GroovyHelper implements IScriptingLanguage, IScriptingLanguageDebugger {

	private Object inline(String codeIn, ArrayList<Object> args, CSGDatabaseInstance db2) throws Exception {
		CompilerConfiguration cc = new CompilerConfiguration();
		cc.addCompilationCustomizers(new ImportCustomizer().addStarImports(ScriptingEngine.getImports())

		);

		Binding binding = new Binding();

		binding.setVariable("args", args);
		if(db2==null) {
			throw new RuntimeException("Can not send an empty CSG Database to script");
		}
		binding.setVariable("csgdb", db2);
		GroovyShell shell = new GroovyShell(GroovyHelper.class.getClassLoader(), binding, cc);
		String code=codeIn;
		if(!code.contains("csgdb")) {
			//getDefaultVitaminsDisplay(
			//			MobileBaseLoader.fromGit(
			code=code.replace("MobileBaseLoader.fromGit(", "MobileBaseLoader.fromGit(csgdb,");

			code=code.replace("MobileBaseCadManager.getDefaultVitaminsDisplay(", "MobileBaseCadManager.getDefaultVitaminsDisplay(csgdb,");

			code=code.replace("MobileBaseCadManager.getOriginVitaminsDisplay(", "MobileBaseCadManager.getOriginVitaminsDisplay(csgdb,");

			code=code.replace("MobileBaseCadManager.get(", "MobileBaseCadManager.get(csgdb,");
			code=code.replace("Vitamins.get(", "Vitamins.get(csgdb,");

			code=code.replace("CaDoodleVitamin.", "new CaDoodleVitamin(csgdb).");
			code=code.replace("StringParameter(", "StringParameter(csgdb,");
			code=code.replace("LengthParameter(", "LengthParameter(csgdb,");
			code=code.replace("setParameter(", "setParameter(csgdb,");

			code=code.replace("import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase", "MYTMPFINDREPLACETHINGY");
			code=code.replace("import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance", "MYTMPFINDREPLACETHINGY2");
			code=code.replace("CSGDatabase", "csgdb");
			code=code.replace( "MYTMPFINDREPLACETHINGY","import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase");
			code=code.replace( "MYTMPFINDREPLACETHINGY2","import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance");

			code=code.replace("inlineGistScriptRun(", "inlineGistScriptRun(csgdb,");
			code=code.replace("inlineFileScriptRun(", "inlineFileScriptRun(csgdb,");
			code=code.replace("inlineScriptRun(", "inlineScriptRun(csgdb,");
			code=code.replace("inlineScriptStringRun(", "inlineScriptStringRun(csgdb,");
			code=code.replace("gitScriptRun(", "gitScriptRun(((eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance)csgdb),");
		}
		
		Script script;
		try {
			script= shell.parse(code);
		}catch(Throwable t) {
			Log.error("Compilation error");
			Log.error(t);
			throw t;
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
