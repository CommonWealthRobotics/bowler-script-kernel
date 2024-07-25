package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.List;

import eu.mihosoft.vrl.v3d.CSG;

public interface ICaDoodleOpperation {
	
	public String getType();
	public List<CSG> process(List<CSG> incoming);
}
