package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.List;

import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public interface ICaDoodleStateUpdate {
	public void onUpdate(List<CSG>  currentState, CaDoodleOperation source,CaDoodleFile file );
	public void onSaveSuggestion();
	public void onInitializationDone();
	public void onInitializationStart();
	public void onRegenerateDone();
	public void onRegenerateStart(CaDoodleOperation source);
	public void onWorkplaneChange(TransformNR newWP);
	public void onTimelineUpdate(int numberOfNew);
}
