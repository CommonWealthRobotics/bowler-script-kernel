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

	private HashMap<String, Bounds> cache = null;

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

	public Bounds getBounds(List<CSG> incoming, HashMap<String, Bounds> inWorkplaneBounds) throws BoundsComputFailure {
		if (bounds != null) {
			Log.error("Depricated Bounds in the align step!");
			return bounds.getBounds();
		}
		if (boundNames != null) {
			if (inWorkplaneBounds == null)
				inWorkplaneBounds = new HashMap<String, Bounds>();
			List<CSG> selectedCSG = getSelectedCSG(boundNames, incoming);
			return CaDoodleFile.getBounds(selectedCSG, workplane, inWorkplaneBounds, null);
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


	public Align copy() {
		return new Align().setBounds(boundNames).setNames(names).setAlignParams(x, y, z).setWorkplane(workplane)
				.setCache(cache);
	}

	public HashMap<String, Bounds> getCache() {
		return cache;
	}

	public Align setCache(HashMap<String, Bounds> cache) {
		this.cache = cache;
		return this;
	}
}
