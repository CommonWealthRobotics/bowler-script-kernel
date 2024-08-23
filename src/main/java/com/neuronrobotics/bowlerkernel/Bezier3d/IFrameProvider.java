package com.neuronrobotics.bowlerkernel.Bezier3d;

import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

public interface IFrameProvider {
	public TransformNR get();
}
