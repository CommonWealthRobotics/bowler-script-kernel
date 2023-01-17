package com.neuronrobotics.bowlerkernel.Bezier3d;

import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import javafx.application.Platform;

public interface IInteractiveUIElementProvider {

	default void runLater(Runnable r) {
		Platform.runLater(r);
	}

	default TransformNR getCamerFrame() {
		return new TransformNR();
	}

	default double getCamerDepth() {
		return -1500;
	}

}
