package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

public class FilletChamfer extends AbstractAddFrom {

	@Expose(serialize = true, deserialize = true)
	private TransformNR workplane = null;
	@Expose(serialize = true, deserialize = true)
	private Set<String> toFillet = null;

	@Override
	public String getType() {
		return "Fillet";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		nameIndex = 0;
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		try {
			for (CSG csg : incoming) {
				for (String name : toFillet) {
					if (name.contentEquals(csg.getName())) {

						back.addAll(makeFillet(csg, null));
					}
				}
			}

		} catch (Exception e) {
			Log.error(e);
			throw new RuntimeException(e);
		}

		if (back.size() == 0)
			throw new RuntimeException("AddFromScript must return at least one CSG! " + getName());
		return back;
	}

	public ArrayList<CSG> makeFillet(CSG csgin, String on) {
		Transform nrToCSG = TransformFactory.nrToCSG(getWorkplane());
		ArrayList<CSG> fillet;
		int numberOfSidesInt = Integer
				.parseInt(ConfigurationDatabase.get("CaDoodle", "DefaultNumberOfSides", "16").toString()) / 4;

		LengthParameter faceCount = new LengthParameter(getDb(), getName() + "_CaDoodle_Num Faces",
				(double) numberOfSidesInt, new ArrayList<>(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0,
						10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0, 18.0, 19.0, 20.0)));

		LengthParameter rad = new LengthParameter(getDb(), getName() + "_CaDoodle_Fillet Size", 2.0,
				new ArrayList<>(Arrays.asList(0.1, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)));
		StringParameter upPm = new StringParameter(getDb(), getName() + "_CaDoodle_Up/Down", "down",
				new ArrayList<>(Arrays.asList("up", "down")));
		boolean up = upPm.getStrValue().contentEquals("up");
		StringParameter outerPm = new StringParameter(getDb(), getName() + "_CaDoodle_Inner/Outer", "inner",
				new ArrayList<>(Arrays.asList("inner", "outer")));
		boolean outer = outerPm.getStrValue().contentEquals("outer");
		try {

			fillet = eu.mihosoft.vrl.v3d.Fillet.fillet(csgin.transformed(nrToCSG.inverse()).movez(0.001), rad.getMM(),
					outer, (int) faceCount.getMM());
		} catch (ColinearPointsException e) {
			e.printStackTrace();
			fillet = new ArrayList<CSG>();
		}

		for (int i = 0; i < fillet.size(); i++) {
			String orderedName = (on == null ? getOrderedName() : on);
			int myIndex = i;
			CSG mine = fillet.get(i);
			if (!up)
				mine = mine.mirrorz();
			CSG tmp = mine.transformed(nrToCSG).setRegenerate(previous -> {
				return makeFillet(csgin, orderedName).get(myIndex);
			}).setName(orderedName);
			tmp.setParameter(getDb(), rad);
			tmp.setParameter(getDb(), faceCount);
			tmp.setParameter(getDb(), upPm);
			tmp.setParameter(getDb(), outerPm);
			if (!outer)
				tmp.setIsHole(true);
			MoveCenter.set(getName(), tmp, nrToCSG);
			fillet.set(i, tmp);
		}
		return fillet;
	}

	public TransformNR getWorkplane() {
		if (workplane == null)
			workplane = new TransformNR();
		return workplane;
	}

	public FilletChamfer setWorkplane(TransformNR workplane) {
		this.workplane = workplane;
		return this;
	}

	@Override
	public File getFile() {
		return null;// no files for fillet
	}

	public Set<String> getToFillet() {
		return toFillet;
	}

	public FilletChamfer setToFillet(Set<String> toFillet) {
		this.toFillet = toFillet;
		return this;
	}
}
