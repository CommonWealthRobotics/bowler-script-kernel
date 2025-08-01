package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;

public class Allign extends CaDoodleOperation{
	@Expose (serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose (serialize = true, deserialize = true)
	public Allignment z=null;
	@Expose (serialize = true, deserialize = true)
	public Allignment y=null;
	@Expose (serialize = true, deserialize = true)
	public Allignment x=null;
	@Expose (serialize = true, deserialize = true)
	private TransformNR workplane=null;
	@Expose (serialize = true, deserialize = true)
	public StoragbeBounds bounds=null;
	@Expose(serialize = true, deserialize = true)
	protected String name = null;
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
		return "Allign";
	}
	
	@Override
	public String toString(){
		String string = getType()+" "+x+" "+y+" "+z;
		for(String n:getNamesAddedInThisOperation()) {
			string+=" "+n;
		}
		return string;
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);

		Bounds bounds2 ;//
		if(bounds!=null) {
			bounds2=bounds.getBounds();
		}else {
			throw new RuntimeException("Allign can not be initialized without bounds!");
		}
		HashMap<String,TransformNR> moves= new HashMap<>();
		for(String name :names) {
			for(CSG tmp:back) {
				if(!tmp.getName().contentEquals(name))
					continue;
				CSG c = tmp.transformed(TransformFactory.nrToCSG(getWorkplane()).inverse());
				TransformNR tf = performTransform(bounds2, c);
				moves.put(c.getName(),tf);
			}
		}
		for(String name:moves.keySet()) {
			TransformNR wpinv = getWorkplane().inverse();
			TransformNR nr = moves.get(name);
			TransformNR wp = getWorkplane();
			
			TransformNR times = wp.times(nr.times(wpinv));
			Transform tf =  TransformFactory.nrToCSG(times);
			CaDoodleFile.applyToAllConstituantElements(false, name, back, (incoming1, depth) ->{
				ArrayList<CSG> b = new ArrayList<>();
				CSG c=incoming1.transformed(tf);
				sync(incoming1,c);
				MoveCenter.set(getName() , c, times);
				b.add(c);
				return b;
			}, 1);
		}
		return back;
	}

//	private void collectToMove(ArrayList<CSG> toMove, ArrayList<CSG> back, String name) {
//		ArrayList<CSG> toSearch = new ArrayList<CSG>();
//		toSearch.addAll(back);
//		for (int i = 0; i < toSearch.size(); i++) {
//			CSG c = toSearch.get(i);
//			if(name.contentEquals(c.getName())) {
//				toMove.add(c);
//			}
//		}
//	}

	private TransformNR performTransform(Bounds reference, CSG incoming) {
		//CSG c = incoming;
		double tx=0,ty=0,tz=0;
		if(z!=null) {
			switch(z) {
			case negative:
				tz=-incoming.getMinZ()+reference.getMinZ();
				break;
			case middle:
				tz=-incoming.getCenterZ()+reference.getCenterZ();
				break;
			case positive:
				tz=-incoming.getMaxZ()+reference.getMaxZ();
				break;
			default:
				break;
			}
		}
		if(x!=null) {
			switch(x) {
			case negative:
				tx=-incoming.getMinX()+reference.getMinX();
				break;
			case middle:
				tx=-incoming.getCenterX()+reference.getCenterX();
				break;
			case positive:
				tx=-incoming.getMaxX()+reference.getMaxX();
				break;
			default:
				break;
			
			}
		}
		if(y!=null) {
			switch(y) {
			case negative:
				ty=-incoming.getMinY()+reference.getMinY();
				break;
			case middle:
				ty=-incoming.getCenterY()+reference.getCenterY();
				break;
			case positive:
				ty=-incoming.getMaxY()+reference.getMaxY();
				break;
			default:
				break;
			
			}
		}
		return new TransformNR(tx,ty,tz);
	}

	private CSG sync(CSG incoming, CSG c) {
		return c.syncProperties(incoming).setName(incoming.getName()).setColor(incoming.getColor());
	}

	public List<String> getNamesAddedInThisOperation() {
		return names;
	}

	public Allign setNames(List<String> names) {
		this.names = names;
		return this;
	}
	public Allign setAllignParams(Allignment X, Allignment Y,Allignment Z) {
		x=X;
		y=Y;
		z=Z;
		return this;
	}

	public TransformNR getWorkplane() {
		if(workplane==null)
			workplane= new TransformNR();
		return workplane;
	}

	public Allign setWorkplane(TransformNR workplane) {
		this.workplane = workplane;
		return this;
	}

	public Bounds getBounds() {
		return bounds.getBounds();
	}

	public Allign setBounds(Bounds bounds) {
		this.bounds = new StoragbeBounds(bounds);
		return this;
	}

}
