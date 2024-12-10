package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class Paste extends AbstractAddFrom implements ICaDoodleOpperation {
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = new TransformNR();
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	public String paste = null;
	@Expose(serialize = true, deserialize = true)
	public double offset = 10;
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

		return back;
	}

	private ArrayList<CSG> copyPasteMoved(ArrayList<CSG> back, CSG c) {
		String prevName = c.getName();
		String name = getPaserID() + (index == 0 ? "" : "_" + index);
		CSG clone = c.clone();
		clone.setRegenerate(c.getRegenerate()).setName(name);
		clone.getStorage().set("PreviousName", prevName);
		CSG newOne = clone.regenerate().moveToCenter().movex(c.getCenterX()).movey(c.getCenterY()).movez(c.getCenterZ())
				.movex(offset);
		newOne.setRegenerate(c.getRegenerate()).setName(name);
		VitaminBomManager boM = CaDoodleFile.getBoM();
		String name2 = c.getName();
		VitaminLocation loc = boM.getByName(name2);
		VitaminLocation locNew = boM.getByName(name);
		if (loc != null) {
			VitaminLocation newElement = new VitaminLocation(loc, name);
			newElement.setLocation(newElement.getLocation().times(new TransformNR(offset, 0, 0)));
			boM.addVitamin(newElement);
			boM.save();
		}
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

	public String getPaserID() {
		if (paste == null)
			paste = RandomStringFactory.generateRandomString();
		return paste;
	}

	public Paste setOffset(double offset) {
		this.offset = offset;
		return this;
	}

	@Override
	public File getFile() throws NoSuchFileException {
		throw new NoSuchFileException(null);
	}
}
