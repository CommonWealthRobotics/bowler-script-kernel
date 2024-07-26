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
					if(c.getGroupMembership().contentEquals(name)) {
						groupConstituants.add(c);
					}
				}
			}
		}
		double zScale = Math.abs(height.getZ());
		CSG resizeUp = starting.scaleToMeasurmentZ(zScale);
		double zMove = -resizeUp.getMinZ()+starting.getMinZ();
		resizeUp=resizeUp
				.movez(zMove);
		double xdimen = Math.abs(rightFront.getX()-leftRear.getX());
		double ydimen = Math.abs(rightFront.getY()-leftRear.getY());
		resizeUp=scaleToMeasurmentXY(resizeUp,xdimen,ydimen);
		double xMove=-resizeUp.getMinX()+leftRear.getX();
		double yMove = -resizeUp.getMinY()+rightFront.getY();
		resizeUp=resizeUp
					.movex(xMove)
					.movey(yMove);
		back.removeAll(groupConstituants);
		for(CSG c:groupConstituants) {
			CSG gc = c.scaleToMeasurmentZ(zScale);
			gc=gc
					.movez(-gc.getMinZ()+c.getMinZ());
			gc=scaleToMeasurmentXY(gc,xdimen,ydimen);
			gc=gc
						.movex(xMove)
						.movey(yMove);
			gc.syncProperties(c).setName(c.getName());
			back.add(gc);
		}
		
		back.add( resizeUp.syncProperties(starting).setName(name));
	}
	
	private CSG scaleToMeasurmentXY(CSG inc,double x, double y) {
		double scalex = x/ inc.getTotalX();
		double scaley = y/ inc.getTotalY();

		Transform scale = new Transform().scale(scalex,scaley,1);
		return inc.moveToCenter().transformed(scale).move(inc.getCenter().transformed(scale));
	}

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
