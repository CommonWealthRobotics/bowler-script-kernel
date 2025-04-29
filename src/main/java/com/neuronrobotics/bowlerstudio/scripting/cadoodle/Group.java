package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.IParametric;
import javafx.scene.paint.Color;

public class Group extends AbstractAddFrom implements ICaDoodleOpperation {
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	public String groupID = null;
	@Expose(serialize = true, deserialize = true)
	public boolean hull = false;
	@Expose(serialize = true, deserialize = true)
	public boolean intersect = false;

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
		for (CSG csg : incoming) {
			if (csg.isLock())
				continue;
			for (String name : names) {
				if (name.contentEquals(csg.getName())) {
					replace.add(csg);
					CSG c = csg.clone().syncProperties(csg).setRegenerate(csg.getRegenerate()).setName(name);
					if (csg.isHole()) {
						holes.add(c);
					} else
						solids.add(c);
					c.addGroupMembership(getGroupID());
					back.add(c);
				}
			}
		}
		for (CSG c : replace) {
			back.remove(c);
		}
		CSG result = null;
		if (holes.size() > 0 && solids.size() == 0) {
			result = CSG.unionAll(holes);
			if (hull)
				result = result.hull();
			result.setIsHole(true);

		} else {
			CSG holecutter = null;
			if (holes.size() > 0) {
				if (intersect)
					holecutter = intersect(holes);
				else
					holecutter = holes.size()==1?holes.get(0):CSG.unionAll(holes);
				if (hull)
					holecutter = holecutter.hull();
			}
			if (intersect)
				result = intersect(solids);
			else
				result =solids.size()==1?solids.get(0).clone(): CSG.unionAll(solids);
			Color c = result.getColor();
			if (hull) {
				result = result.hull();
			}
			if (holecutter != null) {
				if(result.getBounds().isBoundsTouching(holecutter.getBounds())) {
					result = result.difference(holecutter);
				}
			}
			
			result.setIsHole(false);
			result.setColor(c);
		}
		HashMap<String, IParametric> mapOfparametrics = result.getMapOfparametrics();
		if (mapOfparametrics != null)
			mapOfparametrics.clear();
		result.addIsGroupResult(getGroupID());
		result.setName(getGroupID());
		namesAdded.add(result.getName());
		back.add(result);
		return back;
	}

	private CSG intersect(ArrayList<CSG> solids) {
		CSG first = solids.get(0);
		for(int i=1;i<solids.size();i++) {
			first=first.intersect(solids.get(i));
		}
		return first;
	}

	public List<String> getNames() {
		return names;
	}

	public Group setNames(List<String> names) {
		this.names = names;
		return this;
	}

	public String getGroupID() {
		if (groupID == null)
			groupID = RandomStringFactory.generateRandomString();
		return groupID;
	}

	public Group setIntersect(boolean intersect) {
		this.intersect = intersect;
		return this;
	}

	public Group setHull(boolean hull) {
		this.hull = hull;
		return this;
	}

	@Override
	public File getFile() throws NoSuchFileException {
		throw new NoSuchFileException(null);
	}
}
