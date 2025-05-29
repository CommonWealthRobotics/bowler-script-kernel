package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.Parameter;

public class Paste extends AbstractAddFrom  {
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

		CaDoodleFile.applyToAllConstituantElements(false, names, back, new ICadoodleRecursiveEvent() {
			@Override
			public ArrayList<CSG> process(CSG ic, int depth) {
				ArrayList<CSG> copyPasteMoved = copyPasteMoved(back, ic,depth);
				return copyPasteMoved;
			}
		},1);

		for (String from : cpMap.keySet()) {
			CSG source =  CaDoodleFile.getByName(back, from);
			if (source.isGroupResult()) {
				ArrayList<String> c = constituants(back, from);
				if(c.size()<1)
					throw new RuntimeException("A group result must have at least 1 constituants!");
				String newGroupName =  CaDoodleFile.getByName(back, cpMap.get(from)).getName();
				for (String s : c) {
					CSG dest =  CaDoodleFile.getByName(back, s);
					dest.removeGroupMembership(from);
					dest.addGroupMembership(newGroupName);
				}
			}
		}
		return back;
	}

	private ArrayList<String> constituants(ArrayList<CSG> b, String name) {
		ArrayList<String> c = new ArrayList<String>();
		for (String ky:cpMap.keySet()) {
			CSG byName = CaDoodleFile.getByName(b,ky);
			String name2 = cpMap.get(ky);
			CSG byName2 =  CaDoodleFile.getByName(b,name2);
			for(CSG csg:Arrays.asList(byName,byName2)){
			if (csg.checkGroupMembership(name)) {
				// only add objects that were created by this operation
				if (csg.getName().contains(getName()))
					c.add(csg.getName());
			}
			}
		}
		return c;
	}



	private ArrayList<CSG> copyPasteMoved(ArrayList<CSG> back, CSG c, int depth) {
		String prevName = c.getName();
		String name = getName() +( index == 0 ? "" : "_" + index);
		CSG clone = c.clone();
		clone.setRegenerate(c.getRegenerate()).setName(name);
		clone.getStorage().set("PreviousName", prevName);
		Transform nrToCSG = MoveCenter.getTotalOffset(c);

		Transform nrToCSG2 = TransformFactory.nrToCSG(location);
		CSG newOne = null;
		if (CaDoodleVitamin.isVitamin(c)) {
			CSG regenerate = clone.getRegenerate().regenerate(clone);
			newOne = regenerate.transformed(nrToCSG).transformed(nrToCSG2);
			newOne.setRegenerate(regenerate.getRegenerate());
		}else {
			newOne = clone.transformed(nrToCSG2);
			newOne.setRegenerate(c.getRegenerate());
		}
		newOne.syncProperties(c).setName(name);
		MoveCenter.set(name, newOne, location);
		index++;
		getNamesAdded().add(name);
		ArrayList<CSG> b = new ArrayList<>();
		b.add(c);
		b.add(newOne);
		//System.out.println("Copy "+c.getName()+" to "+newOne.getName());
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
		ArrayList<String> n= new ArrayList<String>();
		n.addAll(getNamesAdded());
		n.addAll(names);
		return n;
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
