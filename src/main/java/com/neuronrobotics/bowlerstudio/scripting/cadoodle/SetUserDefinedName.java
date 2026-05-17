package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;

public class SetUserDefinedName extends CaDoodleOperation {
	@Expose(serialize = true, deserialize = true)
	private String target = null;
	@Expose(serialize = true, deserialize = true)
	private String newUserDefinedName = null;

	@Override
	public String getType() {
		return "SetUserDefinedName";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		for (CSG c : incoming) {
			if (target.contentEquals(c.getName())) {
				c.setUserDefinedName(getNewUserDefinedName());
			}
		}
		return incoming;
	}

	public List<String> getNamesAddedInThisOperation() {
		return new ArrayList<String>();
	}

	public String getNewUserDefinedName() {
		return newUserDefinedName;
	}

	public SetUserDefinedName setNewUserDefinedName(String newUserDefinedName) {
		this.newUserDefinedName = newUserDefinedName;
		return this;
	}

}
