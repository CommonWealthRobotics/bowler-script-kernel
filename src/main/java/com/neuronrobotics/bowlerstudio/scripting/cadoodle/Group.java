package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;

public class Group implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose (serialize = true, deserialize = true)
	public String groupID=null;
	@Override
	public String getType() {
		return "Group";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> holes = new ArrayList<CSG>();
		ArrayList<CSG> solids = new ArrayList<CSG>();
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		for(CSG c: incoming) {
			for(String name:names) {
				if(name.contentEquals(c.getName())) {
					if(c.isHole()) {
						holes.add(c);
					}else
						solids.add(c);
					c.setGroupMembership(getGroupID());
					c.setIsGroupResult(false);
				}
			}
		}
		CSG result =null;
		if(holes.size()>0&&solids.size()==0) {
			result = CSG.unionAll(holes);
			result.setIsHole(true);

		}else {
			CSG holecutter = CSG.unionAll(holes);
			CSG solid = CSG.unionAll(solids);
			result=solid.difference(holecutter);
			result.setIsHole(false);
		}
		result.setIsGroupResult(true);
		result.setName(getGroupID());
		back.add(result);
		return back;
	}

	public List<String> getNames() {
		return names;
	}

	public Group setNames(List<String> names) {
		this.names = names;
		return this;
	}

	public String getGroupID() {
		if(groupID==null)
			groupID=AddFromScript.generateRandomString();
		return groupID;
	}

}
