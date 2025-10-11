package com.neuronrobotics.bowlerstudio.scripting;

public interface GitLogProgressMonitor {
	public abstract void onLogUpdate(String update, Exception e);
}
