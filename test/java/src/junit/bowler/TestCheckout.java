package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Ref;
import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.common.Log;

import javafx.application.Platform;
import javafx.scene.control.MenuItem;

public class TestCheckout {
	  @Before
	  public void setup() throws InvalidRemoteException, TransportException, IOException, GitAPIException, Exception {
		  BowlerKernel.startupProcedures();
	  }
	@Test
	public void test() throws IOException, GitAPIException, InterruptedException {
		String url = "https://github.com/OperationSmallKat/greycat.git";
		Collection<Ref> branches = ScriptingEngine.getAllBranches(url);
		for(Ref select:branches) {
			try {
				String []name = select.getName().split("/");
				String myName = name[name.length-1];
				com.neuronrobotics.sdk.common.Log.debug("Selecting Branch\r\n"+url+" \t\t"+myName);
				String was = ScriptingEngine.getBranch(url);
				ScriptingEngine.checkout(url, myName);
				String s = ScriptingEngine.getBranch(url);
				assertTrue("Changing from "+was+" to "+myName+" got "+s,myName.contains(s));
				
			} catch (Exception e) {
				Log.error(e);
				Thread.sleep(1000);
				fail();
			}
		}
	}

}
