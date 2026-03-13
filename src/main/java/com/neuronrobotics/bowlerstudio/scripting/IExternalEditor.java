package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

import javafx.scene.control.Button;
import javafx.scene.image.Image;

public interface IExternalEditor {

	Class getSupportedLangauge();

	default boolean isSupportedByExtension(File file) {
		if (getSupportedLangauge() != null)
			if (getSupportedLangauge().isInstance(ScriptingEngine.getLangaugeByExtension(file.getAbsolutePath()))) {
				return true;
			}
		return false;
	}

	void launch(File file, Button advanced, Runnable onExit);

	String nameOfEditor();

	URL getInstallURL() throws MalformedURLException;

	void onProcessExit(int ev);

	Image getImage();

}
