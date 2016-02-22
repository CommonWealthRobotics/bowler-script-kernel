package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import com.bulletphysics.dynamics.RigidBody;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class MobileBasePhysicsManager {

	
	private HashMap<DHLink, CSG> simplecad;

	public MobileBasePhysicsManager(MobileBase base, CSG baseCad , 
			HashMap<DHLink, CSG> simplecad ){
		this.simplecad = simplecad;
		double minz =0;
		for(DHParameterKinematics dh:base.getAppendages()){
			if(dh.getCurrentTaskSpaceTransform().getZ()<minz)
				minz=dh.getCurrentTaskSpaceTransform().getZ();
		}
		System.out.println("Minimum z = "+minz);
		TransformNR baseStart = base.getFiducialToGlobalTransform();
		baseStart.setZ(baseStart.getZ()-minz);// should boost the robot up to the ground plane.
		base.setGlobalToFiducialTransform(baseStart);
		
		for(DHParameterKinematics dh:base.getAppendages()){
			for(int i=0;i<dh.getNumberOfLinks();i++){
				DHLink l = dh.getDhChain().getLinks().get(i);
				LinkConfiguration conf = dh.getLinkConfiguration(i);
				CSGPhysicsManager tmp = new CSGPhysicsManager(simplecad.get(l), conf.getMassKg());
				RigidBody body = tmp.getFallRigidBody();
				
			}
		}
	}
	
	
	
	public ArrayList<CSG> getCSG(){
		ArrayList<CSG> vals=new ArrayList<>();
		Set<DHLink> keys = simplecad.keySet();
		for(DHLink l:keys){
			vals.add(simplecad.get(l));
		}
		return vals;
	}
}
