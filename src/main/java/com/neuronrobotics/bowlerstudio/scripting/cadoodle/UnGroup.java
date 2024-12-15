package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;

public class UnGroup   implements ICaDoodleOpperation {
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
		
		for (CSG csg : incoming) {
			for (String name : names) {
				if (csg.isGroupResult())
					if (csg.getName().contentEquals(name)) {
						back.remove(csg);
					}
				if (csg.isInGroup()) {
					if (csg.checkGroupMembership(name)) {
						// release this object from the group
						Transform nrToCSG = MoveCenter.getTotalOffset(csg);
						CSG transformed=null;
						if(CaDoodleVitamin.isVitamin(csg))
							transformed= csg.regenerate().transformed(nrToCSG);
						else
							transformed=csg;
						CSG readd= transformed.setRegenerate(csg.getRegenerate()).syncProperties(csg).setName(csg.getName());
						
						readd.removeGroupMembership(name);
						back.remove(csg);
						back.add(readd);
					}
				}

			}
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
