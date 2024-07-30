package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;

public class Resize implements ICaDoodleOpperation {

	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();

	@Expose(serialize = true, deserialize = true)
	private TransformNR height = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR rightFront = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR leftRear = null;

	@Override
	public String getType() {
		return "Resize";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		for(CSG c:incoming) {
			for(String name:names) {
				if(c.getName().contentEquals(name)) {
					performResize(c,  name,back);
				}
			}
		}
		
		return back;
	}

	private void performResize(CSG starting, String name,ArrayList<CSG> back) {
		back.remove(starting);
		ArrayList<CSG> groupConstituants = new ArrayList<CSG>();
		if(starting.isGroupResult()) {
			for(CSG c:back) {
				if(c.isInGroup()) {
					if(c.checkGroupMembership(name)) {
						groupConstituants.add(c);
					}
				}
			}
		}
		double zScale = Math.abs(height.getZ());
		Number scaleValue = zScale/ starting.getTotalZ();
		
		Transform scaleZ =new Transform().scaleZ(scaleValue.doubleValue());
		CSG resizeUp = starting.transformed(scaleZ);
		double zMove = -resizeUp.getMinZ()+starting.getMinZ();
		resizeUp=resizeUp
				.movez(zMove);
		double xdimen = Math.abs(rightFront.getX()-leftRear.getX());
		double ydimen = Math.abs(rightFront.getY()-leftRear.getY());
		double scalex = xdimen/ resizeUp.getTotalX();
		double scaley = ydimen/ resizeUp.getTotalY();

		Transform scale = new Transform().scale(scalex,scaley,1);
		resizeUp=resizeUp.transformed(scale);
		double xMove=-resizeUp.getMinX()+leftRear.getX();
		double yMove = -resizeUp.getMinY()+rightFront.getY();
		resizeUp=resizeUp
					.movex(xMove)
					.movey(yMove);
		back.removeAll(groupConstituants);
		for(CSG c:groupConstituants) {
			CSG gc = c.transformed(scaleZ);
			gc=gc
					.movez(-gc.getMinZ()+c.getMinZ());
			gc=gc.transformed(scale);
			gc=gc
						.movex(xMove)
						.movey(yMove);
			gc.syncProperties(c).setName(c.getName());
			back.add(gc);
		}
		
		back.add( resizeUp.syncProperties(starting).setName(name));
	}
//	
//	private CSG scaleToMeasurmentXY(CSG inc,double x, double y) {
//
//		return inc.moveToCenter().transformed(scale).move(inc.getCenter().transformed(scale));
//	}

	public Resize setResize(TransformNR h, TransformNR rf, TransformNR lr) {
		height = h;
		rightFront = rf;
		leftRear = lr;
		return this;
	}

	public List<String> getNames() {
		return names;
	}

	public Resize setNames(List<String> names) {
		this.names = names;
		return this;
	}

}
