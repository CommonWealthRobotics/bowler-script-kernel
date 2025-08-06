package com.neuronrobotics.bowlerstudio.creature;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.Plane;

public class ControllerFeatures {
	@Expose(serialize = true, deserialize = true)
	int servoChannels = 0;
	@Expose(serialize = true, deserialize = true)
	int motorChannels = 0;
	@Expose(serialize = true, deserialize = true)
	int analogSensorChannels = 0;
	@Expose(serialize = true, deserialize = true)
	int cameras = 0;
	@Expose(serialize = true, deserialize = true)
	int digitalSensorChannels = 0;
	@Expose(serialize = true, deserialize = true)
	int inertialSensors = 0;
	@Expose(serialize = true, deserialize = true)
	int distanceSensors = 0;
	@Expose(serialize = true, deserialize = true)
	int pointCloudSensors = 0;
	@Expose(serialize = true, deserialize = true)
	List<Double> voltages ;
	@Expose(serialize = true, deserialize = true)
	double batteryWattHour = 0;
	@Expose(serialize = true, deserialize = true)
	double batteryPeakWatt = 0;
	
	public void add(ControllerFeatures f) {
		if(f==null)
			return;
		servoChannels+=f.servoChannels;
		motorChannels+=f.motorChannels;
		analogSensorChannels+=f.analogSensorChannels;
		cameras+=f.cameras;
		digitalSensorChannels+=f.digitalSensorChannels;
		inertialSensors+=f.inertialSensors;
		distanceSensors+=f.distanceSensors;
		pointCloudSensors+=f.pointCloudSensors;
		ArrayList<Double> toadd=new ArrayList<Double>();
		for(Double v:f.getBatteryVoltage()) {
			boolean found=false;
			for(Double d:getBatteryVoltage()) {
				if(Math.abs(v-d)<Plane.getEPSILON()) {
					found=true;
				}
			}
			if(!found)
				toadd.add(v);
		}
		getBatteryVoltage().addAll(toadd);
		batteryPeakWatt+=f.batteryPeakWatt;
		batteryWattHour+=f.batteryWattHour;
	}
	public void subtract(ControllerFeatures f) {
		if( f== null)
			return;
		servoChannels-=f.servoChannels;
		motorChannels-=f.motorChannels;
		analogSensorChannels-=f.analogSensorChannels;
		cameras-=f.cameras;
		digitalSensorChannels-=f.digitalSensorChannels;
		inertialSensors-=f.inertialSensors;
		distanceSensors-=f.distanceSensors;
		pointCloudSensors-=f.pointCloudSensors;
		batteryPeakWatt-=f.batteryPeakWatt;
		batteryWattHour-=f.batteryWattHour;
	}
	
	public int getServoChannels() {
		return servoChannels;
	}

	public int getMotorChannels() {
		return motorChannels;
	}

	public int getAnalogSensorChannels() {
		return analogSensorChannels;
	}

	public int getCameras() {
		return cameras;
	}

	public int getDigitalSensorChannels() {
		return digitalSensorChannels;
	}

	public int getInertialSensors() {
		return inertialSensors;
	}

	public int getDistanceSensors() {
		return distanceSensors;
	}

	public int getPointCloudSensors() {
		return pointCloudSensors;
	}

	public List<Double> getBatteryVoltage() {
		if(voltages==null)
			voltages=new ArrayList<Double>();
		return voltages;
	}

	public double getBatteryPeakWatts() {
		return batteryPeakWatt;
	}
	public double getBatteryWattHours() {
		return batteryWattHour;
	}
}
