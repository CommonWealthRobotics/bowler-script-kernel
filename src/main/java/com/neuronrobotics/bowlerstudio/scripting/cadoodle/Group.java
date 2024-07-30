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
		ArrayList<CSG> replace = new ArrayList<CSG>();
		back.addAll(incoming);
		for(CSG csg: incoming) {
			for(String name:names) {
				if(name.contentEquals(csg.getName())) {
					replace.add(csg);
					CSG c=csg.clone().syncProperties(csg).setName(name);
					if(csg.isHole()) {
						holes.add(c);
					}else
						solids.add(c);
					c.addGroupMembership(getGroupID());
					back.add(c);
				}
			}
		}
		for(CSG c:replace) {
			back.remove(c);
		}
		CSG result =null;
		if(holes.size()>0&&solids.size()==0) {
			result = CSG.unionAll(holes);
			result.setIsHole(true);

		}else {
			CSG holecutter =null;
			if(holes.size()>0)
				holecutter=CSG.unionAll(holes);
			result = CSG.unionAll(solids);
			if(holecutter!=null)
				result=result.difference(holecutter);
			result.setIsHole(false);
		}
		result.addIsGroupResult(getGroupID());
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
