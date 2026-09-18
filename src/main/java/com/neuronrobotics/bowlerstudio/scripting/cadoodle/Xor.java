package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;

public class Xor extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	public String operationID = null;

	@Override
	public File getFile() throws NoSuchFileException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getType() {
		return "Xor";
	}

	public String getOperationID() {
		if (operationID == null)
			operationID = RandomStringFactory.generateRandomString();
		return operationID;
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<>();

		//		Paste copy = new Paste().setNames(names);
		//		copy.process(incoming);
		//		ArrayList<String> n = new ArrayList<>(copy.getNamesAddedInThisOperation());
		//		Group groups = new Group().setNames(n);
		//		groups.setHull(false);
		//		groups.setIntersect(true);
		//		groups.process(incoming);
		//		List<CSG> results = ap.get().getCurrentState();
		//		String intersectName = groups.getGroupID();
		//		ArrayList<String> names = new ArrayList<>();
		//		names.add(intersectName);
		//		ToHole th = new ToHole().setNames(names);
		//		ap.addOp(th).join();
		//
		//		for (int i = 0; i < n.size(); i++) {
		//			String e = n.get(i);
		//			ap.get();
		//			CSG g = null;
		//
		//			try {
		//				g = CaDoodleFile.getByName(ap.get().getCurrentState(), e);
		//			} catch (NameMissingException e1) {
		//				continue;
		//			}
		//
		//			if ((g == null) || g.isInGroup())
		//				continue;
		//
		//			ArrayList<String> namesToDiff = new ArrayList<String>();
		//			namesToDiff.add(intersectName);
		//			namesToDiff.add(e);
		//			Group cutIntersect = new Group().setNames(namesToDiff);
		//			ap.addOp(cutIntersect).join();
		//
		//		}
		return back;
	}

	public List<String> getNames() {
		return names;
	}

	public Xor setNames(List<String> names) {
		this.names = names;
		return this;
	}

}
