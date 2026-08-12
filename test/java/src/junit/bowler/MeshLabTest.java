package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;


public class MeshLabTest {

	@Test
	public void test() {
		File exe = DownloadManager.getRunExecutable("meshlab", null);
		if (!exe.exists())
			fail("Failed to fine MeshLab Executable");
		System.out.println("Mesh lab found: " + exe.getAbsolutePath());
	}

}
