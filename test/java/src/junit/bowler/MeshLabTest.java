package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.IExternalEditor;

import javafx.scene.control.Button;
import javafx.scene.image.Image;

public class MeshLabTest {

	@Test
	public void test() {
		File exe = DownloadManager.getRunExecutable("meshlab", null);
		if (!exe.exists())
			fail("Failed to fine MeshLab Executable");
		System.out.println("Mesh lab found: " + exe.getAbsolutePath());
	}

}
