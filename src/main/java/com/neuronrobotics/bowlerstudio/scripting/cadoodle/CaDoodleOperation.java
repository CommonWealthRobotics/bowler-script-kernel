package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.HashMap;
import java.util.List;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;

import eu.mihosoft.vrl.v3d.CSG;

public abstract class CaDoodleOperation {
	private HashMap<String,MobileBaseBuilder> robots;
	private CaDoodleFile cf = null;
	public abstract String getType();
	public abstract List<CSG> process(List<CSG> incoming);
	public abstract List<String> getNamesAddedInThisOperation();
	public CaDoodleFile getCaDoodleFile() {
		return cf;
	}

	public void setCaDoodleFile(CaDoodleFile cf) {
		this.cf = cf;
	}
	/**
	 * @return the robots
	 */
	public HashMap<String,MobileBaseBuilder> getRobots() {
		return robots;
	}
	/**
	 * @param robots the robots to set
	 */
	public void setRobots(HashMap<String,MobileBaseBuilder> robots) {
		this.robots = robots;
	}
	
}
