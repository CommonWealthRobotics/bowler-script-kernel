package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;

public class Resize implements ICaDoodleOpperation {

	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();

	@Expose(serialize = true, deserialize = true)
	private TransformNR height = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR leftFront = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR rightRear = null;

	@Override
	public String getType() {
		return "Resize";
	}
	private class ResizeEvent{
		Transform scaleZ;
		Transform scale;
		double movez;
		double movey;
		double movex;
	}

	public Bounds getSellectedBounds(List<CSG> incoming) {
		Vector3d min = null;
		Vector3d max = null;
		for (CSG c : incoming) {
			Vector3d min2 = c.getBounds().getMin().clone();
			Vector3d max2 = c.getBounds().getMax().clone();
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
		}

		return new Bounds(min, max);
	}
	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		HashMap<String,ResizeEvent> groupsProcessed = new HashMap<>();
		ArrayList<CSG> selected = new ArrayList<CSG>();
		for(CSG c:incoming)
			for (String name : names) {
				if(c.getName().contentEquals(name)) {
					selected.add(c);
				}
			}
		Bounds b = getSellectedBounds(selected);

		for (String name : names) {
			resizeByName(name,back,groupsProcessed,b);
		}
		return back;
	}

	private void resizeByName(String name, ArrayList<CSG> back, HashMap<String,ResizeEvent> groupsProcessed,Bounds bounds) {
		for (int i = 0; i < back.size(); i++) {
			CSG starting = back.get(i);
			if (	starting.getName().contentEquals(name) ){
				double zScale = Math.abs(height.getZ())-bounds.getMin().z;
				double scalez = zScale/ (bounds.getMax().z-bounds.getMin().z);
				
				Transform scaleZ =new Transform().scaleZ(scalez);
				CSG resizeUp = starting.transformed(scaleZ);
				double zMove = -(bounds.getMin().z*scalez)+bounds.getMin().z;
				resizeUp=resizeUp
						.movez(zMove);
				double xdimen = Math.abs(leftFront.getX()-rightRear.getX());
				double ydimen = Math.abs(leftFront.getY()-rightRear.getY());
				double scalex = xdimen/ (bounds.getMax().x-bounds.getMin().x);
				double scaley = ydimen/ (bounds.getMax().y-bounds.getMin().y);

				Transform scale = new Transform().scale(scalex,scaley,1);
				resizeUp=resizeUp.transformed(scale);
				double xMove=-(bounds.getMin().x*scalex)+rightRear.getX();
				double yMove = -(bounds.getMin().y*scaley)+rightRear.getY();
				resizeUp=resizeUp
							.movex(xMove)
							.movey(yMove);
				resizeUp.syncProperties(starting).setName(name);
				ResizeEvent ev = new ResizeEvent();
				ev.movex=xMove;
				ev.movey=yMove;
				ev.movez=zMove;
				ev.scale=scale;
				ev.scaleZ=scaleZ;
				back.set(i, resizeUp);
				groupsProcessed.put(name, ev);
				
				if(starting.isGroupResult()) {
					processCompositMembers(name,back,groupsProcessed);
				}
			}
		}
	}
	

	private void processCompositMembers(String name, ArrayList<CSG> back,
			HashMap<String, ResizeEvent> groupsProcessed) {
		for (int i = 0; i < back.size(); i++) {
			CSG c = back.get(i);
			if(c.isInGroup() && c.checkGroupMembership(name) ) {
				
				ResizeEvent ev =groupsProcessed.get(name);
				CSG gc = c.transformed(ev.scaleZ);
				gc=gc
						.movez(ev.movez);
				gc=gc.transformed(ev.scale);
				gc=gc
							.movex(ev.movex)
							.movey(ev.movey);
				gc.syncProperties(c).setName(c.getName());
				back.set(i, gc);
				if( c.isGroupResult()) {
					groupsProcessed.put(c.getName(), ev);
					processCompositMembers(c.getName(),back,groupsProcessed);
				}
			}
		}
	}

	public Resize setResize(TransformNR h, TransformNR lf, TransformNR rr) {
		height = h;
		leftFront = lf;
		rightRear = rr;
		if(rightRear.getY()>=leftFront.getY() && rightRear.getX()>=leftFront.getX())
			return setResize(h,rr,lf);// they were swapped, just fix it and move along
		if(rightRear.getY()>=leftFront.getY() || rightRear.getX()>=leftFront.getX())
			throw new RuntimeException("Scale must be positive!");
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
