package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.List;

import eu.mihosoft.vrl.v3d.CSG;

public interface ICaDoodleStateUpdate {
	public void onUpdate(List<CSG>  currentState, ICaDoodleOpperation source,CaDoodleFile file );
	public void onSaveSuggestion();
}
