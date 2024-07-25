package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.util.ArrayList;

import com.google.gson.annotations.Expose;

public class CaDoodleFile {
	private ArrayList<ICaDoodleOpperation> opperations = new ArrayList<ICaDoodleOpperation>();
	private int currentIndex =0;
	private String projectName ="NoName";
	@Expose (serialize = false, deserialize = false)
	private File self;
	
	public void addOpperation(ICaDoodleOpperation op) {
		if(currentIndex != opperations.size()) {
			opperations=(ArrayList<ICaDoodleOpperation>) opperations.subList(0, currentIndex);
		}
		opperations.add(op);
		currentIndex++;
	}
	public void back() {
		if(currentIndex>0)
			currentIndex-=1;
	}
	public void forward() {
		if(currentIndex<opperations.size()-1)
			currentIndex+=1;
	}
	public File getSelf() {
		return self;
	}
	public void setSelf(File self) {
		this.self = self;
	}
			
}
