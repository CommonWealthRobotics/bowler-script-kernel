package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;

public interface IScriptingLanguageDebugger {
	/**
	 * This interface defines a scripting langauge that supports debugging. 
	 * A file can be compiled to a DebugScriptRunner object that manages
	 * runtime.
	 * @param f the file to be debugged
	 * @return the debugger instance
	 */
	IDebugScriptRunner compileDebug(File f);
}
