package com.neuronrobotics.bowlerstudio.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import javax.vecmath.Vector3f;

import com.bulletphysics.BulletGlobals;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.linearmath.Transform;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.ILinkListener;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.pid.PIDLimitEvent;
import com.neuronrobotics.sdk.util.ThreadUtil;

import Jama.Matrix;
import eu.mihosoft.vrl.v3d.CSG;
import javafx.application.Platform;
import javafx.scene.transform.Affine;

public class MobileBasePhysicsManager {

	
	private HashMap<DHLink, CSG> simplecad;
	private float lift=20;
	public MobileBasePhysicsManager(MobileBase base, CSG baseCad , 
			HashMap<DHLink, CSG> simplecad ){
		this.simplecad = simplecad;
		double minz =0;
		for(DHParameterKinematics dh:base.getAllDHChains()){
			if(dh.getCurrentTaskSpaceTransform().getZ()<minz)
				minz=dh.getCurrentTaskSpaceTransform().getZ();
		}
		if(baseCad.getMinZ()<minz)
			minz = baseCad.getMinZ();
		System.out.println("Minimum z = "+minz);
		Transform start = new Transform();
		
		TransformFactory.nrToBullet(base.getFiducialToGlobalTransform(), start);
		start.origin.z=(float) (start.origin.z-minz+lift);
		Platform.runLater(()->TransformFactory.bulletToAffine(baseCad.getManipulator(), start));
		CSGPhysicsManager baseManager = new CSGPhysicsManager(baseCad,start, base.getMassKg());
		RigidBody body = baseManager.getFallRigidBody();
		PhysicsEngine.add(baseManager);
		for(DHParameterKinematics dh:base.getAllDHChains()){
			RigidBody lastLink=null;
			ArrayList<TransformNR> cached = dh.getDhChain().getCachedChain();
			for(int i=0;i<dh.getNumberOfLinks();i++){
				DHLink l = dh.getDhChain().getLinks().get(i);
				LinkConfiguration conf = dh.getLinkConfiguration(i);
				AbstractLink abstractLink = dh.getAbstractLink(i);
				Affine manipulator = simplecad.get(l).getManipulator();
				TransformNR localLink = cached.get(i);
				localLink.translateZ(lift);
				Transform linkLoc= new Transform();
				TransformFactory.nrToBullet(localLink, linkLoc);
				
				Platform.runLater(()->TransformFactory.nrToAffine(localLink, manipulator));
				ThreadUtil.wait(16);
				simplecad.get(l).setManipulator(manipulator);
				CSGPhysicsManager tmp = new CSGPhysicsManager(simplecad.get(l),linkLoc, conf.getMassKg());
				
				RigidBody linkSection = tmp.getFallRigidBody();
				// Setup some damping on the m_bodies
				linkSection.setDamping(0.05f, 0.85f);
				linkSection.setDeactivationTime(0.8f);
				linkSection.setSleepingThresholds(1.6f, 2.5f);
				
				Generic6DofConstraint joint6DOF;
				Transform localA = new Transform();
				Transform localB= new Transform();
				localA.setIdentity();
				localB.setIdentity();
				Matrix step;
				if(conf.getType().isPrismatic())
					step= l.DhStep(0);
				else
					step= l.DhStep(Math.toRadians(0));
				TransformFactory.nrToBullet(conf.getCenterOfMassFromCentroid(), localA);
				TransformFactory.nrToBullet(new TransformNR(step), localB);
				if(i==0){					
					joint6DOF= new Generic6DofConstraint(body, linkSection, localA, localB, true);
					tmp.setConstraint(joint6DOF);
				}else{
					joint6DOF= new Generic6DofConstraint(lastLink, linkSection, localA, localB, true);
				}
				lastLink = linkSection;
				//tmp.setConstraint(joint6DOF);
				int index=i;
				abstractLink.addLinkListener(new ILinkListener() {
					@Override
					public void onLinkPositionUpdate(AbstractLink source, double engineeringUnitsValue) {
						System.out.println("Link "+index+" value="+engineeringUnitsValue);
						PhysicsEngine.get().getDynamicsWorld().removeConstraint(joint6DOF);
						Vector3f t = new Vector3f();
						t.set((float) Math.toRadians(engineeringUnitsValue)-BulletGlobals.FLT_EPSILON, -BulletGlobals.FLT_EPSILON, -BulletGlobals.FLT_EPSILON);
						joint6DOF.setAngularLowerLimit(t);
						t.set((float) Math.toRadians(engineeringUnitsValue)+BulletGlobals.FLT_EPSILON, BulletGlobals.FLT_EPSILON, BulletGlobals.FLT_EPSILON);
						joint6DOF.setAngularUpperLimit(t);
						PhysicsEngine.get().getDynamicsWorld().addConstraint(joint6DOF);
					}
					@Override
					public void onLinkLimit(AbstractLink source, PIDLimitEvent event) {}
				});
				abstractLink.getCurrentPosition();
				PhysicsEngine.add(tmp);
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
