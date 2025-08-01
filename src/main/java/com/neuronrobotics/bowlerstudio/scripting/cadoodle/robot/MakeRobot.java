package com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleOperation;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.RandomStringFactory;

import eu.mihosoft.vrl.v3d.CSG;

public class MakeRobot extends CaDoodleOperation{
	@Expose(serialize = true, deserialize = true)
	protected String name = null;
	
	
	public String getName() {
		if (name == null) {
			setName(RandomStringFactory.generateRandomString());
		}
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	@Override
	public String getType() {
		return "MakeRobot";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		return incoming;
	}

	@Override
	public List<String> getNames() {
		return new ArrayList<String>();
	}

}
