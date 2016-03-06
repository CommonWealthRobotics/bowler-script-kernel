package com.neuronrobotics.bowlerstudio.physics;

import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.bulletphysics.collision.shapes.*;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.HingeConstraint;
import com.bulletphysics.dynamics.constraintsolver.TypedConstraint;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.bulletphysics.util.ObjectArrayList;
//import com.neuronrobotics.sdk.addons.kinematics.gui.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Sphere;
import eu.mihosoft.vrl.v3d.Vertex;
import javafx.application.Platform;
import javafx.scene.transform.Affine;

public class CSGPhysicsManager {
	
	private RigidBody fallRigidBody;
	private Affine ballLocation = new Affine();
	protected CSG baseCSG;
	private Transform updateTransform = new Transform();

	public CSGPhysicsManager(int sphereSize, Vector3f start, double mass){
		this.setBaseCSG(new Sphere(sphereSize).toCSG());
		CollisionShape fallShape = new SphereShape((float) (baseCSG.getMaxX()-baseCSG.getMinX())/2);
		setup(fallShape,new Transform(new Matrix4f(new Quat4f(0, 0, 0, 1), start, 1.0f)),mass);
	}
	public CSGPhysicsManager(CSG baseCSG, Vector3f start, double mass){
		this(baseCSG,new Transform(new Matrix4f(new Quat4f(0, 0, 0, 1), start, 1.0f)),mass);
	}
	
//	public CSGPhysicsManager(CSG baseCSG,  double mass){
//		this.setBaseCSG(baseCSG);// force a hull of the shape to simplify physics
//		
//		
//		ObjectArrayList<Vector3f> arg0= new ObjectArrayList<>();
//		for( Polygon p:baseCSG.getPolygons()){
//			for( Vertex v:p.vertices){
//				arg0.add(new Vector3f((float)v.getX(), (float)v.getY(), (float)v.getZ()));
//			}
//		}
//		TransformNR startPose = TransformFactory.affineToNr(baseCSG.getManipulator());
//		CollisionShape fallShape =  new com.bulletphysics.collision.shapes.ConvexHullShape(arg0);
//		Transform tr= new Transform();
//		TransformFactory.nrToBullet(startPose, tr);
//		setup(fallShape,tr,mass);
//	}
	
	public CSGPhysicsManager(CSG baseCSG, Transform pose,  double mass){
		this.setBaseCSG(baseCSG);// force a hull of the shape to simplify physics
		
		
		ObjectArrayList<Vector3f> arg0= new ObjectArrayList<>();
		for( Polygon p:baseCSG.getPolygons()){
			for( Vertex v:p.vertices){
				arg0.add(new Vector3f((float)v.getX(), (float)v.getY(), (float)v.getZ()));
			}
		}
		CollisionShape fallShape =  new com.bulletphysics.collision.shapes.ConvexHullShape(arg0);
		setup(fallShape,pose,mass);
	}
	public void setup(CollisionShape fallShape,Transform pose, double mass ){
		// setup the motion state for the ball
		DefaultMotionState fallMotionState = new DefaultMotionState(
				pose);
		// This we're going to give mass so it responds to gravity
		Vector3f fallInertia = new Vector3f(0, 0, 0);
		fallShape.calculateLocalInertia((float) mass, fallInertia);
		RigidBodyConstructionInfo fallRigidBodyCI = new RigidBodyConstructionInfo((float) mass, fallMotionState, fallShape,
				fallInertia);
		fallRigidBodyCI.additionalDamping = true;
		setFallRigidBody(new RigidBody(fallRigidBodyCI));
		update(40);
	}
	

	public void update(float timeStep){
		
		fallRigidBody.getMotionState().getWorldTransform(updateTransform);
		
		Platform.runLater(()->TransformFactory.bulletToAffine(ballLocation, updateTransform));
		
	}


	public RigidBody getFallRigidBody() {
		return fallRigidBody;
	}

	public void setFallRigidBody(RigidBody fallRigidBody) {
		
		this.fallRigidBody = fallRigidBody;
	}

	public CSG getBaseCSG() {
		return baseCSG;
	}

	public void setBaseCSG(CSG baseCSG) {
		
		baseCSG.setManipulator(ballLocation);
		this.baseCSG = baseCSG;
	}


}