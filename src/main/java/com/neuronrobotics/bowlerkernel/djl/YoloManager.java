package com.neuronrobotics.bowlerkernel.djl;

import java.io.IOException;

import ai.djl.ModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;

public final class YoloManager implements AutoCloseable {
	private final ZooModel<Image, DetectedObjects> model;
	private final Predictor<Image, DetectedObjects> predictor;

	public YoloManager(Criteria<Image, DetectedObjects> criteria) throws IOException, ModelException {
		this.model = criteria.loadModel();
		this.predictor = model.newPredictor();
	}

	public Predictor<Image, DetectedObjects> predictor() {
		return predictor;
	}

	@Override
	public void close() throws Exception {
		predictor.close(); // FIRST
		model.close(); // SECOND
	}
}
