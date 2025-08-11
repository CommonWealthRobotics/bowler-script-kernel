package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;

import eu.mihosoft.vrl.v3d.CSG;

public abstract class CaDoodleOperation {
	private CaDoodleFile cf = null;
	public abstract String getType();
	public abstract List<CSG> process(List<CSG> incoming);
	public abstract List<String> getNamesAddedInThisOperation();
	public void pruneCleanup() {
		
	}
	
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
		return cf.getRobots();
	}
	
	public String getBuilder(List<String> selected, List<CSG> state) {
		if(selected==null)
			return null;
		for(CSG c: state) {
			for(String s:selected) {
				if(s.contentEquals(c.getName())) {
					Optional<String> mobileBaseName= c.getMobileBaseName();
					if(mobileBaseName.isPresent()) {
						MobileBaseBuilder b = getRobots().get(mobileBaseName.get());
						if(b!=null) {
							return mobileBaseName.get();
						}
					}
				}
			}
		}
		return null;
	}
	
}
