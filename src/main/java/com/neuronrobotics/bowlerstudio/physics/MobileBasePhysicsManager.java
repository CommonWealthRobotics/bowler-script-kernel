package com.neuronrobotics.bowlerstudio.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;

import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.constraintsolver.HingeConstraint;
import com.bulletphysics.linearmath.Transform;
import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.ILinkListener;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.imu.IMU;
import com.neuronrobotics.sdk.addons.kinematics.imu.IMUUpdate;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.IClosedLoopController;
import com.neuronrobotics.sdk.pid.PIDLimitEvent;
import com.neuronrobotics.sdk.util.ThreadUtil;

import Jama.Matrix;

import eu.mihosoft.vrl.v3d.CSG;
//import eu.mihosoft.vrl.v3d.ext.quickhull3d.HullUtil;
//import eu.mihosoft.vvecmath.Vector3d;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

public class MobileBasePhysicsManager {

	public static final float PhysicsGravityScalar = 6;
	private HashMap<LinkConfiguration, ArrayList<CSG>> simplecad;
	private float lift = 0;
	private ArrayList<ILinkListener> linkListeners = new ArrayList<>();
	public static final float LIFT_EPS = (float) Math.toRadians(0.1);

	private IPhysicsUpdate getUpdater(RigidBody body, IMU base) {
		return new IPhysicsUpdate() {
			Vector3f oldavelocity = new Vector3f(0f, 0f, 0f);
			Vector3f oldvelocity = new Vector3f(0f, 0f, 0f);
			Vector3f gravity = new Vector3f();
			private Quat4f orentation = new Quat4f();
			Transform gravTrans = new Transform();
			Transform orentTrans = new Transform();
			Vector3f avelocity = new Vector3f();
			Vector3f velocity = new Vector3f();

			@Override
			public void update(float timeStep) {

				body.getAngularVelocity(avelocity);
				body.getLinearVelocity(velocity);

				body.getGravity(gravity);
				body.getOrientation(orentation);

				TransformFactory.nrToBullet(new TransformNR(gravity.x, gravity.y, gravity.z, new RotationNR()),
						gravTrans);
				TransformFactory.nrToBullet(
						new TransformNR(0, 0, 0, orentation.w, orentation.x, orentation.y, orentation.z), orentTrans);
				orentTrans.inverse();
				orentTrans.mul(gravTrans);

				// A=DeltaV / DeltaT
				Double rotxAcceleration = (double) ((oldavelocity.x - avelocity.x) / timeStep);
				Double rotyAcceleration = (double) ((oldavelocity.y - avelocity.y) / timeStep);
				Double rotzAcceleration = (double) ((oldavelocity.z - avelocity.z) / timeStep);
				Double xAcceleration = (double) (((oldvelocity.x - velocity.x) / timeStep) / PhysicsGravityScalar)
						+ (orentTrans.origin.x / PhysicsGravityScalar);
				Double yAcceleration = (double) (((oldvelocity.y - velocity.y) / timeStep) / PhysicsGravityScalar)
						+ (orentTrans.origin.y / PhysicsGravityScalar);
				Double zAcceleration = (double) (((oldvelocity.z - velocity.z) / timeStep) / PhysicsGravityScalar)
						+ (orentTrans.origin.z / PhysicsGravityScalar);
				// tell the virtual IMU the system updated
				base.setVirtualState(new IMUUpdate(xAcceleration, yAcceleration, zAcceleration, rotxAcceleration,
						rotyAcceleration, rotzAcceleration,base.currentTimeMillis()));
				// update the old variables
				oldavelocity.set(avelocity);
				oldvelocity.set(velocity);

			}
		};
	}

	public MobileBasePhysicsManager(MobileBase base, ArrayList<CSG> baseCad,
			HashMap<LinkConfiguration, ArrayList<CSG>> simplecad) {
		this(base, baseCad, simplecad, PhysicsEngine.get());
	}

