package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import eu.mihosoft.vrl.v3d.Transform;

public class MoveCenter extends CaDoodleOperation {
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = new TransformNR();
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	protected String name = null;

	public String getName() {
		if (name == null) {
			name = (RandomStringFactory.generateRandomString());
		}
		return name;
	}

	@Override
	public String getType() {
		return "Move Center";
	}

	public static void set(String name, CSG c, TransformNR tf) {
		set(name, c, TransformFactory.nrToCSG(tf));
	}

	public static void set(String name, CSG c, Transform tf) {
		if (tf == null)
			throw new NullPointerException();
		if (name == null)
			throw new NullPointerException();
		if (c == null)
			throw new NullPointerException();
		PropertyStorage storage = c.getStorage();
		Optional<Object> o = storage.getValue("TFSet");
		ArrayList<String> tfs = null;
		if (!o.isPresent()) {
			tfs = new ArrayList<String>();
			storage.set("TFSet", tfs);
		} else {
			tfs = (ArrayList<String>) o.get();
		}
		boolean contains = false;
		for (String s : tfs) {
			if (s.contentEquals(name)) {
				contains = true;
				break;
			}
		}
		storage.set(name, tf);
		if (!contains)
			tfs.add(name);
	}

	public static Transform getTotalOffset(CSG c) {
		Transform nrToCSG = new Transform();
		PropertyStorage storage = c.getStorage();
		Optional<Object> o = storage.getValue("TFSet");
		if (o.isPresent()) {
			Transform start = new Transform();
			ArrayList<String> tfs = (ArrayList<String>) o.get();
			for (String s : tfs) {
				try {
					Transform transTmp = new Transform().apply((Transform) storage.getValue(s).get());
					start = transTmp.apply(start);
				} catch (Exception ex) {
					com.neuronrobotics.sdk.common.Log.error(ex);
					;
				}
			}
			nrToCSG = start;
		}
		return nrToCSG;
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);

		CaDoodleFile.applyToAllConstituantElements(false, names, back, new ICadoodleRecursiveEvent() {
			@Override
			public ArrayList<CSG> process(CSG incoming, int depth) {

				Transform nrToCSG2 = TransformFactory.nrToCSG(location);
				if (incoming.isMotionLock()) {
					nrToCSG2 = new Transform();
				}
				CSG tmpToAdd = incoming.transformed(nrToCSG2)
						.syncProperties(getCaDoodleFile().getCsgDBinstance(), incoming).setName(incoming.getName())
						.setID(incoming);
				ArrayList<CSG> b = new ArrayList<>();
				b.add(tmpToAdd);
				set(getName(), tmpToAdd, location);
				return b;
			}
		}, 1);

		return back;
	}

	public boolean isWorkplaneNotOrigin(TransformNR w) {
		double epsilon = 0.00001;
		RotationNR r = w.getRotation();
		double abst = Math.abs(w.getX());
		double abs2t = Math.abs(w.getY());
		double abs3t = Math.abs(w.getZ());

		if ((abst > epsilon) || (abs2t > epsilon) || (abs3t > epsilon))
			return true;

		double abs = Math.abs(r.getRotationAzimuthDegrees());
		double abs2 = Math.abs(r.getRotationElevationDegrees());
		double abs3 = Math.abs(r.getRotationTiltDegrees());

		return ((abs > epsilon) || (abs2 > epsilon) || (abs3 > epsilon));
	}

	public TransformNR getLocation() {
		return location;
	}

	public MoveCenter setLocation(TransformNR location) throws InvalidLocationMove {
		if (isWorkplaneNotOrigin(location))
			this.location = location;
		else
			throw new InvalidLocationMove();
		return this;
	}

	public List<String> getNamesAddedInThisOperation() {
		return names;
	}

	public MoveCenter setNames(List<String> names, CaDoodleFile f) {

		for (String s : names) {
			boolean found = false;
			for (CSG c : f.getCurrentState()) {
				if (c.getName().contentEquals(s))
					found = true;
			}
			if (!found)
				throw new RuntimeException("Set a name that does not exist!!");
		}
		this.names = names;
		return this;
	}

}
