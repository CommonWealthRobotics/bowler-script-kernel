package com.neuronrobotics.bowlerstudio.sequence;

import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.IOnInterpolationDone;
import com.neuronrobotics.sdk.addons.kinematics.InterpolationMoveState;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.pid.InterpolationType;

public class SequenceEvent implements IOnInterpolationDone{
	private TransformNR pose = new TransformNR();
	private InterpolationType mode = InterpolationType.LINEAR;
	
	private double TRAPEZOIDAL_time=0;
	private double BEZIER_P0=0;
	private double BEZIER_P1=0;
	
	private int msDuration=1;
	
	public void execute(DHParameterKinematics kin) {
		double seconds = ((double)msDuration)/1000.0;
		if(mode==InterpolationType.BEZIER)
			kin.asyncInterpolatedMove(pose, seconds, mode,this, BEZIER_P0,BEZIER_P1);
		else if(mode==InterpolationType.TRAPEZOIDAL)
			kin.asyncInterpolatedMove(pose, seconds, mode,this,  TRAPEZOIDAL_time);
		else
			kin.asyncInterpolatedMove(pose, seconds, mode,this);		

	}
	
	public TransformNR getPose() {
		return pose;
	}

	public void setPose(TransformNR pose) {
		this.pose = pose;
	}

	public InterpolationType getType() {
		return mode;
	}

	public void setType(InterpolationType mode) {
		this.mode = mode;
	}

	public double getTRAPEZOIDAL_time() {
		return TRAPEZOIDAL_time;
	}

	public void setTRAPEZOIDAL_time(double tRAPEZOIDAL_time) {
		TRAPEZOIDAL_time = tRAPEZOIDAL_time;
	}

	public double getBEZIER_P0() {
		return BEZIER_P0;
	}

	public void setBEZIER_P0(double bEZIER_P0) {
		BEZIER_P0 = bEZIER_P0;
	}

	public double getBEZIER_P1() {
		return BEZIER_P1;
	}

	public void setBEZIER_P1(double bEZIER_P1) {
		BEZIER_P1 = bEZIER_P1;
	}

	public int getMsDuration() {
		return msDuration;
	}

	public void setMsDuration(int msDuration) {
		this.msDuration = msDuration;
	}

	@Override
	public void done(InterpolationMoveState state) {
		// TODO Auto-generated method stub
		
	}
}
