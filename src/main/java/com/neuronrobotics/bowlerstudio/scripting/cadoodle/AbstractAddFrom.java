package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.HashSet;

import com.google.gson.annotations.Expose;

public abstract class AbstractAddFrom {
	@Expose (serialize = false, deserialize = false)
	protected HashSet<String> namesAdded = new HashSet<>();
	@Expose (serialize = false, deserialize = false)
	protected int nameIndex = 0;
	
	public HashSet<String> getNamesAdded() {
		return namesAdded;
	}
	public abstract String getName();

	public abstract void setName(String name);
	
	public String getOrderedName() {
		if(getName()==null) {
			setName(RandomStringFactory.generateRandomString());
		}
		String result= getName();
		if(nameIndex!=0){
			result+= "_"+nameIndex;
		}
		nameIndex++;
		namesAdded.add(result);
		return result;
	}
}
