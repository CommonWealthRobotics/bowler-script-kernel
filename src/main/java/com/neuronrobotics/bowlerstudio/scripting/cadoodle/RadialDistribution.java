package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;

public class RadialDistribution extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	private TransformNR workplane = new TransformNR();

	// @Expose(serialize = true, deserialize = true)
	// public double offset = 10;
	private int index;
	private HashMap<String, String> cpMap = new HashMap<String, String>();

	@Override
	public String getType() {
		return "RadialDistribution";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		index = 1;
		cpMap.clear();
		LengthParameter objectCount = new LengthParameter(getDb(), getName() + "_CaDoodle_Num Object", (double) 3,
				new ArrayList<>(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0,
						15.0, 16.0, 17.0, 18.0, 19.0, 20.0, 100.0)));
		LengthParameter rad = new LengthParameter(getDb(), getName() + "_CaDoodle_Radius", 20.0, new ArrayList<>(Arrays
				.asList(0.001, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 20.0, 30.0, 40.0, 50.0, 500.0)));
		for (int i = 0; i < objectCount.getMM(); i++) {
			double angle = 360.0 / objectCount.getMM() * ((double) i);
			TransformNR tf = new TransformNR(rad.getMM(), 0, 0);
			TransformNR rot = new TransformNR(new RotationNR(0, angle, 0));
			TransformNR loc = rot.times(tf);
			Bounds b = getBounds(incoming, getCaDoodleFile().getBoundsCache());

			CaDoodleFile.applyToAllConstituantElements(false, names, back, new ICadoodleRecursiveEvent() {
				@Override
				public ArrayList<CSG> process(CSG ic, int depth) {
					ArrayList<CSG> copyPasteMoved = copyPasteMoved(ic, depth, loc, b);
					for (CSG c : copyPasteMoved) {
						c.setParameter(getDb(), rad);
						c.setParameter(getDb(), objectCount);
					}
					return copyPasteMoved;
				}
			}, 1);
		}
		for (String from : cpMap.keySet()) {
			CSG source;
			try {
				source = CaDoodleFile.getByName(back, from);
			} catch (NameMissingException e) {
				continue;
			}
			if (source.isGroupResult()) {
				ArrayList<String> c = constituants(back, from);
				if (c.size() < 1) {
					new RuntimeException("A radial distribution must have at least 1 constituants!").printStackTrace();;
					continue;
				}
				String newGroupName;
				try {
					newGroupName = CaDoodleFile.getByName(back, cpMap.get(from)).getName();
				} catch (NameMissingException e) {
					continue;
				}
				for (String s : c) {
					CSG dest;
					try {
						dest = CaDoodleFile.getByName(back, s);
					} catch (NameMissingException e) {
						continue;
					}
					dest.removeGroupMembership(from);
					dest.addGroupMembership(newGroupName);
				}
			}
		}
		return back;
	}

	private ArrayList<String> constituants(ArrayList<CSG> b, String name) {
		ArrayList<String> c = new ArrayList<String>();
		for (String ky : cpMap.keySet()) {
			CSG byName;
			try {
				byName = CaDoodleFile.getByName(b, ky);
			} catch (NameMissingException e) {
				continue;
			}
			String name2 = cpMap.get(ky);
			CSG byName2;
			try {
				byName2 = CaDoodleFile.getByName(b, name2);
			} catch (NameMissingException e) {
				continue;
			}
			for (CSG csg : Arrays.asList(byName, byName2)) {
				if (csg.checkGroupMembership(name)) {
					// only add objects that were created by this operation
					if (csg.getName().contains(getName()))
						c.add(csg.getName());
				}
			}
		}
		return c;
	}

	private ArrayList<CSG> copyPasteMoved(CSG c, int depth, TransformNR location, Bounds b2) {
		String prevName = c.getName();
		String name = getName() + (index == 0 ? "" : "_" + index);
		CSG clone = c.clone();
		clone.setRegenerate(c.getRegenerate()).setName(name);
		clone.getStorage().set("PreviousName", prevName);
		CSGDatabaseInstance db = getDb();
		clone.syncParameter(db, c);
		TransformNR center = new TransformNR(b2.getCenterX(), b2.getCenterY(), b2.getMinZ());
		Transform nrToCSG = TransformFactory.nrToCSG(center.times(getWorkplane()));
		Transform nrToCSG2 = TransformFactory.nrToCSG(location);
		CSG newOne = null;
		CaDoodleVitamin caDoodleVitamin = new CaDoodleVitamin(db);
		if (caDoodleVitamin.isVitamin(c)) {
			CSG regenerate = c.getRegenerate().regenerate(clone);
			newOne = regenerate.transformed(nrToCSG).transformed(nrToCSG2);
			newOne.setRegenerate(regenerate.getRegenerate()).setName(name);
			newOne.setID(regenerate);
			if (!caDoodleVitamin.isVitamin(newOne)) {
				throw new RuntimeException("Failed to create the vitamin");
			}
		} else {
			newOne = clone.transformed(nrToCSG.inverse()).transformed(nrToCSG2).transformed(nrToCSG);
			newOne.setRegenerate(c.getRegenerate());
		}
		newOne.syncProperties(getCaDoodleFile().getCsgDBinstance(), c).setName(name);
		MoveCenter.set(name, newOne, location);
		index++;
		addNameInThisOperation(name);
		for (String s : c.getParameters(db)) {
			newOne.removeParameter(db, s);
		}
		ArrayList<CSG> b = new ArrayList<>();
		b.add(c);
		b.add(newOne);
		// com.neuronrobotics.sdk.common.Log.debug("Copy "+c.getName()+" to
		// "+newOne.getName());
		cpMap.put(c.getName(), newOne.getName());
		return b;
	}

	public Bounds getBounds(List<CSG> incoming, HashMap<CSG, Bounds> inWorkplaneBounds) {

		if (names != null) {
			if (inWorkplaneBounds == null)
				inWorkplaneBounds = new HashMap<CSG, Bounds>();
			List<CSG> selectedCSG = getSelectedCSG(names, incoming);
			return Align.getBounds(selectedCSG, workplane, inWorkplaneBounds);
		} else {
			throw new RuntimeException("Align can not be initialized without bounds!");
		}

	}

	public List<CSG> getSelectedCSG(Iterable<String> sele, List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		for (String sel : sele) {
			CSG t = getSelectedCSG(sel, incoming);
			if (t != null) {
				back.add(t);
			}
		}
		return back;
	}

	private CSG getSelectedCSG(String string, List<CSG> incoming) {
		for (CSG c : incoming) {
			if (c.getName().contentEquals(string))
				return c;
		}
		return null;
	}


	public RadialDistribution setNames(List<String> names) {
		this.names = names;
		return this;
	}


	@Override
	public File getFile() throws NoSuchFileException {
		throw new NoSuchFileException(null);
	}

	public TransformNR getWorkplane() {
		return workplane;
	}

	public RadialDistribution setWorkplane(TransformNR workplane) {
		this.workplane = workplane;
		return this;
	}
}
