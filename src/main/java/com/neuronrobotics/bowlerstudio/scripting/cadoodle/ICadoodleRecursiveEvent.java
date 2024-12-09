package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;

import eu.mihosoft.vrl.v3d.CSG;

public interface ICadoodleRecursiveEvent {
	public ArrayList<CSG> process(CSG incoming);
}
