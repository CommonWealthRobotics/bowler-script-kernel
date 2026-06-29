package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.application.Platform;
import javafx.scene.transform.Affine;

public class Align extends CaDoodleOperation {
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	public Alignment z = null;
	@Expose(serialize = true, deserialize = true)
	public Alignment y = null;
	@Expose(serialize = true, deserialize = true)
	public Alignment x = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR workplane = null;
	@Deprecated
	@Expose(serialize = true, deserialize = true)
	public StoragbeBounds bounds = null;
	@Expose(serialize = true, deserialize = true)
	private List<String> boundNames = null;

	@Expose(serialize = true, deserialize = true)
	protected String name = null;

	private HashMap<CSG, Bounds> cache = null;

	public String getName() {
		if (name == null) {
			setName(RandomStringFactory.generateRandomString());
		}
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getType() {
		return "Align";
	}

	@Override
	public String toString() {
		String string = getType() + " " + x + " " + y + " " + z;
		for (String n : getNamesAddedInThisOperation()) {
			string += " " + n;
		}
		return string;
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);

		Bounds bounds2;
		try {
			bounds2 = getBounds(incoming, cache);
			HashMap<String, TransformNR> moves = new HashMap<>();
			HashMap<String, CSG> objects = new HashMap<String, CSG>();
			for (String name : names) {
				for (CSG tmp : back) {
					if (!tmp.getName().contentEquals(name))
						continue;
					objects.put(name, tmp);
					CSG c = tmp.transformed(TransformFactory.nrToCSG(getWorkplane(tmp)).inverse());
					TransformNR tf = performTransform(bounds2, c);
					moves.put(c.getName(), tf);
				}
			}
			for (String name : moves.keySet()) {
				TransformNR nr = moves.get(name);
				TransformNR wp = getWorkplane(objects.get(name));
				TransformNR wpinv = wp.inverse();

				TransformNR times = wp.times(nr.times(wpinv));
				Transform tf = TransformFactory.nrToCSG(times);
				CaDoodleFile.applyToAllConstituantElements(false, name, back, (incoming1, depth) -> {
					ArrayList<CSG> b = new ArrayList<>();
					CSG c = incoming1.transformed(tf);
					sync(incoming1, c);
					MoveCenter.set(getName(), c, times);
					b.add(c);
					return b;
				}, 1, new HashSet<String>());
			}
		} catch (BoundsComputFailure e) {
			Log.error(e);
		}
		return back;
	}

	// private void collectToMove(ArrayList<CSG> toMove, ArrayList<CSG> back, String
	// name) {
	// ArrayList<CSG> toSearch = new ArrayList<CSG>();
	// toSearch.addAll(back);
	// for (int i = 0; i < toSearch.size(); i++) {
	// CSG c = toSearch.get(i);
	// if(name.contentEquals(c.getName())) {
	// toMove.add(c);
	// }
	// }
	// }

	private TransformNR performTransform(Bounds reference, CSG incoming) {
		// CSG c = incoming;
		double tx = 0, ty = 0, tz = 0;
		if (z != null) {
			switch (z) {
				case negative :
					tz = -incoming.getMinZ() + reference.getMinZ();
					break;
				case middle :
					tz = -incoming.getCenterZ() + reference.getCenterZ();
					break;
				case positive :
					tz = -incoming.getMaxZ() + reference.getMaxZ();
					break;
				default :
					break;
			}
		}
		if (x != null) {
			switch (x) {
				case negative :
					tx = -incoming.getMinX() + reference.getMinX();
					break;
				case middle :
					tx = -incoming.getCenterX() + reference.getCenterX();
					break;
				case positive :
					tx = -incoming.getMaxX() + reference.getMaxX();
					break;
				default :
					break;

			}
		}
		if (y != null) {
			switch (y) {
				case negative :
					ty = -incoming.getMinY() + reference.getMinY();
					break;
				case middle :
					ty = -incoming.getCenterY() + reference.getCenterY();
					break;
				case positive :
					ty = -incoming.getMaxY() + reference.getMaxY();
					break;
				default :
					break;

			}
		}
		return new TransformNR(tx, ty, tz);
	}

