package com.neuronrobotics.bowlerstudio.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.linearmath.Transform;
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
			dh.getCurrentTaskSpaceTransform();
		}
		CSGPhysicsManager baseManager = new CSGPhysicsManager(baseCad, base.getMassKg());
		RigidBody body = baseManager.getFallRigidBody();
		PhysicsEngine.add(baseManager);
		DiscreteDynamicsWorld world = PhysicsEngine.get().getDynamicsWorld();
		TransformNR baseTransform = base.getCurrentTaskSpaceTransform();
		for(DHParameterKinematics dh:base.getAppendages()){
			RigidBody lastLink=null;
			ArrayList<TransformNR> cached = dh.getDhChain().getCachedChain();
			for(int i=0;i<dh.getNumberOfLinks();i++){
				DHLink l = dh.getDhChain().getLinks().get(i);
				LinkConfiguration conf = dh.getLinkConfiguration(i);
				CSGPhysicsManager tmp = new CSGPhysicsManager(simplecad.get(l), conf.getMassKg());
				PhysicsEngine.add(tmp);
				RigidBody linkSection = tmp.getFallRigidBody();
				Generic6DofConstraint joint6DOF;
				Transform localA = new Transform();
				Transform localB= new Transform();
				if(i==0){
					TransformFactory.nrToBullet(baseTransform, localA);
					TransformFactory.nrToBullet(cached.get(0), localB);
					joint6DOF= new Generic6DofConstraint(body, linkSection, localA, localB, true);
				}else{
					TransformFactory.nrToBullet(cached.get(i-1), localA);
					TransformFactory.nrToBullet(cached.get(i), localB);
					joint6DOF= new Generic6DofConstraint(lastLink, linkSection, localA, localB, true);
				}
				
				lastLink = linkSection;
				world.addConstraint(joint6DOF);
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
