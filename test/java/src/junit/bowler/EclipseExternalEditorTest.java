package junit.bowler;

import static org.junit.Assert.*;


import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;

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
