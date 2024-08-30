package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class Allign implements ICaDoodleOpperation {
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
	
	@Override
	public String getType() {
		return "Allign";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> toMove = new ArrayList<CSG>();
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		CSG reference=null;
		for(CSG c: incoming) {
			String name = names.get(0);
			if(name.contentEquals(c.getName())) {
				back.remove(c);
				reference=c.transformed(TransformFactory.nrToCSG(workplane).inverse());
			}
		}
		for(CSG c: back) {
			for(String name:names)
			if(name.contentEquals(c.getName())) {
				back.remove(c);
				toMove.add(c);
			}
		}
		for(CSG tmp:toMove) {
			CSG c = tmp.transformed(TransformFactory.nrToCSG(workplane).inverse());
			if(z!=null) {
				switch(z) {
				case negative:
					c=( c.toZMin()
						.movez(reference.getMinZ())
						);
					break;
				case middle:
					c=( c.moveToCenterZ()
							.movez(reference.getCenterZ()));
					break;
				case positive:
					c=( c.toZMax()
							.movez(reference.getMaxZ()));
					break;
				default:
					break;
				}
			}
			if(x!=null) {
				switch(x) {
				case negative:
					c=( c.toXMin()
							.movex(reference.getMinX()));
					break;
				case middle:
					c=( c.moveToCenterX()
							.movex(reference.getCenterX()));
					break;
				case positive:
					c=( c.toXMax()
							.movex(reference.getMaxX()));
					break;
				default:
					break;
				
				}
			}
			if(y!=null) {
				switch(y) {
				case middle:
					c=( c.moveToCenterY()
							.movey(reference.getCenterY()));
					break;
				case positive:
					c=( c.toYMax()
							.movey(reference.getMaxY()));
					break;
				case negative:
					c= c.toYMin()
							.movey(reference.getMinY());
					break;
				default:
					break;
				
				}
			}
			back.add(c.transformed(TransformFactory.nrToCSG(workplane)));
		}
		return back;
	}

	public List<String> getNames() {
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

}