	public MobileBasePhysicsManager(MobileBase base, ArrayList<CSG> baseCad,
			HashMap<LinkConfiguration, ArrayList<CSG>> simplecad, PhysicsCore core) {
		this.simplecad = simplecad;
		double minz = 0;
		double Maxz = 0;
		for (DHParameterKinematics dh : base.getAllDHChains()) {
			if (dh.getCurrentTaskSpaceTransform().getZ() < minz) {
				minz = dh.getCurrentTaskSpaceTransform().getZ();
			}
		}
		for (CSG c : baseCad) {
			if (c.getMinZ() < minz) {
				minz = c.getMinZ();
			}
			if (c.getMaxZ() > Maxz) {
				Maxz = c.getMaxZ();
			}
		}
		// System.out.println("Minimum z = "+minz);
		Transform start = new Transform();
		base.setFiducialToGlobalTransform(new TransformNR());
		// TransformNR globe= base.getFiducialToGlobalTransform();

		TransformFactory.nrToBullet(base.getFiducialToGlobalTransform(), start);
		start.origin.z = (float) (start.origin.z - minz + lift);
		BowlerKernel.runLater(new Runnable() {
			@Override
			public void run() {
				TransformFactory.bulletToAffine(baseCad.get(0).getManipulator(), start);
			}
		});
		CSG collisionBod;
//		ArrayList<Vector3d> points = new ArrayList<Vector3d>();
//		try {
//			for (DHParameterKinematics leg : base.getAllDHChains()) {
//				TransformNR limbRoot = leg.getRobotToFiducialTransform();
//				points.add(Vector3d.xyz(limbRoot.getX(), limbRoot.getY(), limbRoot.getZ()));
//				points.add(Vector3d.xyz(limbRoot.getX(), limbRoot.getY(), Maxz));
//			}
//
//			collisionBod = HullUtil.hull(points);
//		} catch (Exception ex) {
			collisionBod = CSG.hullAll(MobileBaseCadManager.getBaseCad(base));
//		}

		CSGPhysicsManager baseManager = new CSGPhysicsManager((List<CSG>) Arrays.asList(collisionBod), start,
				base.getMassKg(), false, core);
		baseManager.setBaseCSG(baseCad);

		RigidBody body = baseManager.getFallRigidBody();
		baseManager.setUpdateManager(getUpdater(body, base.getImu()));
		body.setWorldTransform(start);
		core.getDynamicsWorld().setGravity(new Vector3f(0, 0, (float) -98 * PhysicsGravityScalar));
		core.add(baseManager);
		for (int j = 0; j < base.getAllDHChains().size(); j++) {
			DHParameterKinematics dh = base.getAllDHChains().get(j);
			TransformNR limbRoot = dh.getRobotToFiducialTransform();
			RigidBody lastLink = body;
			Matrix previousStep = null;
			// ensure the dh-cache chain is computed and recent
			dh.forwardKinematics(dh.getCurrentJointSpaceVector());
			ArrayList<TransformNR> cachedStart = dh.getDhChain().getCachedChain();
			List<TransformNR> cached = cachedStart.stream().collect(Collectors.toList());
			for (int i = 0; i < dh.getNumberOfLinks() && i < cached.size(); i++) {
				// Hardware to engineering units configuration
				LinkConfiguration conf = dh.getLinkConfiguration(i);
				// DH parameters
				DHLink l = dh.getDhChain().getLinks().get(i);
				ArrayList<CSG> thisLinkCad = simplecad.get(conf);
				if (thisLinkCad != null && thisLinkCad.size() > 0) {

					// use the DH parameters to calculate the offset of the link
					// at 0 degrees
					Matrix step;
					if (conf.isPrismatic()) {
						step = l.DhStepInversePrismatic(0);
					} else {
						step = l.DhStepInverseRotory(Math.toRadians(0));
					}
					// correct jog for singularity.

					// Engineering units to kinematics link (limits and hardware
					// type abstraction)
					AbstractLink abstractLink = dh.getAbstractLink(i);

					// Transform used by the UI to render the location of the
					// object
					Affine manipulator = new Affine();// make a new affine for the physics engine to service. the
														// manipulaters in the CSG will not conflict for resources here
					// The DH chain calculated the starting location of the link
					// in its current configuration
					TransformNR localLink;
					if (i > 0)
						localLink = cached.get(i - 1);
					else
						localLink = limbRoot.copy();
					// Lift it in the air so nothing is below the ground to
					// start.
					localLink.translateZ(lift);
					// Bullet engine transform object
					Transform linkLoc = new Transform();
					TransformFactory.nrToBullet(localLink, linkLoc);
					linkLoc.origin.z = (float) (linkLoc.origin.z - minz + lift);

					// Set the manipulator to the location from the kinematics,
					// needs to be in UI thread to touch manipulator
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							TransformFactory.nrToAffine(localLink, manipulator);
						}
					});
					ThreadUtil.wait(16);

					double mass = conf.getMassKg();
					ArrayList<CSG> outCad = new ArrayList<>();
					ArrayList<CSG> collisions = new ArrayList<>();
					for (int x = 0; x < thisLinkCad.size(); x++) {
						Color color = thisLinkCad.get(x).getColor();
						CSG cad = thisLinkCad.get(x);
						CSG tmp = CSGPhysicsManager.getBoundingBox(cad);
						// tmp=tmp.difference(tmp.toXMax())
						outCad.add(cad.transformed(TransformFactory.nrToCSG(new TransformNR(step).inverse())));
						outCad.get(x).setManipulator(manipulator);
						outCad.get(x).setColor(color);

						collisions.add(tmp.transformed(TransformFactory.nrToCSG(new TransformNR(step).inverse())));
						collisions.get(x).setManipulator(manipulator);
						collisions.get(x).setColor(color);

					}

					// Build a hinge based on the link and mass
					// was outCad
					HingeCSGPhysicsManager hingePhysicsManager = new HingeCSGPhysicsManager(collisions, linkLoc, mass,
							core);
					hingePhysicsManager.setBaseCSG(outCad);
					HingeCSGPhysicsManager.setMuscleStrength(1000000);

					RigidBody linkSection = hingePhysicsManager.getFallRigidBody();

					hingePhysicsManager.setUpdateManager(getUpdater(linkSection, abstractLink.getImu()));
					// // Setup some damping on the m_bodies
					// linkSection.setDamping(0.5f, 08.5f);
					// linkSection.setDeactivationTime(0.8f);
					// linkSection.setSleepingThresholds(1.6f, 2.5f);
					linkSection.setActivationState(
							com.bulletphysics.collision.dispatch.CollisionObject.DISABLE_DEACTIVATION);

					// linkSection.set

					HingeConstraint joint6DOF;
					Transform localA = new Transform();
					Transform localB = new Transform();
					localA.setIdentity();
					localB.setIdentity();

					// set up the center of mass offset from the centroid of the
					// links
					if (i == 0) {

						TransformFactory.nrToBullet(dh.forwardOffset(new TransformNR()), localA);
					} else {
						TransformFactory.nrToBullet(new TransformNR(previousStep.inverse()), localA);
					}
					// set the link constraint based on DH parameters
					TransformFactory.nrToBullet(new TransformNR(), localB);
					previousStep = step;
					// build the hinge constraint
					joint6DOF = new HingeConstraint(lastLink, linkSection, localA, localB);
					joint6DOF.setLimit(-(float) Math.toRadians(abstractLink.getMinEngineeringUnits()),
							-(float) Math.toRadians(abstractLink.getMaxEngineeringUnits()));

					lastLink = linkSection;

					hingePhysicsManager.setConstraint(joint6DOF);

					if (!conf.isPassive()) {
						ILinkListener ll = new ILinkListener() {
							@Override
							public void onLinkPositionUpdate(AbstractLink source, double engineeringUnitsValue) {
								// System.out.println("
								// value="+engineeringUnitsValue);
								hingePhysicsManager.setTarget(Math.toRadians(-engineeringUnitsValue));

								// joint6DOF.setLimit( (float)
								// (Math.toRadians(-engineeringUnitsValue )-
								// LIFT_EPS),
								// (float) (Math.toRadians(-engineeringUnitsValue )+
								// LIFT_EPS));
							}

							@Override
							public void onLinkLimit(AbstractLink source, PIDLimitEvent event) {
								// println event
							}
						};
						hingePhysicsManager.setController(new IClosedLoopController() {

							@Override
							public double compute(double currentState, double target, double seconds) {
								double error = target - currentState;
								return (error / seconds) * (seconds * 10);
							}
						});
						abstractLink.addLinkListener(ll);
						linkListeners.add(ll);
					}

					abstractLink.getCurrentPosition();
					core.add(hingePhysicsManager);
					linkSection.setWorldTransform(linkLoc);
				}
			}
		}
	}

	public void clear() {
		if(simplecad!=null) {
			simplecad.clear();
		}
		linkListeners.clear();
	}
}
