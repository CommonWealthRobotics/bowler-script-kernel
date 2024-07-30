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
	@Expose (serialize = true, deserialize = true)
	public double offset=10;
	@Override
	public String getType() {
		return "Paste";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		int index=0;
		for (int i = 0; i < incoming.size(); i++) {
			CSG c = incoming.get(i);
			for(String s:names) {
				if(s.contentEquals(c.getName())) {
					index = copyPasteMoved(back, index, c);
					if(c.isGroupResult()) {
						String groupName = c.getName();
						CSG newGroupResult= back.get(back.size()-1);
						newGroupResult.removeIsGroupResult(groupName);
						newGroupResult.addIsGroupResult(newGroupResult.getName());
						for(int j=0;j<incoming.size();j++) {
							CSG jc=incoming.get(j);
							if(jc.isInGroup()) {
								if(jc.checkGroupMembership(groupName)) {
									// this pasted gropups member found
									index = copyPasteMoved(back, index, jc);
									CSG newCopyInGroup = back.get(back.size()-1);
									newCopyInGroup.removeGroupMembership(groupName);
									newCopyInGroup.addGroupMembership(newGroupResult.getName());
								}
							}
						}
					}
				}
			}
		}
		return back;
	}

	private int copyPasteMoved(ArrayList<CSG> back, int index, CSG c) {
		CSG newOne = c.clone().movex(offset);
		String name = getPaserID()+(index==0?"":"_"+index);
		index++;
		newOne.syncProperties(c).setName(name);
		back.add(newOne);
		return index;
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

	public Paste setOffset(double offset) {
		this.offset = offset;
		return this;
	}
}
