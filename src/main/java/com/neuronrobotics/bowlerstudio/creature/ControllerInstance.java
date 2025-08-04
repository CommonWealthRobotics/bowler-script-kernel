package com.neuronrobotics.bowlerstudio.creature;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

public class ControllerInstance {
	@Expose(serialize = true, deserialize = true)
	ControllerOption type;
	@Expose(serialize = true, deserialize = true)
	String name;
	@Expose(serialize = true, deserialize = true)
	TransformNR pose;
	
}
