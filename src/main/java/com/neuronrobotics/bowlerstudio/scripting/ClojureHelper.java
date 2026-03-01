package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

/**
 * Class containing static utility methods for Java to Clojure interop
 *
 * @author Mike
 *         https://github.com/mikera/clojure-utils/blob/master/src/main/java/mikera/cljutils/Clojure.java
 */
public class ClojureHelper implements IScriptingLanguage {

	public static Var REQUIRE = var("clojure.core", "require");
	public static Var META = var("clojure.core", "meta");
	public static Var EVAL = var("clojure.core", "eval");
	public static Var READ_STRING = var("clojure.core", "load-string");

	/**
	 * Require a namespace by name, loading it if necessary.
	 *
	 * Calls clojure.core/require
	 */
	public static Object require(String nsName) {
		return REQUIRE.invoke(Symbol.intern(nsName));
	}

	public static Object readString(String s) {
		return READ_STRING.invoke(s);
	}

	/**
	 * Looks up a var by name in the clojure.core namespace.
	 *
	 * The var can subsequently be invoked if it is a function.
	 */
	public static Var var(String varName) {
		return var("clojure.core", varName);
	}

	/**
	 * Looks up a var by name in the given namespace.
	 *
	 * The var can subsequently be invoked if it is a function.
	 */
	public static Var var(String nsName, String varName) {
		return RT.var(nsName, varName);
	}

	/**
	 * Evaluates a String, which should contain valid Clojure code.
	 */
	public static Object eval(String string) {
		return EVAL.invoke(readString(string));
	}

	@Override
	public Object inlineScriptRun(CSGDatabaseInstance db,File code, ArrayList<Object> args) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(code.toPath());
			String s = new String(bytes, "UTF-8");
			return inlineScriptRun( db,s, args);
		} catch (IOException e1) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e1);
		}
		// com.neuronrobotics.sdk.common.Log.error("Clojure returned of type="+ret.getClass()+" value="+ret);
		return null;
	}

	@Override
	public Object inlineScriptRun(CSGDatabaseInstance db,String code, ArrayList<Object> args) {

		return ClojureHelper.eval(code);
	}

	@Override
	public String getShellType() {
		return "Clojure";
	}

	@Override
	public boolean getIsTextFile() {
		// Auto-generated method stub
		return true;
	}

	/**
	 * Get the contents of an empty file
	 * 
	 * @return
	 */
	public String getDefaultContents() {
		return "(println \"hello world\")";
	}

	@Override
	public ArrayList<String> getFileExtension() {
		// Auto-generated method stub
		return new ArrayList<>(Arrays.asList("clj", "cljs", "cljc"));
	}

}
