package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.Extrude;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Slice;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;

public class ExtrudeSurface extends AbstractAddFrom {

	@Expose(serialize = true, deserialize = true)
	private TransformNR workplane = null;
	@Expose(serialize = true, deserialize = true)
	private Set<String> toFillet = null;

	@Override
	public String getType() {
		return "ExtrudeSurface";
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
						back.addAll(makeExtrusion(csg, null));
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

	public ArrayList<CSG> makeExtrusion(CSG csgin, String on) {
		Transform nrToCSG = TransformFactory.nrToCSG(getWorkplane());
		ArrayList<CSG> fillet = new ArrayList<CSG>();
		try {

			CSG base = csgin.transformed(nrToCSG.inverse()).movez(0.001);
			List<Polygon> polys = Slice.slice(base);
			for (Polygon p : polys) {
				boolean hole = !Extrude.isCCW(p);
				if (hole)
					p = new Polygon(p.getVertices().reversed());
				CSG extrude = Extrude.extrude(new Vector3d(0, 0, 20), p);
				extrude.setIsHole(hole);
				fillet.add(extrude);
			}
		} catch (ColinearPointsException e) {
			e.printStackTrace();
			fillet = new ArrayList<CSG>();
		}

		for (int i = 0; i < fillet.size(); i++) {
			String orderedName = (on == null ? getOrderedName() : on);
			int myIndex = i;
			CSG mine = fillet.get(i);

			CSG tmp = mine.transformed(nrToCSG).setRegenerate(previous -> {
				return makeExtrusion(csgin, orderedName).get(myIndex);
			}).setName(orderedName).setUserDefinedName("extrude_" + (i + 1));
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

	public ExtrudeSurface setWorkplane(TransformNR workplane) {
		this.workplane = workplane;
		return this;
	}

	@Override
	public File getFile() {
		return null;// no files for fillet
	}

	public Set<String> getToExtrude() {
		return toFillet;
	}

	public ExtrudeSurface setToExtrude(Set<String> toFillet) {
		this.toFillet = toFillet;
		return this;
	}
}
