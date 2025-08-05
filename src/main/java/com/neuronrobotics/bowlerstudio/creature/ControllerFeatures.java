package com.neuronrobotics.bowlerstudio.creature;

import java.util.List;

import com.google.gson.annotations.Expose;

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
		return voltages;
	}

	public double getBatteryPeakWatts() {
		return batteryPeakWatt;
	}
	public double getBatteryWattHours() {
		return batteryWattHour;
	}
}
