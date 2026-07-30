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

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ColinearPointsException;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.Extrude;
import eu.mihosoft.vrl.v3d.ITransformProvider;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Slice;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;
import eu.mihosoft.vrl.v3d.parametrics.Parameter;
import javafx.scene.paint.Color;

public class ExtrudeSurface extends AbstractAddFrom {

	@Expose(serialize = true, deserialize = true)
	private TransformNR workplane = null;
	@Expose(serialize = true, deserialize = true)
	private Set<String> toFillet = null;
	@Expose(serialize = true, deserialize = true)
	private boolean sweep = false;

	@Expose(serialize = true, deserialize = true)
	private double defz = 0;
	@Expose(serialize = true, deserialize = true)
	private double defrad = 10;
	@Expose(serialize = true, deserialize = true)
	private double defstep = 64;
	@Expose(serialize = true, deserialize = true)
	private double defangle = 90;
	@Expose(serialize = true, deserialize = true)
	private double defSpiral = 0;

	private LengthParameter z = null;
	private LengthParameter rad = null;
	private LengthParameter step = null;
	private LengthParameter angle = null;
	private LengthParameter spiral = null;
	private LengthParameter axis = null;
	private static ArrayList<Double> nopt = new ArrayList<Double>();

	public double getDefz() {
		return defz;
	}

	public void setDefz(double defz) {
		this.defz = defz;
	}

	public double getDefrad() {
		return defrad;
	}

	public void setDefrad(double defrad) {
		this.defrad = defrad;
	}

	public double getDefstep() {
		return defstep;
	}

	public void setDefstep(double defstep) {
		this.defstep = defstep;
	}

	public double getDefangle() {
		return defangle;
	}

	public void setDefangle(double defangle) {
		this.defangle = defangle;
	}

	public double getDefSpiral() {
		return defSpiral;
	}

	public void setDefSpiral(double defSpiral) {
		this.defSpiral = defSpiral;
	}

	public LengthParameter radius(String name) {
		String key = name + "_CaDoodle_Rad";
		if (rad == null)
			rad = new LengthParameter(getCaDoodleFile().getCsgDBinstance(), key, getDefrad(), nopt);
		return rad;
	}

	public LengthParameter zoffset(String name) {
		String key = name + "_CaDoodle_Z-per";
		if (z == null)
			z = new LengthParameter(getCaDoodleFile().getCsgDBinstance(), key, getDefz(), nopt);
		return z;
	}

	public LengthParameter steps(String name) {
		String key = name + "_CaDoodle_Step";
		if (step == null)
			step = new LengthParameter(getCaDoodleFile().getCsgDBinstance(), key, getDefstep(), nopt);
		if (step.getMM() < 3)
			step.setMM(3);
		return step;
	}

	public LengthParameter angle(String name) {
		String key = name + "_CaDoodle_Angle";
		if (angle == null)
			angle = new LengthParameter(getCaDoodleFile().getCsgDBinstance(), key, getDefangle(), nopt);
		if (angle.getMM() < 0.001)
			angle.setMM(0.001);
		return angle;
	}

	public LengthParameter spiralStep(String name) {
		String key = name + "_CaDoodle_Spiral";
		if (spiral == null)
			spiral = new LengthParameter(getCaDoodleFile().getCsgDBinstance(), key, getDefSpiral(), nopt);
		if (spiral.getMM() < 0)
			spiral.setMM(0);
		return spiral;
	}

	public LengthParameter axis(String name) {
		String key = name + "_CaDoodle_Orentation";
		if (axis == null)
			axis = new LengthParameter(getCaDoodleFile().getCsgDBinstance(), key, 0.0,
					new ArrayList<Double>(Arrays.asList(-90.0, 0.0, 90.0, 180.0)));

		return axis;
	}

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

