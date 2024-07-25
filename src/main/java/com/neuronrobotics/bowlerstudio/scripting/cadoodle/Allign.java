package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

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
				reference=c;
			}
		}
		for(CSG c: back) {
			for(String name:names)
			if(name.contentEquals(c.getName())) {
				back.remove(c);
				toMove.add(c);
			}
		}
		for(CSG c:toMove) {
			if(z!=null) {
				switch(z) {
				case BottomAllign:
					back.add( c.toZMin()
						.movez(reference.getMinZ()));
					break;
				case Center:
					back.add( c.moveToCenterZ()
							.movez(reference.getCenterZ()));
					break;
				case TopAllign:
					back.add( c.toZMax()
							.movez(reference.getMaxZ()));
					break;
				default:
					break;
				}
			}
			if(x!=null) {
				switch(x) {
				case Back:
					back.add( c.toXMin()
							.movex(reference.getMinX()));
					break;
				case Center:
					back.add( c.moveToCenterX()
							.movex(reference.getCenterX()));
					break;
				case Front:
					back.add( c.toXMax()
							.movex(reference.getMaxX()));
					break;
				default:
					break;
				
				}
			}
			if(y!=null) {
				switch(y) {
				case Center:
					back.add( c.moveToCenterY()
							.movey(reference.getCenterY()));
					break;
				case Left:
					back.add( c.toYMax()
							.movey(reference.getMaxY()));
					break;
				case Right:
					back.add( c.toYMin()
							.movey(reference.getMinY()));
					break;
				default:
					break;
				
				}
			}
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

}
