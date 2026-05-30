package com.neuronrobotics.bowlerstudio.vitamins;

import java.io.File;

public interface AskToFixInterface {
	// Ask the user if they want to fix the file
	public boolean tryToFix(File f, Throwable t);
}
