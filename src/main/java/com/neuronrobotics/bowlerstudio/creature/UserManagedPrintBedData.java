package com.neuronrobotics.bowlerstudio.creature;

import java.util.HashMap;

import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

public class UserManagedPrintBedData {
	public HashMap<String,TransformNR> locations;
	public double bedX;
	public double bedY;
	public void init() {
		locations=new HashMap<String, TransformNR>();
		bedX=240;
		bedY=200;
	}

}
