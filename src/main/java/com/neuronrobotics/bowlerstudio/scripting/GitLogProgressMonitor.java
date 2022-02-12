package com.neuronrobotics.bowlerstudio.scripting;

public interface GitLogProgressMonitor {
	public abstract void onUpdate(String update, Exception e);
}
