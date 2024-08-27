package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.IParametric;
import javafx.scene.paint.Color;

public class Group implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose (serialize = true, deserialize = true)
	public String groupID=null;
	@Expose (serialize = true, deserialize = true)
	public boolean hull=false;
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
			if(hull)
				result=result.hull();
			result.setIsHole(true);

		}else {
			CSG holecutter =null;
			if(holes.size()>0) {
				holecutter=CSG.unionAll(holes);
				if(hull)
					holecutter=holecutter.hull();
			}
			result = CSG.unionAll(solids);
			Color c = result.getColor();
			if(hull) {
				result=result.hull();
			}
			if(holecutter!=null)
				result=result.difference(holecutter);
			result.setIsHole(false);
			result.setColor(c);
		}
		HashMap<String, IParametric> mapOfparametrics = result.getMapOfparametrics();
		if(mapOfparametrics!=null)
			mapOfparametrics.clear();
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
			groupID=RandomStringFactory.generateRandomString();
		return groupID;
	}

	public Group setHull(boolean hull) {
		this.hull = hull;
		return this;
	}

}
