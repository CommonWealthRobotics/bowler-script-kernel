package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
	private class ResizeEvent{
		Transform scaleZ;
		Transform scale;
		double movez;
		double movey;
		double movex;
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		HashMap<String,ResizeEvent> groupsProcessed = new HashMap<>();

		for (String name : names) {
			resizeByName(name,back,groupsProcessed);
		}
		return back;
	}

	private void resizeByName(String name, ArrayList<CSG> back, HashMap<String,ResizeEvent> groupsProcessed) {
		for (int i = 0; i < back.size(); i++) {
			CSG starting = back.get(i);
			if (	starting.getName().contentEquals(name) ){
				double zScale = Math.abs(height.getZ());
				double scalez = zScale/ starting.getTotalZ();
				
				Transform scaleZ =new Transform().scaleZ(scalez);
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
