package com.neuronrobotics.bowlerstudio.physics;

import java.util.ArrayList;

import com.bulletphysics.dynamics.vehicle.DefaultVehicleRaycaster;
import com.bulletphysics.dynamics.vehicle.RaycastVehicle;
import com.bulletphysics.dynamics.vehicle.VehicleRaycaster;
import com.bulletphysics.dynamics.vehicle.VehicleTuning;
import com.bulletphysics.linearmath.Transform;
//import com.neuronrobotics.bowlerstudio.BowlerStudio;

import eu.mihosoft.vrl.v3d.CSG;

public class VehicleCSGPhysicsManager extends CSGPhysicsManager {

  ////////////////////////////////////////////////////////////////////////////


  private VehicleTuning tuning = new VehicleTuning();
  public VehicleRaycaster vehicleRayCaster;
  private RaycastVehicle vehicle;

  public VehicleCSGPhysicsManager(ArrayList<CSG> baseCSG, Transform pose, double mass,
      boolean adjustCenter,
      PhysicsCore core) {
    super(baseCSG, pose, mass, adjustCenter, core);

    vehicleRayCaster = new DefaultVehicleRaycaster(core.getDynamicsWorld());
    setVehicle(new RaycastVehicle(getTuning(), getFallRigidBody(), vehicleRayCaster));
  }

  @Override
  public void update(float timeStep) {
    getFallRigidBody().getMotionState().getWorldTransform(getUpdateTransform());
    if (getUpdateManager() != null) {
      try {
        getUpdateManager().update(timeStep);
      } catch (Exception e) {
        //BowlerStudio.printStackTrace(e);
        throw e;
      }
    }
    vehicle.updateVehicle(timeStep);

  }


  public RaycastVehicle getVehicle() {
    return vehicle;
  }


  public void setVehicle(RaycastVehicle vehicle) {
    this.vehicle = vehicle;
  }

  public VehicleTuning getTuning() {
    return tuning;
  }

  public void setTuning(VehicleTuning tuning) {
    this.tuning = tuning;
  }


}
