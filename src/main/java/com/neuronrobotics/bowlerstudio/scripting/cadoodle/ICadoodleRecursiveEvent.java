package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import eu.mihosoft.vrl.v3d.CSG;

public interface ICadoodleRecursiveEvent {
	public CSG process(CSG incoming);
}
