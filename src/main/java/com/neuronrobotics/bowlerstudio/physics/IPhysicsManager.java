package com.neuronrobotics.bowlerstudio.physics;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.transform.Affine;

public interface IPhysicsManager {
	
	public void update(float timeStep);
	public RigidBody getFallRigidBody() ;
	public CSG getBaseCSG() ;
	public Affine getRigidBodyLocation();
	public Transform getUpdateTransform();
	
}
