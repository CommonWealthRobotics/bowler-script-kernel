package com.neuronrobotics.bowlerstudio.physics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import javax.vecmath.Vector3f;

import com.bulletphysics.BulletGlobals;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.Generic6DofConstraint;
import com.bulletphysics.dynamics.constraintsolver.HingeConstraint;
import com.bulletphysics.linearmath.Transform;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.ILinkListener;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.IClosedLoopController;
import com.neuronrobotics.sdk.pid.PIDLimitEvent;
import com.neuronrobotics.sdk.util.ThreadUtil;

import Jama.Matrix;
import eu.mihosoft.vrl.v3d.CSG;
import javafx.application.Platform;
import javafx.scene.transform.Affine;

public class MobileBasePhysicsManager {

	
	private HashMap<DHLink, CSG> simplecad;
	private float lift=20;
	private ArrayList<ILinkListener> linkListeners=new ArrayList<>();
	public static final float LIFT_EPS = 0.0000001f;
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
		base.setFiducialToGlobalTransform(new TransformNR());
		//TransformNR globe= base.getFiducialToGlobalTransform();
		
		TransformFactory.nrToBullet(base.getFiducialToGlobalTransform(), start);
		start.origin.z=(float) (start.origin.z-minz+lift);
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				TransformFactory.bulletToAffine(baseCad.getManipulator(), start);
			}
		});
		CSGPhysicsManager baseManager = new CSGPhysicsManager(baseCad,start,0.1,false);
		RigidBody body = baseManager.getFallRigidBody();
		PhysicsEngine.get()
			.getDynamicsWorld()
			.setGravity(new Vector3f(0, 0, (float) -98*6));
		PhysicsEngine.add(baseManager);
		for(int j=0;j<base.getAllDHChains().size();j++){
			DHParameterKinematics dh=base.getAllDHChains().get(j);
			RigidBody lastLink=body;
			Matrix previousStep=null;
			ArrayList<TransformNR> cached = dh.getDhChain().getCachedChain();
			for(int i=0;i<dh.getNumberOfLinks();i++){
				// Hardware to engineering units configuration
				LinkConfiguration conf = dh.getLinkConfiguration(i);
				//DH parameters
				DHLink l = dh.getDhChain().getLinks().get(i);
				boolean flagAlpha=false;
				boolean flagTheta=false;
				double jogAmount=0.001;
				// Check for singularities and just jog it off the singularity. 
				if(Math.toDegrees(l.getAlpha())%90<jogAmount){
					l.setAlpha(l.getAlpha()+Math.toRadians(jogAmount));
					cached = dh.getDhChain().getCachedChain();
					flagAlpha=true;
				}
				if(Math.toDegrees(l.getTheta())%90<jogAmount){
					l.setTheta(l.getTheta()+Math.toRadians(jogAmount));
					cached = dh.getDhChain().getCachedChain();
					flagTheta=true;
				}
				// use the DH parameters to calculate the offset of the link at 0 degrees
				Matrix step;
				if(conf.getType().isPrismatic())
					step= l.DhStepInversePrismatic(0);
				else
					step= l.DhStepInverseRotory(Math.toRadians(0));
				// correct jog for singularity. 
				
				if(flagAlpha){
					l.setAlpha(l.getAlpha()-Math.toRadians(jogAmount));	
				}
				if(flagTheta){
					l.setTheta(l.getTheta()-Math.toRadians(jogAmount));	
				}	
				
				// Engineering units to kinematics link (limits and hardware type abstraction)
				AbstractLink abstractLink = dh.getAbstractLink(i);
				// Transform used by the UI to render the location of the object
				Affine manipulator = simplecad.get(l).getManipulator();
				// The DH chain calculated the starting location of the link in its current configuration
				TransformNR localLink = cached.get(i);
				// Lift it in the air so nothing is below the ground to start. 
				localLink.translateZ(lift);
				// Bullet engine transform object
				Transform linkLoc= new Transform();
				TransformFactory.nrToBullet(localLink, linkLoc);
				linkLoc.origin.z=(float) (linkLoc.origin.z-minz+lift);
				// Set the manipulator to the location from the kinematics, needs to be in UI thread to touch manipulator
				Platform.runLater(new Runnable() {
					@Override
					public void run() {
						TransformFactory.nrToAffine(localLink, manipulator);
					}
				});
				ThreadUtil.wait(16);
				simplecad.get(l).setManipulator(manipulator);
				double mass=conf.getMassKg();

				CSG cadPart = simplecad.get(l)
							.transformed(TransformFactory.nrToCSG(new TransformNR(step).inverse()));
				// Build a hinge based on the link and mass
				HingeCSGPhysicsManager hingePhysicsManager = new HingeCSGPhysicsManager(cadPart,linkLoc, mass);
				hingePhysicsManager.setMuscleStrength(1000000);
				

				RigidBody linkSection = hingePhysicsManager.getFallRigidBody();
//				// Setup some damping on the m_bodies
				linkSection.setDamping(0.05f, 0.85f);
				linkSection.setDeactivationTime(0.8f);
				linkSection.setSleepingThresholds(1.6f, 2.5f);
				
				HingeConstraint joint6DOF;
				Transform localA = new Transform();
				Transform localB= new Transform();
				localA.setIdentity();
				localB.setIdentity();

				// set up the center of mass offset from the centroid of the links
				if(i==0){
					
					TransformFactory.nrToBullet(dh.forwardOffset(new TransformNR()), localA);
				}else
					TransformFactory.nrToBullet(new TransformNR(previousStep.inverse()), localA);
				//set the link constraint based on DH parameters
				TransformFactory.nrToBullet(new TransformNR(), localB);
				previousStep = step;
				// build the hinge constraint			
				joint6DOF= new HingeConstraint(lastLink, linkSection, localA, localB);
				joint6DOF.setLimit(	-(float)Math.toRadians(abstractLink.getMinEngineeringUnits()),
								-(float)Math.toRadians(abstractLink.getMaxEngineeringUnits()));
								
				lastLink = linkSection;
				if(i<3)hingePhysicsManager.setConstraint(joint6DOF);
				ILinkListener ll=new ILinkListener() {
					@Override
					public void onLinkPositionUpdate(AbstractLink source, double engineeringUnitsValue) {
						//System.out.println(" value="+engineeringUnitsValue);
						hingePhysicsManager.setTarget(Math.toRadians(-engineeringUnitsValue));
						//joint6DOF.setLimit(	(float) Math.toRadians(-engineeringUnitsValue )- LIFT_EPS,
						//				(float) Math.toRadians(-engineeringUnitsValue )+ LIFT_EPS);
					}
					@Override
					public void onLinkLimit(AbstractLink source, PIDLimitEvent event) {
						//println event
					}
				};
				if(!conf.getType().isTool()){
					hingePhysicsManager.setController(new IClosedLoopController() {
						
						@Override
						public double compute(double currentState, double target, double seconds) {
							double error = target-currentState;
							return (error/seconds)*(seconds*10);
							//return 0
						}
					});
					abstractLink.addLinkListener(ll);linkListeners.add(ll);
				}
				
				
				
				abstractLink.getCurrentPosition();
				PhysicsEngine.add(hingePhysicsManager);
			}
			
		}
	}
}
