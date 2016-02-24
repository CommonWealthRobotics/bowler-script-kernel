package com.neuronrobotics.bowlerstudio.physics;

import com.bulletphysics.dynamics.constraintsolver.HingeConstraint;
import com.bulletphysics.linearmath.Transform;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.application.Platform;
import javafx.scene.paint.Color;

public class HingeCSGPhysicsManager extends CSGPhysicsManager{
	private HingeConstraint constraint=null;
	private IClosedLoopController controller=null;
	private double target=0;
	private float muscleStrength=(float) 1000;
	boolean flagBroken=false;
	public HingeCSGPhysicsManager(CSG baseCSG, Transform pose, double mass) {
		super(baseCSG, pose, mass);
		baseCSG.setColor(Color.YELLOW);
	}
	@Override
	public void update(float timeStep){
		super.update(timeStep);
		if(constraint!=null&&getController()!=null &&!flagBroken){
			double velocity = getController().compute(constraint.getHingeAngle(), getTarget(),timeStep);
			constraint.enableAngularMotor(true, (float) velocity, getMuscleStrength());
			if(constraint.getAppliedImpulse()>muscleStrength){
				baseCSG.setColor(Color.RED);
				flagBroken=true;
			}
		}else if (constraint!=null && flagBroken){
			constraint.enableAngularMotor(false, 0, 0);
		}
	}

	
	public HingeConstraint getConstraint() {
		return constraint;
	}
	public void setConstraint(HingeConstraint constraint) {
		this.constraint = constraint;
	}
	public double getTarget() {
		return target;
	}
	public void setTarget(double target) {
		this.target = target;
	}
	public float getMuscleStrength() {
		return muscleStrength;
	}
	public void setMuscleStrength(float muscleStrength) {
		this.muscleStrength = muscleStrength;
	}
	public void setMuscleStrength(double muscleStrength) {
		setMuscleStrength((float)muscleStrength);
	}
	public IClosedLoopController getController() {
		return controller;
	}
	public void setController(IClosedLoopController controller) {
		this.controller = controller;
	}

}
