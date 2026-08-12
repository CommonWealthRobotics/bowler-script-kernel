package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

public class EclipseExternalEditorTest {

	@Test
	public void test() {
		try {
			DownloadManager.getConfigExecutable("eclipse", null);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

}
