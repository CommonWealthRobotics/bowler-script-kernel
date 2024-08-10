package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class MoveCenter implements ICaDoodleOpperation {
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = new TransformNR();
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();

	@Override
	public String getType() {
		return "Move Center";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		HashSet<String> groupsProcessed = new HashSet<String>();
		back.addAll(incoming);
		for (String name : names) {
			moveByName(name,back,groupsProcessed);
		}
		return back;
	}

	private void moveByName(String name, ArrayList<CSG> back, HashSet<String> groupsProcessed) {
		
		for (int i = 0; i < back.size(); i++) {
			CSG csg = back.get(i);
			if (	csg.getName().contentEquals(name) ||
					(csg.isInGroup() && csg.checkGroupMembership(name))){
				groupsProcessed.add(name);
				if(csg.isInGroup() && csg.isGroupResult() && !groupsProcessed.contains(csg.getName())) {
					// composite group
					moveByName(csg.getName(), back,groupsProcessed);
					
				}
				// move it
				CSG tmpToAdd = csg
						.transformed(TransformFactory.nrToCSG(location))
						.syncProperties(csg)
						.setName(csg.getName());
				back.set(i, tmpToAdd);
			}
		}
	}

	public TransformNR getLocation() {
		return location;
	}

	public MoveCenter setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

	public List<String> getNames() {
		return names;
	}

	public MoveCenter setNames(List<String> names) {
		this.names = names;
		return this;
	}

}
