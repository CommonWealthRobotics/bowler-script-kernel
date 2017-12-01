package com.neuronrobotics.bowlerstudio.physics;

import java.util.ArrayList;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;

import eu.mihosoft.vrl.v3d.CSG;

import javafx.scene.transform.Affine;

public interface IPhysicsManager {
  /**
   * Run the update for this ridgid body. Run any controllers for links
   */
  public void update(float timeStep);

  /**
   * Return a RigidBody for the physics engine
   */
  public RigidBody getFallRigidBody();

  /**
   * Return the CSG that tis being modelsed
   */
  public ArrayList<CSG> getBaseCSG();

  /**
   * Return the current spatial location fo the rigid body
   */
  public Affine getRigidBodyLocation();

  /**
   * The Bullet version of the location
   */
  public Transform getUpdateTransform();

}
