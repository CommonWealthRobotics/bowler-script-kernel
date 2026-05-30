package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.manifold3d.NonManifoldShapeError;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.CSG.OptType;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import eu.mihosoft.vrl.v3d.STL;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.thumbnail.ThumbnailImageCSG;
import javafx.scene.shape.CullFace;

public class BrokenSTLLoading {
	@Test
	public void test2() throws Throwable {
		CSG.setDefaultOptType(OptType.Manifold3d);
		String filename = "hourglass.stl";
		File file = new File(filename);

		CSG loaded = Vitamins.get(CSGDatabase.getInstance(), file);
		try {
			JavaFXInitializer.go();
			ThumbnailImageCSG.setCullFaceValue(CullFace.NONE);
			new ThumbnailImageCSG().writeImage(CSGDatabase.getInstance(), loaded,
					new File(file.getAbsolutePath() + ".png"));
		} catch (Exception e) {
			// Auto-generated catch block
			e.printStackTrace();
		}

	}


}
