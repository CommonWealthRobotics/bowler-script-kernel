package com.neuronrobotics.bowlerstudio.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.bulletphysics.collision.broadphase.BroadphaseInterface;
import com.bulletphysics.collision.broadphase.DbvtBroadphase;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.CollisionShape;
import com.bulletphysics.collision.shapes.CompoundShape;
import com.bulletphysics.collision.shapes.StaticPlaneShape;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import com.bulletphysics.util.ObjectArrayList;
import com.neuronrobotics.bowlerstudio.IssueReportingExceptionHandler;
import com.neuronrobotics.sdk.util.ThreadUtil;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Vertex;
import javafx.application.Platform;

public class JBulletCore implements IPhysicsCore {

  private BroadphaseInterface broadphase = new DbvtBroadphase();
  private DefaultCollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();
  private CollisionDispatcher dispatcher = new CollisionDispatcher(collisionConfiguration);

  private SequentialImpulseConstraintSolver solver = new SequentialImpulseConstraintSolver();

  private DiscreteDynamicsWorld dynamicsWorld = new DiscreteDynamicsWorld(dispatcher, broadphase,
      solver,
      collisionConfiguration);
  // setup our collision shapes
  private CollisionShape groundShape = null;

  private ArrayList<IPhysicsManager> objects = new ArrayList<>();
  private RigidBody groundRigidBody;

  private boolean runEngine = false;
  private int msTime = 16;

  private Thread physicsThread = null;
  private int simulationSubSteps = 5;
  private float lin_damping;
  private float ang_damping;
  private float linearSleepThreshhold;
  private float angularSleepThreshhold;
  private float deactivationTime;
  private List<CSG> ground=null;
  public static final float PhysicsGravityScalar = 6;
  public JBulletCore() throws Exception {
    // set the gravity of our world
    getDynamicsWorld().setGravity(
        new Vector3f(0, 0, (float) -98 * MobileBasePhysicsManager.PhysicsGravityScalar));

    setGroundShape(new StaticPlaneShape(new Vector3f(0, 0, 10), 1));
  }
  
  public JBulletCore(List<CSG> ground) throws Exception {
	this.ground = ground;
	// set the gravity of our world
	getDynamicsWorld().setGravity(new Vector3f(0, 0, (float) -98 * MobileBasePhysicsManager.PhysicsGravityScalar));

	CollisionShape cs = new CompoundShape();
	Transform localTransform = new Transform();
	localTransform.setIdentity();
	((CompoundShape)cs).addChildShape(localTransform, new StaticPlaneShape(new Vector3f(0, 0, 10), 1));
	
	for(int i=0;i<ground.size();i++) {
		ObjectArrayList<Vector3f> arg0 = new ObjectArrayList<>();
		List<Polygon> polygons = ground.get(i).getPolygons();
		//if(polygons.size()>1000)
		//	 polygons = getBoundingBox(finalCSG).getPolygons();
		for (Polygon p : polygons) {
			for (Vertex v : p.vertices) {
				arg0.add(new Vector3f((float) v.getX(), (float) v.getY(), (float) v.getZ()));
			}
		}
		CollisionShape fallShape = new com.bulletphysics.collision.shapes.ConvexHullShape(arg0);
		
		((CompoundShape)cs).addChildShape(localTransform, fallShape);
	}
	setGroundShape(cs);
  }
  public BroadphaseInterface getBroadphase() {
    return broadphase;
  }

  public void setBroadphase(BroadphaseInterface broadphase) {
    this.broadphase = broadphase;
  }

  public DefaultCollisionConfiguration getCollisionConfiguration() {
    return collisionConfiguration;
  }

  public void setCollisionConfiguration(DefaultCollisionConfiguration collisionConfiguration) {
    this.collisionConfiguration = collisionConfiguration;
  }

  public CollisionDispatcher getDispatcher() {
    return dispatcher;
  }

