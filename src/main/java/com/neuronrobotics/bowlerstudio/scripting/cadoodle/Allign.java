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
	public AllignmentZ z=null;
	@Expose (serialize = true, deserialize = true)
	public AllignmentY y=null;
	@Expose (serialize = true, deserialize = true)
	public AllignmentX x=null;
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
				case BottomAllign:
					c=( c.toZMin()
						.movez(reference.getMinZ())
						);
					break;
				case Center:
					c=( c.moveToCenterZ()
							.movez(reference.getCenterZ()));
					break;
				case TopAllign:
					c=( c.toZMax()
							.movez(reference.getMaxZ()));
					break;
				default:
					break;
				}
			}
			if(x!=null) {
				switch(x) {
				case Back:
					c=( c.toXMin()
							.movex(reference.getMinX()));
					break;
				case Center:
					c=( c.moveToCenterX()
							.movex(reference.getCenterX()));
					break;
				case Front:
					c=( c.toXMax()
							.movex(reference.getMaxX()));
					break;
				default:
					break;
				
				}
			}
			if(y!=null) {
				switch(y) {
				case Center:
					c=( c.moveToCenterY()
							.movey(reference.getCenterY()));
					break;
				case Left:
					c=( c.toYMax()
							.movey(reference.getMaxY()));
					break;
				case Right:
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
	public Allign setAllignParams(AllignmentX X, AllignmentY Y,AllignmentZ Z) {
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
