package com.neuronrobotics.bowlerstudio.scripting;

public interface IDebugScriptRunner {
	
	/**
	 * Run one step of the debugger
	 * @return the file URI and line number 
	 */
	public String [] step();
	
	

}
