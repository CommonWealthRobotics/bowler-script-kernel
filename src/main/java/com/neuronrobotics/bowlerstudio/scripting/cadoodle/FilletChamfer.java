package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;

public class FilletChamfer extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private boolean up = true;
	@Expose(serialize = true, deserialize = true)
	private double radius = 2;
	@Expose(serialize = true, deserialize = true)
	private boolean outer = true;
	@Expose(serialize = true, deserialize = true)
	private int faces = 12;
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
						String orderedName = getOrderedName();

						CSG fillet = makeFillet(csg, orderedName);
						back.add(fillet);
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

	public CSG makeFillet(CSG csgin, String orderedName) {
		Transform nrToCSG = TransformFactory.nrToCSG(getWorkplane());
		CSG fillet;
		LengthParameter rad = new LengthParameter(getDb(), orderedName + "_CaDoodle_Fillet Size", radius,
				new ArrayList<>(Arrays.asList(0.1, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)));

		try {

			fillet = eu.mihosoft.vrl.v3d.Fillet.fillet(csgin.transformed(nrToCSG.inverse()).movez(0.001), rad.getMM(),
					outer, faces);
		} catch (ColinearPointsException e) {
			e.printStackTrace();
			fillet = new CSG();
		}
		if (fillet.getNumberOfTriangles() == 0)
			return fillet;
		if (!up)
			fillet = fillet.mirrorz();
		CSG tmp = fillet.transformed(nrToCSG).setRegenerate(previous -> {
			return makeFillet(previous, orderedName);
		}).setName(orderedName);
		tmp.setParameter(getDb(), rad);
		MoveCenter.set(getName(), tmp, nrToCSG);
		return tmp;
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

	public int getFaces() {
		return faces;
	}

	public FilletChamfer setFaces(int faces) {
		this.faces = faces;
		return this;
	}

	public boolean isOuter() {
		return outer;
	}

	public FilletChamfer setOuter(boolean outer) {
		this.outer = outer;
		return this;

	}

	public double getRadius() {
		return radius;
	}

	public FilletChamfer setRadius(double radius) {
		this.radius = radius;
		return this;

	}

	public boolean isUp() {
		return up;
	}

	public FilletChamfer setUp(boolean up) {
		this.up = up;
		return this;

	}


}
