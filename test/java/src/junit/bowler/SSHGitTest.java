package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

public class SSHGitTest {

	@Test
	public void test() throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		/*
		//"https://github.com/CommonWealthRobotics/DHParametersCadDisplay.git", "dhcad.groovy"
		File confFile = ScriptingEngine.fileFromGit("https://github.com/CommonWealthRobotics/DHParametersCadDisplay.git", "dhcad.groovy");
		
		assertTrue(ScriptingEngine.checkOwner(confFile));
		*/
	}

}
