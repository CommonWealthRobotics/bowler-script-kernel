package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;

public class WekaTester {
	  @Before
	  public void setup() throws InvalidRemoteException, TransportException, IOException, GitAPIException, Exception {
		  BowlerKernel.startupProcedures();
	  }
  @Test
  public void test() {
  
  }

}
