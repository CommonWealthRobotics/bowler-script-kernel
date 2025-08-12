package com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot;

import java.util.ArrayList;
import java.util.List;

import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleOperation;

import eu.mihosoft.vrl.v3d.CSG;

public class ModifyLimb extends CaDoodleOperation{

	@Override
	public String getType() {
		return "ModifyLimb";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		
		return incoming;
	}

	@Override
	public List<String> getNamesAddedInThisOperation() {
		return new ArrayList<String>();
	}

}
