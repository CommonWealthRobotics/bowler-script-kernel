package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Extrude;
import eu.mihosoft.vrl.v3d.Plane;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.ext.org.poly2tri.PolygonUtil;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;
import eu.mihosoft.vrl.v3d.parametrics.Parameter;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;
import eu.mihosoft.vrl.v3d.svg.SVGLoad;
import javafx.scene.paint.Color;

public class Sweep extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = null;
	private static ArrayList<String> options = new ArrayList<String>();
	private static ArrayList<Double> nopt = new ArrayList<Double>();
	@Expose(serialize = true, deserialize = true)
	private Boolean preventBoM = false;

	private LengthParameter z = null;
	private LengthParameter rad = null;
	private LengthParameter step = null;
	private LengthParameter angle = null;

	public Sweep set(File source) throws Exception {
		if (!source.getName().toLowerCase().endsWith(".svg"))
			throw new Exception("Sweep can only take files with the .svg extention");
		System.out.println("Saving Local Copy of "+source.getAbsolutePath());
		AddFromFile.toLocal(source, getName());
		try {
			getFile();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return this;
	}

	@Override
	public String getType() {
		// TODO Auto-generated method stub
		return "Sweep";
	}

	public CSG sweep(Polygon p, String name, Bounds b) {
		double sweepTot = angle(name).getMM();
		double d = sweepTot / 360;
		int steps = (int) (steps(name).getMM() * d);
		double angle = sweepTot / steps;
		Parameter zp = zoffset(name);
		double z = zp.getMM() * d / steps;
		double radius = radius(name).getMM();
		if (angle < 0)
			angle = -angle;
		Transform centerandAllignedPolygon = new Transform().movex(-b.getMinX()).movey(-b.getMinY());
		Transform increment = new Transform().rotY(-angle).movey(z);
		Transform radiusT = new Transform().movex(radius);
		Polygon transformedP = p.transformed(centerandAllignedPolygon);
		return Extrude.sweep(transformedP, increment, radiusT, steps).rotx(-90).setName(name);
	}

	private LengthParameter radius(String name) {
		String key = name + "_CaDoodle_Rad";
		if (rad == null)
			rad = new LengthParameter(key, 10.0, nopt);
		if(rad.getMM()<0)
			rad.setMM(0);
		return rad;
	}

	private LengthParameter zoffset(String name) {
		String key = name + "_CaDoodle_Z-per";
		if (z == null)
			z = new LengthParameter(key, 0.0, nopt);
		return z;
	}

	private LengthParameter steps(String name) {
		String key = name + "_CaDoodle_Step";
		if (step == null)
			step = new LengthParameter(key, 30.0, nopt);
		if(step.getMM()<3)
			step.setMM(3);
		return step;
	}

	private LengthParameter angle(String name) {
		String key = name + "_CaDoodle_Angle";
		if (angle == null)
			angle = new LengthParameter(key, 360.0, nopt);
		if (angle.getMM()<0.001)
			angle.setMM(0.001);
		return angle;
	}



	@Override
	public List<CSG> process(List<CSG> incoming) {
		nameIndex = 0;
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		if (getName() == null) {

		}
		try {
//			ArrayList<Object>args = new ArrayList<>();
//			args.addAll(Arrays.asList(getName() ));
			ArrayList<CSG> collect = new ArrayList<>();
			File file = getFile();
			if (!file.exists()) {
				throw new RuntimeException("Failed to find file");
			}

			ArrayList<Object> args = new ArrayList<>();
			args.addAll(Arrays.asList(name));
			HashMap<String, Object> configs = new HashMap<String, Object>();
			configs.put("name", name);
			configs.put("PreventBomAdd", preventBoM);
			args.add(configs);
			// List<CSG> flattenedCSGs = ScriptingEngine.flaten(file, CSG.class, args);
			SVGLoad s = new SVGLoad(file.toURI());
			HashMap<String, List<Polygon>> polygons = s.toPolygons();
			Object[] array = polygons.keySet().toArray();
			int j = 0;
			Bounds b = getBounds(polygons);
			for (int i = 0; i < array.length; i++) {
				String key = (String) array[i];
				for (Polygon P : polygons.get(key)) {

					String orderedName = getOrderedName();
					CSG processedCSG = processGiven(P, b, j++, orderedName);
					collect.add(processedCSG);
				}
			}

			back.addAll(collect);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return back;
	}

	public Bounds getBounds(HashMap<String, List<Polygon>> polygons) {
		Vector3d min = null;
		Vector3d max = null;
		// TickToc.tic("getSellectedBounds "+incoming.size());
		for (String s : polygons.keySet())
			for (Polygon csg : polygons.get(s)) {

				Bounds b = csg.getBounds();
				Vector3d min2 = b.getMin().clone();
				Vector3d max2 = b.getMax().clone();
				if (min == null)
					min = min2;
				if (max == null)
					max = max2;
				if (min2.x < min.x)
					min.x = min2.x;
				if (min2.y < min.y)
					min.y = min2.y;
				if (min2.z < min.z)
					min.z = min2.z;
				if (max.x < max2.x)
					max.x = max2.x;
				if (max.y < max2.y)
					max.y = max2.y;
				if (max.z < max2.z)
					max.z = max2.z;
				// TickToc.tic("Bounds for "+c.getName());
			}

		return new Bounds(min, max);
	}

	@Override
	public File getFile() throws NoSuchFileException {
		return AddFromFile.getFile(name);
	}

	private CSG processGiven(Polygon p, Bounds b, int j, String name) {
		Color c = p.getColor();
		if (c == null)
			c = Color.ROSYBROWN;
		boolean hole = p.isHole();
		CSG csg = sweep(p, name, b);

		Transform nrToCSG = TransformFactory.nrToCSG(getLocation());
		String pathname;
		try {
			pathname = getFile().getAbsolutePath();
		} catch (NoSuchFileException e) {
			throw new RuntimeException(e);
		}

		StringParameter parameter = new StringParameter(name + "_CaDoodle_File", pathname, options);
		Parameter steps = steps(name);
		Parameter angle = angle(name);
		Parameter z = zoffset(name);
		Parameter radius = radius(name);
		parameter.setStrValue(pathname);
		CSG processedCSG = csg.transformed(nrToCSG).syncProperties(csg).setParameter(parameter).setParameter(steps)
				.setParameter(angle).setParameter(z).setParameter(radius).setColor(c).setIsHole(hole)
				.setRegenerate(previous -> {
					try {
						File file = getFile();
						String fileLocation = file.getAbsolutePath();
						com.neuronrobotics.sdk.common.Log.error("Regenerating " + fileLocation);
						return processGiven(p, b, j, name);
					} catch (Exception e) {
						e.printStackTrace();
					}
					return previous;
				}).setName(name);
		MoveCenter.set(getName(), processedCSG, nrToCSG);
		return processedCSG;
	}

	public TransformNR getLocation() {
		if (location == null)
			location = new TransformNR();
		return location;
	}

	public Sweep setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

	public Boolean getPreventBoM() {
		return preventBoM;
	}

	public Sweep setPreventBoM(Boolean preventBoM) {
		this.preventBoM = preventBoM;
		return this;
	}

}
