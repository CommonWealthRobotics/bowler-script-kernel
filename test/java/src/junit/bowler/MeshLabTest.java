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
		File exe = DownloadManager.getRunExecutable("meshlab", new IExternalEditor() {

			@Override
			public void onProcessExit(int ev) {
				// TODO Auto-generated method stub

			}

			@Override
			public String nameOfEditor() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public void launch(File file, Button advanced, Runnable onExit) {
				// TODO Auto-generated method stub

			}

			@Override
			public Class getSupportedLangauge() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public URL getInstallURL() throws MalformedURLException {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public Image getImage() {
				// TODO Auto-generated method stub
				return null;
			}
		});
		if (!exe.exists())
			fail("Failed to fine MeshLab Executable");
		System.out.println("Mesh lab found: " + exe.getAbsolutePath());
	}

}
