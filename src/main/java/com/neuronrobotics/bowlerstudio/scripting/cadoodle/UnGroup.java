package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;

public class UnGroup extends CaDoodleOperation{
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
						CSG transformed=csg;
						if(new CaDoodleVitamin(getCaDoodleFile().getCsgDBinstance()).isVitamin(csg)) {
							CSG regenerate = csg.getRegenerate().regenerate(csg);
							transformed = regenerate.transformed(nrToCSG);
						}
						CSG readd= transformed.setRegenerate(csg.getRegenerate()).syncProperties(getCaDoodleFile().getCsgDBinstance(),csg).setName(csg.getName());
						
						readd.removeGroupMembership(name);
						back.remove(csg);
						back.add(readd);
					}
				}

			}
		}

		return back;
	}

	public List<String> getNamesAddedInThisOperation() {
		return names;
	}

	public UnGroup setNames(List<String> names) {
		this.names = names;
		return this;
	}

}
