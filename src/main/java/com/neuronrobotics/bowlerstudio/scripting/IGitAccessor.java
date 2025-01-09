package com.neuronrobotics.bowlerstudio.scripting;

import org.eclipse.jgit.api.Git;

public interface IGitAccessor {
	public void run(Git git) throws Exception;
}
