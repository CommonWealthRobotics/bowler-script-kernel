package junit.bowler;

import static org.junit.Assert.*;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.neuronrobotics.bowlerkernel.djl.FaceDetectionTranslator;
import com.neuronrobotics.bowlerkernel.djl.FaceFeatureTranslator;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.BoundingBox;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Rectangle;
import ai.djl.modality.cv.transform.Normalize;
import ai.djl.modality.cv.transform.Resize;
import ai.djl.modality.cv.transform.ToTensor;
import ai.djl.modality.cv.translator.YoloV5Translator;
import ai.djl.modality.cv.translator.YoloV5TranslatorFactory;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Pipeline;
import ai.djl.translate.TranslateException;
import ai.djl.translate.Translator;
import ai.djl.translate.TranslatorContext;

public class PyTorchResNetTest {
	String imageUrl = "https://avatars.githubusercontent.com/u/1254726?v=4";

	@Test
	public void testResNet() throws Exception {

	}

	@Test
	public void testUltraNet() throws Exception {
		BufferedImage bimg = ImageIO.read(new URL(imageUrl));

		Image img = ImageFactory.getInstance().fromImage(bimg);

		double confThresh = 0.85f;
		double nmsThresh = 0.45f;
		double[] variance = { 0.1f, 0.2f };
		int topK = 5000;
		int[][] scales = { { 10, 16, 24 }, { 32, 48 }, { 64, 96 }, { 128, 192, 256 } };
		int[] steps = { 8, 16, 32, 64 };

		FaceDetectionTranslator translator = new FaceDetectionTranslator(confThresh, nmsThresh, variance, topK, scales,
				steps);

		Criteria<Image, DetectedObjects> criteria = Criteria.builder().setTypes(Image.class, DetectedObjects.class)
				.optModelUrls("https://resources.djl.ai/test-models/pytorch/ultranet.zip").optTranslator(translator)
				.optProgress(new ProgressBar()).optEngine("PyTorch") // Use PyTorch engine
				.build();

		try (ZooModel<Image, DetectedObjects> model = criteria.loadModel()) {
			try (Predictor<Image, DetectedObjects> predictor = model.newPredictor()) {
				DetectedObjects detection = predictor.predict(img);
				saveBoundingBoxImage(img, detection,"ultranet");
				// return detection;
			}
		}
	}

	private static void saveBoundingBoxImage(Image img, DetectedObjects detection, String type) throws Exception {
		Path outputDir = Paths.get("build/output");
		Files.createDirectories(outputDir);

		img.drawBoundingBoxes(detection);

		Path imagePath = outputDir.resolve(type+".png").toAbsolutePath();
		img.save(Files.newOutputStream(imagePath), "png");
		System.out.println("Face detection result image has been saved in: {} " + imagePath);
	}

	@Test
	public void testYolo() throws Exception {

        String MODEL_URL = "https://mlrepo.djl.ai/model/cv/object_detection/ai/djl/onnxruntime/yolo5s/0.0.1/yolov5s.zip";

        Criteria<Image, DetectedObjects> criteria = Criteria.builder()
                .setTypes(Image.class, DetectedObjects.class)
                .optModelUrls(MODEL_URL)
                .optEngine("OnnxRuntime")
                .optTranslatorFactory(new YoloV5TranslatorFactory())
                .build();
        try(ZooModel<Image, DetectedObjects> model = criteria.loadModel()) {
            try (Predictor<Image, DetectedObjects> predictor = model.newPredictor()) {
                Image input = ImageFactory.getInstance().fromUrl("https://github.com/ultralytics/yolov5/raw/master/data/images/bus.jpg");
                DetectedObjects objects = predictor.predict(input);
                List<BoundingBox> boxes = new ArrayList<>();
                List<String> names = new ArrayList<>();
                List<Double> prob = new ArrayList<>();
                for (Classifications.Classification obj : objects.items()) {
                    DetectedObjects.DetectedObject objConvered = (DetectedObjects.DetectedObject) obj;
                    BoundingBox box = objConvered.getBoundingBox();
                    Rectangle rec = box.getBounds();
                    Rectangle rec2 = new Rectangle(
                        rec.getX() ,
                        rec.getY() ,
                        rec.getWidth(),
                        rec.getHeight()
                        );
                    boxes.add(rec2);
                    names.add(obj.getClassName());
                    prob.add(obj.getProbability());
                }
                DetectedObjects converted = new DetectedObjects(names, prob, boxes);
                saveBoundingBoxImage(input, converted,"yolov5");
            }
        }
	}

	@Test
	public void testFeatures() throws Exception {
		BufferedImage img = ImageIO.read(new URL(imageUrl));

		Criteria<Image, float[]> criteria = Criteria.builder().setTypes(Image.class, float[].class)
				.optModelUrls("https://resources.djl.ai/test-models/pytorch/face_feature.zip")
				.optModelName("face_feature") // specify model file prefix
				.optTranslator(new FaceFeatureTranslator()).optProgress(new ProgressBar()).optEngine("PyTorch") // Use
																												// PyTorch
																												// engine
				.build();

		try (ZooModel<Image, float[]> model = criteria.loadModel()) {
			Predictor<Image, float[]> predictor = model.newPredictor();
			float[] back = predictor.predict(ImageFactory.getInstance().fromImage(img));
			System.out.println(Arrays.toString(back));

		}
	}

	public static float calculSimilar(float[] feature1, float[] feature2) {
		float ret = 0.0f;
		float mod1 = 0.0f;
		float mod2 = 0.0f;
		int length = feature1.length;
		for (int i = 0; i < length; ++i) {
			ret += feature1[i] * feature2[i];
			mod1 += feature1[i] * feature1[i];
			mod2 += feature2[i] * feature2[i];
		}
		return (float) ((ret / Math.sqrt(mod1) / Math.sqrt(mod2) + 1) / 2.0f);
	}

}
