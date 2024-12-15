package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;

public class Paste extends AbstractAddFrom implements ICaDoodleOpperation {
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = new TransformNR();
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
//	@Expose(serialize = true, deserialize = true)
//	public double offset = 10;
	private int index;
	private HashMap<String, String> cpMap = new HashMap<String, String>();

	@Override
	public String getType() {
		return "Paste";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		index = 1;
		cpMap.clear();
		for (int j = 0; j < names.size(); j++) {
			String s = names.get(j);
			CaDoodleFile.applyToAllConstituantElements(false, s, back, new ICadoodleRecursiveEvent() {
				@Override
				public ArrayList<CSG> process(CSG ic) {
					ArrayList<CSG> copyPasteMoved = copyPasteMoved(back, ic);
					return copyPasteMoved;
				}
			});
		}
		for(String from:cpMap.keySet()) {
			CSG source = getByName(back, from);
			if(source.isGroupResult()) {
				ArrayList<String> c =constituants(back,from);
				String newGroupName = getByName(back,cpMap.get(from)).getName();
				for(String s:c) {
					CSG dest = getByName(back,s);
					dest.removeGroupMembership(from);
					dest.addGroupMembership(newGroupName);
				}
			}
		}
		return back;
	}
	private ArrayList<String> constituants(ArrayList<CSG> back,String name){
		ArrayList<String> c = new ArrayList<String>();
		for(CSG csg:back) {
			if(csg.checkGroupMembership(name)) {
				// only add objects that were created by this operation
				if(csg.getName().contains(getName()))
					c.add(csg.getName());
			}
		}
		return c;
	}
	private CSG getByName(ArrayList<CSG> back, String name) {
		for(CSG c:back) {
			if (c.getName().contentEquals(name))
					return c;
		}
		throw new RuntimeException("Fail! there was no object named "+name);
	}
	


	private ArrayList<CSG> copyPasteMoved(ArrayList<CSG> back, CSG c) {
		String prevName = c.getName();
		String name = getName() + (index == 0 ? "" : "_" + index);
		CSG clone = c.clone();
		clone.setRegenerate(c.getRegenerate()).setName(name);
		clone.getStorage().set("PreviousName", prevName);
		Transform nrToCSG = MoveCenter.getTotalOffset(c);
		CSG newOne =null;
		if(CaDoodleVitamin.isVitamin(c))
			newOne=clone.regenerate().transformed(nrToCSG);
		else
			newOne=clone;
		newOne.setRegenerate(c.getRegenerate()).setName(name);
		index++;
		newOne.syncProperties(c).setName(name);
		getNamesAdded().add(name);
		ArrayList<CSG> b = new ArrayList<>();
		b.add(c);
		b.add(newOne);
		cpMap.put(c.getName(), newOne.getName());
		return b;
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


//	public Paste setOffset(double offset) {
//		this.offset = offset;
//		return this;
//	}

	@Override
	public File getFile() throws NoSuchFileException {
		throw new NoSuchFileException(null);
	}
}