  public void setDispatcher(CollisionDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  public SequentialImpulseConstraintSolver getSolver() {
    return solver;
  }

  public void setSolver(SequentialImpulseConstraintSolver solver) {
    this.solver = solver;
  }

  public DiscreteDynamicsWorld getDynamicsWorld() {
    return dynamicsWorld;
  }

  public void setDynamicsWorld(DiscreteDynamicsWorld dynamicsWorld) {
    this.dynamicsWorld = dynamicsWorld;
  }

  public CollisionShape getGroundShape() {
    return groundShape;
  }

  public void setGroundShape(CollisionShape cs) {
    if (groundRigidBody != null) {
      getDynamicsWorld().removeRigidBody(groundRigidBody); // add our
      // ground to
      // the
    }
    this.groundShape = cs;
    // setup the motion state
    DefaultMotionState groundMotionState = new DefaultMotionState(
        new Transform(new Matrix4f(new Quat4f(0, 0, 0, 1), new Vector3f(0, 0, 0), 1.0f)));

    RigidBodyConstructionInfo groundRigidBodyCI = new RigidBodyConstructionInfo(0,
        groundMotionState, groundShape,
        new Vector3f(0, 0, 0));
    groundRigidBody = new RigidBody(groundRigidBodyCI);
    dynamicsWorld.addRigidBody(groundRigidBody); // add our ground to the
    dynamicsWorld.setGravity(new Vector3f(0, 0, (float) -98 * PhysicsGravityScalar));
  }

  public ArrayList<IPhysicsManager> getPhysicsObjects() {
    return objects;
  }

  public void setDamping(float lin_damping, float ang_damping) {
    this.lin_damping = (lin_damping);
    this.ang_damping = (ang_damping);
    for (IPhysicsManager m : getPhysicsObjects()) {
      m.getFallRigidBody().setDamping(lin_damping, ang_damping);
    }
  }

  public void setSleepingThresholds(float linearSleepThreshhold, float angularSleepThreshhold) {
    this.linearSleepThreshhold = (linearSleepThreshhold);
    this.angularSleepThreshhold = (angularSleepThreshhold);
    for (IPhysicsManager m : getPhysicsObjects()) {
      m.getFallRigidBody().setSleepingThresholds(linearSleepThreshhold, angularSleepThreshhold);
    }
  }

  public void setDeactivationTime(float deactivationTime) {
    this.deactivationTime = deactivationTime;
    for (IPhysicsManager m : getPhysicsObjects()) {
      m.getFallRigidBody().setDeactivationTime(deactivationTime);
    }
  }

  public void setObjects(ArrayList<IPhysicsManager> objects) {
    this.objects = objects;
  }

  public void startPhysicsThread(int ms) {
    msTime = ms;
    if (physicsThread == null) {
      runEngine = true;
      physicsThread = new Thread(() -> {
			Thread.currentThread().setUncaughtExceptionHandler(new IssueReportingExceptionHandler());

        while (runEngine) {
          try {
            long start = System.currentTimeMillis();
            stepMs(msTime);
            long took = (System.currentTimeMillis() - start);
            if (took < msTime) {
              ThreadUtil.wait((int) (msTime - took));
            } else {
              System.out.println("Real time physics broken: " + took);
            }
          } catch (Exception E) {
            E.printStackTrace();
          }
        }
      });
      physicsThread.start();
    }
  }

  public ArrayList<CSG> getCsgFromEngine() {
    ArrayList<CSG> csg = new ArrayList<>();
    for (IPhysicsManager o : getPhysicsObjects()) {
      for (CSG c : o.getBaseCSG()) {
        csg.add(c);
      }
    }
    if(ground!=null)
    	csg.addAll(ground);
    return csg;
  }

  public void stopPhysicsThread() {
    physicsThread = null;
    runEngine = false;
  }

  public void step(float timeStep) {
    long startTime = System.currentTimeMillis();

    getDynamicsWorld().stepSimulation(timeStep, getSimulationSubSteps());
    if ((((float) (System.currentTimeMillis() - startTime)) / 1000.0f) > timeStep) {
      // System.out.println(" Compute took too long "+timeStep);
    }
    for (IPhysicsManager o : getPhysicsObjects()) {
      o.update(timeStep);
    }

    Platform.runLater(() -> {
      for (IPhysicsManager o : getPhysicsObjects()) {
        try {
          TransformFactory.bulletToAffine(o.getRigidBodyLocation(), o.getUpdateTransform());
        } catch (Exception e) {

        }
      }
    });
  }

  public void stepMs(double timeStep) {
    step((float) (timeStep / 1000.0));
  }

  public void add(IPhysicsManager manager) {
    if (!getPhysicsObjects().contains(manager)) {
      getPhysicsObjects().add(manager);
      if (!WheelCSGPhysicsManager.class.isInstance(manager)
          //&& !VehicleCSGPhysicsManager.class.isInstance(manager)
          ) {
        getDynamicsWorld().addRigidBody(manager.getFallRigidBody());
      }
      if (JBulletHingeCSGPhysicsManager.class.isInstance(manager)) {
        if (((JBulletHingeCSGPhysicsManager) manager).getConstraint() != null) {
          getDynamicsWorld()
              .addConstraint(((JBulletHingeCSGPhysicsManager) manager).getConstraint(), true);
        }
      }
//      if (VehicleCSGPhysicsManager.class.isInstance(manager)) {
//        getDynamicsWorld().addVehicle(((VehicleCSGPhysicsManager) manager).getVehicle());
//      }

    }
  }

  public void remove(IPhysicsManager manager) {
    if (getPhysicsObjects().contains(manager)) {
      getPhysicsObjects().remove(manager);
      if (!WheelCSGPhysicsManager.class.isInstance(manager)
          //&& !VehicleCSGPhysicsManager.class.isInstance(manager)
          ) {

        getDynamicsWorld().removeRigidBody(manager.getFallRigidBody());
      }
      if (JBulletHingeCSGPhysicsManager.class.isInstance(manager)) {
        if (((JBulletHingeCSGPhysicsManager) manager).getConstraint() != null) {
          getDynamicsWorld().removeConstraint(((JBulletHingeCSGPhysicsManager) manager).getConstraint());
        }
      }
//      if (VehicleCSGPhysicsManager.class.isInstance(manager)) {
//        getDynamicsWorld().removeVehicle(((VehicleCSGPhysicsManager) manager).getVehicle());
//      }

    }
  }

  public void clear() {
    stopPhysicsThread();
    ThreadUtil.wait((int) (msTime * 2));
    for (IPhysicsManager manager : getPhysicsObjects()) {
      if (!WheelCSGPhysicsManager.class.isInstance(manager)
          //&& !VehicleCSGPhysicsManager.class.isInstance(manager)
          ) {
        getDynamicsWorld().removeRigidBody(manager.getFallRigidBody());
      }
      if (JBulletHingeCSGPhysicsManager.class.isInstance(manager)) {
        if (((JBulletHingeCSGPhysicsManager) manager).getConstraint() != null) {
          getDynamicsWorld().removeConstraint(((JBulletHingeCSGPhysicsManager) manager).getConstraint());
        }
      }
//      if (VehicleCSGPhysicsManager.class.isInstance(manager)) {
//        getDynamicsWorld().removeVehicle(((VehicleCSGPhysicsManager) manager).getVehicle());
//      }

    }
    getPhysicsObjects().clear();
    if(ground!=null)
    	ground.clear();
    ground=null;
    
  }

  public int getSimulationSubSteps() {
    return simulationSubSteps;
  }

  public float getDeactivationTime() {
    return deactivationTime;
  }

  public void setSimulationSubSteps(int simpulationSubSteps) {
    this.simulationSubSteps = simpulationSubSteps;
  }

  public float getLin_damping() {
    return lin_damping;
  }

  public float getAng_damping() {
    return ang_damping;
  }

  public float getLinearSleepThreshhold() {
    return linearSleepThreshhold;
  }

  public float getAngularSleepThreshhold() {
    return angularSleepThreshhold;
  }

}
