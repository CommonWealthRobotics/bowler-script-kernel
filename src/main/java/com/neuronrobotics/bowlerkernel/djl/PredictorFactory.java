package com.neuronrobotics.bowlerkernel.djl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import ai.djl.MalformedModelException;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.translator.YoloV5TranslatorFactory;
import ai.djl.pytorch.jni.JniUtils;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;

public class PredictorFactory {
	static {
		Engine.getEngine("PyTorch"); // Make sure PyTorch engine is loaded
	}
	private static HashMap<ImagePredictorType, Predictor<Image, DetectedObjects>> preloaded = new HashMap<>();
	private static Predictor<Image, float[]> features = null;

	public static Predictor<Image, DetectedObjects> imageContentsFactory(ImagePredictorType type)
			throws ModelNotFoundException, MalformedModelException, IOException {
		JniUtils.setGraphExecutorOptimize(false);

		if (preloaded.get(type) == null) {

			switch (type) {
			case retinaface:
				double confThreshretinaface = 0.85f;
				double nmsThreshretinaface = 0.45f;
				double[] varianceretinaface = { 0.1f, 0.2f };
				int topKretinaface = 5000;
				int[][] scalesretinaface = { { 16, 32 }, { 64, 128 }, { 256, 512 } };
				int[] stepsretinaface = { 8, 16, 32 };
				FaceDetectionTranslator translatorretinaface = new FaceDetectionTranslator(confThreshretinaface,
						nmsThreshretinaface, varianceretinaface, topKretinaface, scalesretinaface, stepsretinaface);

				Criteria<Image, DetectedObjects> criteriaretinaface = Criteria.builder()
						.setTypes(Image.class, DetectedObjects.class)
						.optModelUrls("https://resources.djl.ai/test-models/pytorch/retinaface.zip")
						// Load model from local file, e.g:
						.optModelName("retinaface") // specify model file prefix
						.optTranslator(translatorretinaface).optProgress(new ProgressBar()).optEngine("PyTorch") // Use
																													// PyTorch
																													// engine
						.build();

				preloaded.put(type, criteriaretinaface.loadModel().newPredictor());
				break;
			case ultranet:
				double confThresh = 0.85f;
				double nmsThresh = 0.45f;
				double[] variance = { 0.1f, 0.2f };
				int topK = 5000;
				int[][] scales = { { 10, 16, 24 }, { 32, 48 }, { 64, 96 }, { 128, 192, 256 } };
				int[] steps = { 8, 16, 32, 64 };
				FaceDetectionTranslator translator = new FaceDetectionTranslator(confThresh, nmsThresh, variance, topK,
						scales, steps);

				Criteria<Image, DetectedObjects> criteria = Criteria.builder()
						.setTypes(Image.class, DetectedObjects.class)
						.optModelUrls("https://resources.djl.ai/test-models/pytorch/ultranet.zip")
						.optTranslator(translator).optProgress(new ProgressBar()).optEngine("PyTorch") // Use PyTorch
																										// engine
						.build();

				preloaded.put(type, criteria.loadModel().newPredictor());
				break;
			case yolov5:
				String MODEL_URL = "https://mlrepo.djl.ai/model/cv/object_detection/ai/djl/onnxruntime/yolo5s/0.0.1/yolov5s.zip";

				Criteria<Image, DetectedObjects> criteria2 = Criteria.builder()
						.setTypes(Image.class, DetectedObjects.class).optModelUrls(MODEL_URL).optEngine("OnnxRuntime")
						.optTranslatorFactory(new YoloV5TranslatorFactory()).build();
				preloaded.put(type, criteria2.loadModel().newPredictor());
				break;
			default:
				throw new RuntimeException("No Model availible of type " + type);

			}
		}
		return preloaded.get(type);
	}

	public static Predictor<Image, float[]> faceFeatureFactory()
			throws ModelNotFoundException, MalformedModelException, IOException {
		JniUtils.setGraphExecutorOptimize(false);
		if (features == null) {
			Criteria<Image, float[]> criteria = Criteria.builder().setTypes(Image.class, float[].class)
					.optModelUrls("https://resources.djl.ai/test-models/pytorch/face_feature.zip")
					.optModelName("face_feature") // specify model file prefix
					.optTranslator(new FaceFeatureTranslator()).optProgress(new ProgressBar()).optEngine("PyTorch") // Use
																													// PyTorch
																													// engine
					.build();
			ZooModel<Image, float[]> model = criteria.loadModel();
			features = model.newPredictor();
		}
		return features;
	}
	public static float calculSimilarFaceFeature(float[] feature1, ArrayList<float[]> people) {
		float ret = 0.0f;
		float mod1 = 0.0f;
		float mod2 = 0.0f;
		int length = feature1.length;
		for(int j=0;j<people.size();j++) {
			float[] feature2 = people.get(j);
			for (int i = 0; i < length; ++i) {
				ret += feature1[i] * feature2[i];
				mod1 += feature1[i] * feature1[i];
				mod2 += feature2[i] * feature2[i];
			}
		}
		return (float) ((ret / Math.sqrt(mod1) / Math.sqrt(mod2) + 1) / 2.0f);
	}
	public static float calculSimilarFaceFeature(float[] feature1, float[] feature2) {
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
