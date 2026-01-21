package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import com.google.gson.annotations.Expose;

public class CaDoodleParameter {
	@Expose(serialize = true, deserialize = true)
	private String key;
	@Expose(serialize = true, deserialize = true)
	private String value;

	public CaDoodleParameter(String key, String string) {
		this.key = key;
		// TODO Auto-generated constructor stub
		this.setValue(string);
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

}
