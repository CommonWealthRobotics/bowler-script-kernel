package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
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
		back.addAll(incoming);
		for (String name : names) {
			CaDoodleFile.applyToAllConstituantElements(false,name, back, new ICadoodleRecursiveEvent() {
				@Override
				public ArrayList<CSG> process(CSG incoming) {
					CSG tmpToAdd = incoming.transformed(TransformFactory.nrToCSG(location)).syncProperties(incoming)
							.setName(incoming.getName());
//					VitaminBomManager boM = CaDoodleFile.getBoM();
//					VitaminLocation loc = boM.getByName(name);
//					if (loc != null) {
//						loc.setLocation(loc.getLocation().times(location));
//						boM.save();
//					}
					ArrayList<CSG> b = new ArrayList<>();
					b.add(tmpToAdd);
					return b;
				}
			});
		}
		return back;
	}

//	private void moveByName(String name, ArrayList<CSG> back, HashSet<String> groupsProcessed) {
//		
//		for (int i = 0; i < back.size(); i++) {
//			CSG csg = back.get(i);
//			if(csg.isLock())
//				continue;
//			if (	csg.getName().contentEquals(name) ||
//					(csg.isInGroup() && csg.checkGroupMembership(name))){
//				groupsProcessed.add(name);
//				if(csg.isInGroup() && csg.isGroupResult() && !groupsProcessed.contains(csg.getName())) {
//					// composite group
//					moveByName(csg.getName(), back,groupsProcessed);
//					
//				}
//				// move it
//				CSG tmpToAdd = csg
//						.transformed(TransformFactory.nrToCSG(location))
//						.syncProperties(csg)
//						.setName(csg.getName());
//				VitaminBomManager boM = CaDoodleFile.getBoM();
//				VitaminLocation loc = boM.getByName(name);
//				if(loc!=null) {
//					loc.setLocation(loc.getLocation().times(location));
//					boM.save();
//				}
//				back.set(i, tmpToAdd);
//			}
//		}
//	}

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
