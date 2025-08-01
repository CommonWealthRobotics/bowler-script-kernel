package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

public abstract class CaDoodleOperation implements ICaDoodleOpperation{
	private CaDoodleFile cf = null;

	public CaDoodleFile getCaDoodleFile() {
		return cf;
	}

	public void setCaDoodleFile(CaDoodleFile cf) {
		this.cf = cf;
	}
	
}
