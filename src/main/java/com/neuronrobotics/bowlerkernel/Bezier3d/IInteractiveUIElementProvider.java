package com.neuronrobotics.bowlerkernel.Bezier3d;

import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import javafx.application.Platform;

@SuppressWarnings("restriction")
public interface IInteractiveUIElementProvider {

	default void runLater(Runnable r) {
		new Thread(()->{
			Platform.runLater(() -> {
				try {
					r.run();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			});
		}).start();
	}

	default TransformNR getCamerFrame() {
		return new TransformNR();
	}

	default double getCamerDepth() {
		return -1500;
	}

}
