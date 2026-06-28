package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.util.List;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.scene.image.WritableImage;

public interface ImagePorviderInterface {

	public WritableImage get(CSGDatabaseInstance instance, List<CSG> incomingToDisplay, File destination)
			throws NoImageException, IOException;
}
