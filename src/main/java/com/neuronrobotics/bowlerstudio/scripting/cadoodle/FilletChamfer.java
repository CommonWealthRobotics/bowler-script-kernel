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
						CSG fillet = makeFillet(csg);
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

	public CSG makeFillet(CSG csgin) {
		Transform nrToCSG = TransformFactory.nrToCSG(getWorkplane());
		CSG fillet;
		try {
			fillet = eu.mihosoft.vrl.v3d.Fillet.fillet(csgin.transformed(nrToCSG.inverse()), radius, outer, faces);
		} catch (ColinearPointsException e) {
			e.printStackTrace();
			fillet = new CSG();
		}
		if (fillet.getNumberOfTriangles() == 0)
			return fillet;
		if (!up)
			fillet = fillet.mirrorz();
		String orderedName = getOrderedName();
		CSG tmp = fillet.transformed(nrToCSG).setRegenerate(previous -> {
			return makeFillet(previous);
		}).setName(orderedName);
		LengthParameter rad = new LengthParameter(getDb(), orderedName + "_CaDoodle_Fillet Size", 2.0,
				new ArrayList<>(Arrays.asList(0.1, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0)));
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

	public void setToFillet(Set<String> toFillet) {
		this.toFillet = toFillet;
	}

	public int getFaces() {
		return faces;
	}

	public void setFaces(int faces) {
		this.faces = faces;
	}

	public boolean isOuter() {
		return outer;
	}

	public void setOuter(boolean outer) {
		this.outer = outer;
	}

	public double getRadius() {
		return radius;
	}

	public void setRadius(double radius) {
		this.radius = radius;
	}

	public boolean isUp() {
		return up;
	}

	public void setUp(boolean up) {
		this.up = up;
	}


}
