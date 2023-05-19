package junit.bowler;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.junit.Test;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications.Classification;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.DetectedObjects.DetectedObject;
import ai.djl.modality.cv.output.Point;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.modality.cv.util.BufferedImageUtils;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ModelZoo;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;

public class PyTorchYoloTest {

	@Test
	public void test() throws ModelNotFoundException, MalformedModelException, IOException, TranslateException {

		String url = "https://github.com/CommonWealthRobotics/CommonWealthRobotics.github.io/blob/source/content/img/AndrewHarrington/kickstarterVideoImage-clean.jpeg?raw=true";
		BufferedImage img = BufferedImageUtils.fromUrl(url);

		Criteria<BufferedImage, DetectedObjects> criteria = Criteria.builder()
				.optApplication(Application.CV.OBJECT_DETECTION).setTypes(BufferedImage.class, DetectedObjects.class)
				.optFilter("backbone", "resnet50").optProgress(new ProgressBar()).build();

		ZooModel<BufferedImage, DetectedObjects> model = ModelZoo.loadModel(criteria);

		Predictor<BufferedImage, DetectedObjects> predictor = model.newPredictor();
		DetectedObjects detection = predictor.predict(img);
		List<DetectedObject> items = detection.items();
		for (int i = 0; i < items.size(); i++) {
			DetectedObject c = items.get(i);
			BoundingBox cGetBoundingBox = c.getBoundingBox();
			Point topLeft = cGetBoundingBox.getPoint();
			Rectangle rect = cGetBoundingBox.getBounds();
			System.out.println(c);
			System.out.println("Name: "+c.getClassName() +" probability "+c.getProbability()+" center x "+topLeft.getX()+" center y "+topLeft.getY()+" rect h"+rect.getHeight()+" rect w"+rect.getWidth() );
		}
		System.out.println(detection);
	}

}
