package com.neuronrobotics.bowlerstudio.creature;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

public class MobileBaseBuilder {

	private String gitURL;
	private String name;
	private String xmlName =null;

	public MobileBaseBuilder(String gitURL, String name) {
		this.gitURL = gitURL;
		this.name = name;
		
	}
	
	public MobileBase build() throws Exception {
		MobileBase base = new MobileBase();
		base.setScriptingName(name);
		String filename = name+".xml";
		if(xmlName!=null) {
			filename=xmlName;
		}
		base.setGitSelfSource(new String[] {gitURL,filename});
		
	
		ScriptingEngine.pushCodeToGit(gitURL, null, filename, base.getXml(), "Builder Write XML ", true);
		return base;
	}

	public MobileBaseBuilder setXmlName(String xmlName) {
		this.xmlName = xmlName;
		return this;
	}
}
