package junit.bowler;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.neuronrobotics.bowlerkernel.djl.ImagePredictorType;
import com.neuronrobotics.bowlerkernel.djl.PredictorFactory;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.DetectedObjects.DetectedObject;
import ai.djl.modality.cv.output.Landmark;
import ai.djl.modality.cv.output.Point;

public class PyTorchResNetTest {
	String imageUrl = "https://avatars.githubusercontent.com/u/1254726?v=4";
	String image2URL = "https://images.squarespace-cdn.com/content/v1/51f533d1e4b0de43ba620290/f2120e79-d8dc-4a34-aa8c-03dc5fe5f0df/LMheadshot.png?format=300w";
	String image3URL = "https://static1.squarespace.com/static/56ffcc3901dbae8fc9b21603/t/572f5e92cf80a12c4b5c66fe/1462722410298/?format=1500w";
	String notme = "https://images.squarespace-cdn.com/content/v1/51f533d1e4b0de43ba620290/1472046872933-F3PCYXEK429ZJHDZSWLC/image-asset.jpeg?format=300w";

	@Test
	public void testResNet() throws Exception {

	}

	@Test
	public void testretinaface() throws Exception {
		BufferedImage bimg = ImageIO.read(new URL(imageUrl));

		Predictor<Image, DetectedObjects> predictor = PredictorFactory
				.imageContentsFactory(ImagePredictorType.retinaface).newPredictor();

		for (int i = 0; i < 3; i++) {
			Image img = ImageFactory.getInstance().fromImage(bimg);

			DetectedObjects objects = predictor.predict(img);
			saveBoundingBoxImage(img, objects, "retinaface");
		}
	}

	@Test
	public void testUltraNet() throws Exception {
		BufferedImage bimg = ImageIO.read(new URL(imageUrl));

		Predictor<Image, DetectedObjects> predictor = PredictorFactory.imageContentsFactory(ImagePredictorType.ultranet)
				.newPredictor();

		for (int j = 0; j < 3; j++) {
			Image img = ImageFactory.getInstance().fromImage(bimg);

			DetectedObjects objects = predictor.predict(img);
			List<DetectedObject> items = objects.items();
			for (int i = 0; i < items.size(); i++) {
				DetectedObject c = items.get(i);
				BoundingBox cGetBoundingBox = c.getBoundingBox();
				cGetBoundingBox = c.getBoundingBox();
				Iterator<Point> path = cGetBoundingBox.getPath().iterator();
				List<Point> list = new ArrayList<>();
				// keypoints in the image
				path.forEachRemaining(list::add);
				
				Landmark lm;
				if (Landmark.class.isInstance(cGetBoundingBox)) {
					lm = (Landmark) cGetBoundingBox;
					
				}
			}
			saveBoundingBoxImage(img, objects, "ultranet");
		}
	}

	private static void saveBoundingBoxImage(Image img, DetectedObjects detection, String type) throws Exception {
		Path outputDir = Paths.get("build/output");
		Files.createDirectories(outputDir);

		img.drawBoundingBoxes(detection);

		Path imagePath = outputDir.resolve(type + ".png").toAbsolutePath();
		img.save(Files.newOutputStream(imagePath), "png");
		System.out.println("Face detection result image has been saved in: {} " + imagePath);
	}

	@Test
	public void testYolo() throws Exception {

		Predictor<Image, DetectedObjects> predictor = PredictorFactory.imageContentsFactory(ImagePredictorType.yolov5)
				.newPredictor();
		for (int i = 0; i < 3; i++) {
			Image input = ImageFactory.getInstance()
					.fromUrl("https://github.com/ultralytics/yolov5/raw/master/data/images/bus.jpg");

			DetectedObjects objects = predictor.predict(input);
			saveBoundingBoxImage(input, objects, "yolov5");
		}
	}

	@Test
	public void testFeatures() throws Exception {
		BufferedImage img = ImageIO.read(new URL(imageUrl));
		BufferedImage img2 = ImageIO.read(new URL(image2URL));
		BufferedImage img3 = ImageIO.read(new URL(image3URL));

		Predictor<Image, float[]> predictor = PredictorFactory.faceFeatureFactory();
		float[] back = predictor.predict(ImageFactory.getInstance().fromImage(img));
		float[] back2 = predictor.predict(ImageFactory.getInstance().fromImage(img2));
		float[] back3 = predictor.predict(ImageFactory.getInstance().fromImage(img3));
		float[] back4 = predictor.predict(ImageFactory.getInstance().fromImage(ImageIO.read(new URL(notme))));

		System.out.println(" Comprair 1 to 2 " + PredictorFactory.calculSimilarFaceFeature(back, back2));
		System.out.println(" Comprair 1 to 3 " + PredictorFactory.calculSimilarFaceFeature(back, back3));
		System.out.println(" Comprair 2 to 3 " + PredictorFactory.calculSimilarFaceFeature(back2, back3));
		System.out.println(" Comprair 1 to 4 " + PredictorFactory.calculSimilarFaceFeature(back, back4));

		System.out.println(Arrays.toString(back));

	}

}
