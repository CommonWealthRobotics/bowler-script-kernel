package junit.bowler;

import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;

/**
 * A scripted tool that finishes its work and then enters an interactive console
 * must be stopped, not waited on. The console reads a stdin that never ends on
 * Windows, where there is no /dev/null to redirect, so the run would hang
 * forever after the tool printed its console banner.
 */
public class ConsoleBannerWatchdogTest {

	@Test
	public void aProcessThatEntersItsConsoleIsStopped() throws Exception {
		ArrayList<String> command = new ArrayList<>();
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			// ping is the portable "block without reading stdin" on Windows
			command.add("cmd.exe");
			command.add("/c");
			command.add("echo FreeCAD Console mode entered & ping -n 300 127.0.0.1 >nul");
		} else {
			command.add("sh");
			command.add("-c");
			command.add("echo 'FreeCAD Console mode entered'; sleep 300");
		}
		ExecutorService pool = Executors.newSingleThreadExecutor();
		try {
			Future<?> run = pool.submit(() -> {
				try {
					DownloadManager.legacySystemRun(null, new File("."), System.out, command);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				return null;
			});
			run.get(60, TimeUnit.SECONDS);
		} catch (TimeoutException hung) {
			fail("the run hung after the process printed its console banner");
		} finally {
			pool.shutdownNow();
		}
	}
}
