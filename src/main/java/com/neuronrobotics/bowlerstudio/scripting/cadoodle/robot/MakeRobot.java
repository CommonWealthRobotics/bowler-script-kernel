package com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AbstractAddFrom;
import eu.mihosoft.vrl.v3d.CSG;

public class MakeRobot extends AbstractAddFrom{

	@Override
	public String getType() {
		return "MakeRobot";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		return incoming;
	}

	@Override
	public List<String> getNamesAddedInThisOperation() {
		return new ArrayList<String>();
	}

	@Override
	public File getFile() throws NoSuchFileException {
		// TODO Auto-generated method stub
		return null;
	}

}
