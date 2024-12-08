package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.HashSet;

import com.google.gson.annotations.Expose;

public abstract class AbstractAddFrom  implements ICaDoodleOpperation {
	@Expose (serialize = false, deserialize = false)
	protected HashSet<String> namesAdded = new HashSet<>();
	@Expose (serialize = false, deserialize = false)
	protected int nameIndex = 0;
	@Expose(serialize = true, deserialize = true)
	protected String name = null;
	public HashSet<String> getNamesAdded() {
		return namesAdded;
	}
	public String getName() {
		if (name == null) {
			setName(RandomStringFactory.generateRandomString());
		}
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
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
	
	public abstract File getFile()throws NoSuchFileException;
	@Override 
	public String toString() {
		return getType()+" with name "+getName();
	}
}