	private CSG sync(CSG incoming, CSG c) {
		return c.syncProperties(getCaDoodleFile().getCsgDBinstance(), incoming).setName(incoming.getName())
				.setColor(incoming.getColor()).setID(incoming);
	}

	public List<String> getNamesAddedInThisOperation() {
		return names;
	}

	public Align setNames(List<String> names) {
		this.names = names;
		return this;
	}

	public Align setAlignParams(Alignment X, Alignment Y, Alignment Z) {
		x = X;
		y = Y;
		z = Z;
		return this;
	}

	public TransformNR getWorkplane(CSG c) {
		if (workplane == null)
			workplane = new TransformNR();
		Affine af;
		TransformNR afNR = null;
		if (c.hasManipulator())
			try {
				af = c.getManipulator();
				afNR = TransformFactory.affineToNr(af).inverse();
			} catch (MissingManipulatorException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		else {
			afNR = new TransformNR();
		}

		return afNR.times(workplane);
	}

	public Align setWorkplane(TransformNR workplane) {
		this.workplane = workplane;
		return this;
	}

	public Bounds getBounds(List<CSG> incoming, HashMap<CSG, Bounds> inWorkplaneBounds) throws BoundsComputFailure {
		if (bounds != null) {
			Log.error("Depricated Bounds in the align step!");
			return bounds.getBounds();
		}
		if (boundNames != null) {
			if (inWorkplaneBounds == null)
				inWorkplaneBounds = new HashMap<CSG, Bounds>();
			List<CSG> selectedCSG = getSelectedCSG(boundNames, incoming);
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

	public Align setBounds(List<String> boundNames) {
		this.boundNames = boundNames;
		bounds = null;
		return this;
	}

	public static Bounds getBounds(List<CSG> incoming, TransformNR frame, HashMap<CSG, Bounds> cache)
			throws BoundsComputFailure {
		if (cache == null)
			cache = new HashMap<>();
		Vector3d min = null;
		Vector3d max = null;
		// TickToc.tic("getSellectedBounds "+incoming.size());

		for (CSG csg : incoming) {
			if (csg.isHide() || csg.isInGroup()) {
				//				Log.debug("Skipping bounds for " + csg.getName() + " hide:" + csg.isHide() + " in group:"
				//						+ csg.isInGroup());
				continue;
			}
			if (cache.get(csg) == null) {
				if (Platform.isFxApplicationThread())
					throw new RuntimeException("Computed bounds in UI thread!");
				else
					Log.debug("Computing bounds for " + csg.getName());
				// Log.error(new RuntimeException("Computing bounds for " + csg.getName()));
				Transform inverse = TransformFactory.nrToCSG(frame).inverse();

				if (csg.hasManipulator()) {
					Affine af;
					try {
						af = csg.getManipulator();
						TransformNR afNR = TransformFactory.affineToNr(af);
						inverse = TransformFactory.nrToCSG(afNR.inverse().times(frame)).inverse();
					} catch (MissingManipulatorException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				cache.put(csg, csg.transformed(inverse).getBounds());
			}
			Bounds b = cache.get(csg);
			Vector3d min2 = b.getMin().clone();
			Vector3d max2 = b.getMax().clone();
			if (min == null && min2 != null)
				min = min2.clone();
			if (max == null && max2 != null)
				max = max2.clone();
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
			if (min == null || max == null) {
				Log.error("Failed to find bounds!");
				throw new BoundsComputFailure("Failed to find bounds!!");
			}
		}
		if (min == null || max == null)
			throw new BoundsComputFailure("Failed to get bounds for objects: " + incoming);
		return new Bounds(min, max);
	}

	public Align copy() {
		return new Align().setBounds(boundNames).setNames(names).setAlignParams(x, y, z).setWorkplane(workplane)
				.setCache(cache);
	}

	public HashMap<CSG, Bounds> getCache() {
		return cache;
	}

	public Align setCache(HashMap<CSG, Bounds> cache) {
		this.cache = cache;
		return this;
	}
}
