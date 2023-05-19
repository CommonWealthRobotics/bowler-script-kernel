package junit.bowler;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.io.IOException;

import org.junit.Test;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.output.DetectedObjects;
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

		String url = "https://github.com/awslabs/djl/raw/master/examples/src/test/resources/dog_bike_car.jpg";
		BufferedImage img = BufferedImageUtils.fromUrl(url);

		Criteria<BufferedImage, DetectedObjects> criteria = Criteria.builder()
				.optApplication(Application.CV.OBJECT_DETECTION).setTypes(BufferedImage.class, DetectedObjects.class)
				.optFilter("backbone", "resnet50").optProgress(new ProgressBar()).build();

		ZooModel<BufferedImage, DetectedObjects> model = ModelZoo.loadModel(criteria);

		Predictor<BufferedImage, DetectedObjects> predictor = model.newPredictor();
		DetectedObjects detection = predictor.predict(img);
		System.out.println(detection);
	}

}
