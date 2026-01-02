package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class ModelNotes extends CaDoodleOperation{
	@Expose (serialize = true, deserialize = true)
	TransformNR location=null;
	@Expose (serialize = true, deserialize = true)
	String text=null;
	
	@Override
	public String getType() {
		return "ModelNotes";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		// no change to the models when adding a note
		return incoming;
	}

	@Override
	public List<String> getNamesAddedInThisOperation() {
		return new ArrayList<String>();
	}

}
