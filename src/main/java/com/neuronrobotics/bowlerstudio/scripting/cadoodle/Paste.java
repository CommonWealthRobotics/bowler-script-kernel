package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class Paste implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private TransformNR location=new TransformNR();
	@Expose (serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose (serialize = true, deserialize = true)
	public String paste=null;
	@Override
	public String getType() {
		return "Paste";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		int index=0;
		for(CSG c:incoming) {
			for(String s:names) {
				if(s.contentEquals(c.getName())) {
					CSG newOne = c.clone().movex(10);
					String name = getPaserID()+(index==0?"":"_"+index);
					newOne.setName(name);
					back.add(newOne);
				}
			}
		}
		return back;
	}

	public TransformNR getLocation() {
		return location;
	}

	public Paste setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

	public List<String> getNames() {
		return names;
	}

	public Paste setNames(List<String> names) {
		this.names = names;
		return this;
	}
	public String getPaserID() {
		if(paste==null)
			paste=AddFromScript.generateRandomString();
		return paste;
	}
}