	public CSG sweep(Polygon p, String name, Bounds b) {
		double sweepTot = angle(name).getMM();
		double d = sweepTot / 360;
		int steps = (int) (steps(name).getMM() * d);
		if (steps == 0)
			steps = 1;
		double angle = sweepTot / steps;
		Parameter zp = zoffset(name);
		double z = zp.getMM() * d / steps;
		double radius = radius(name).getMM();
		boolean flip = false;
		if (radius < 0) {
			radius = -radius;
			flip = true;
		}
		if (angle < 0)
			angle = -angle;
		double sprl = spiralStep(name).getMM();
		Transform centerandAlignedPolygon = new Transform().movex(-b.getMinX()).movey(-b.getMinY());
		Transform increment = new Transform().rotY(-angle * (flip ? -1.0 : 1.0)).movey(z);
		Transform radiusT = new Transform().movex(radius);
		Polygon transformedP;
		try {
			transformedP = p.transformed(centerandAlignedPolygon);

			ITransformProvider pr = (unit, domain) -> {
				return new Transform().movex(sprl * unit * d);
			};
			return Extrude.sweep(transformedP, increment, radiusT, steps, pr).setName(name)
					.transformed(centerandAlignedPolygon.inverse()).movex(-radius);
		} catch (ColinearPointsException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		return new Cube(10).toCSG().setColor(Color.PINK);
	}

	public ArrayList<CSG> makeExtrusion(CSG csgin, String on) {
		Transform nrToCSG = TransformFactory.nrToCSG(getWorkplane());
		ArrayList<CSG> fillet = new ArrayList<CSG>();
		double howFarToMove = 0.001;

		try {

			CSG base = csgin.transformed(nrToCSG.inverse()).movez(howFarToMove);
			CSG offset = csgin.transformed(nrToCSG.inverse()).movez(-howFarToMove);
			List<Polygon> polys = Slice.slice(base);
			List<Polygon> offsetpolys = Slice.slice(offset, howFarToMove);
			ArrayList<CSG> cutters = new ArrayList<CSG>();
			for (Polygon p : offsetpolys) {
				boolean hole = !Extrude.isCCW(p);
				if (hole)
					p = new Polygon(p.getVertices().reversed());
				CSG extrude = Extrude.extrude(new Vector3d(0, 0, 21), p).movez(-0.5);
				extrude.setIsHole(true);
				extrude.setColor(base.getColor());
				cutters.add(extrude);
			}
			ArrayList<Polygon> sweeps = new ArrayList<Polygon>();
			for (Polygon p : polys) {
				boolean hole = !Extrude.isCCW(p);
				if (hole)
					p = new Polygon(p.getVertices().reversed());
				CSG extrude = Extrude.extrude(new Vector3d(0, 0, 20), p);
				extrude.setIsHole(hole);
				extrude.setColor(base.getColor());
				// fillet.add(extrude);
				for (CSG cutter : cutters) {
					extrude = extrude.difference(cutter);
				}
				if (extrude.getVertCount() > 0)
					if (sweep) {
						extrude = extrude.rotz(axis(name).getMM());
						List<Polygon> slice = Slice.slice(extrude);
						for (Polygon P : slice)
							P.setHole(hole);
						sweeps.addAll(slice);
					} else {
						fillet.add(extrude);
					}
			}
			if (sweep && sweeps.size() > 0) {
				Bounds b = Sweep.getBounds(sweeps);
				for (Polygon ps : sweeps) {
					CSG csg = sweep(ps, name, b).mirrorz();
					csg = csg.rotz(-axis(name).getMM());
					csg.setIsHole(ps.isHole());
					csg.setColor(base.getColor());
					fillet.add(csg);
				}
			}
			// fillet.addAll(cutters);
		} catch (ColinearPointsException e) {
			e.printStackTrace();
			fillet = new ArrayList<CSG>();
		}

		for (int i = 0; i < fillet.size(); i++) {
			String orderedName = (on == null ? getOrderedName() : on);
			int myIndex = i;
			CSG mine = fillet.get(i);

			CSG tmp = mine.movez(-howFarToMove).transformed(nrToCSG).setRegenerate(previous -> {
				return makeExtrusion(csgin, orderedName).get(myIndex);
			}).setName(orderedName);
			if (sweep) {
				Parameter steps = steps(name);
				Parameter angle = angle(name);
				Parameter z = zoffset(name);
				Parameter radius = radius(name);
				tmp.setParameter(getCaDoodleFile().getCsgDBinstance(), steps)
						.setParameter(getCaDoodleFile().getCsgDBinstance(), angle)
						.setParameter(getCaDoodleFile().getCsgDBinstance(), z)
						.setParameter(getCaDoodleFile().getCsgDBinstance(), radius)
						.setParameter(getCaDoodleFile().getCsgDBinstance(), spiralStep(name))
						.setParameter(getCaDoodleFile().getCsgDBinstance(), axis(name));
				tmp.setUserDefinedName("bend_" + (i + 1));
			} else {
				tmp.setUserDefinedName("extrude_" + (i + 1));
			}
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

	public ExtrudeSurface setSweep(boolean sweep) {
		this.sweep = sweep;
		return this;
	}
}
