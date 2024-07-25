package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;

public class Hide implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Override
	public String getType() {
		return "Hide";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> replace = new ArrayList<CSG>();
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		for(CSG c: incoming) {
			for(String name:names) {
				if(name.contentEquals(c.getName())) {
					replace.add(c);
					CSG b=c.clone().syncProperties(c);
					b.setIsHide(true);
					back.add(b);
				}
			}
		}
		for(CSG c:replace) {
			back.remove(c);
		}
		return back;
	}

	public List<String> getNames() {
		return names;
	}

	public Hide setNames(List<String> names) {
		this.names = names;
		return this;
	}

}
