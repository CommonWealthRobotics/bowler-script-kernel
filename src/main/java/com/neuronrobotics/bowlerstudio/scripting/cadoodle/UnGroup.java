package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;

public class UnGroup implements ICaDoodleOpperation {
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();

	@Override
	public String getType() {
		return "Un-Group";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		ArrayList<CSG> results = new ArrayList<CSG>();
		for (CSG csg : back) {
			if (csg.isGroupResult()) {
				for (String name : names) {
					if (csg.getName().contentEquals(name)) {
						results.add(csg);
					}
					if (csg.isInGroup()) {
						if (csg.getGroupMembership().contentEquals(name)) {
							// release this object from the group
							csg.setGroupMembership(null);
						}
					}
				}
			}
		}
		for(CSG c:results) {
			// remove the group results from the list of parts
			back.remove(c);
		}
		return back;
	}

	public List<String> getNames() {
		return names;
	}

	public UnGroup setNames(List<String> names) {
		this.names = names;
		return this;
	}

}
