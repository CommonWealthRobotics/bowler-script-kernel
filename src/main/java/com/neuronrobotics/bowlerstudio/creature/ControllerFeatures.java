package com.neuronrobotics.bowlerstudio.creature;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.Plane;

public class ControllerFeatures {
	@Expose(serialize = true, deserialize = true)
	int servoChannels = 0;
	@Expose(serialize = true, deserialize = true)
	int vexV5Motors = 0;
	@Expose(serialize = true, deserialize = true)
	int hiwonderBus = 0;
	@Expose(serialize = true, deserialize = true)
	int dynamixelBus = 0;
	@Expose(serialize = true, deserialize = true)
	int steppers = 0;
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
	List<Double> voltages;
	@Expose(serialize = true, deserialize = true)
	double batteryWattHour = 0;
	@Expose(serialize = true, deserialize = true)
	double batteryPeakWatt = 0;

	public void add(ControllerFeatures f) {
		if (f == null)
			return;
		vexV5Motors = getVexV5Motors() + f.vexV5Motors;
		hiwonderBus = getHiwonderBus() + f.hiwonderBus;
		dynamixelBus = getDynamixelBus() + f.dynamixelBus;
		steppers = getSteppers() + f.steppers;
		servoChannels += f.servoChannels;
		motorChannels += f.motorChannels;
		analogSensorChannels += f.analogSensorChannels;
		cameras += f.cameras;
		digitalSensorChannels += f.digitalSensorChannels;
		inertialSensors += f.inertialSensors;
		distanceSensors += f.distanceSensors;
		pointCloudSensors += f.pointCloudSensors;
		ArrayList<Double> toadd = new ArrayList<Double>();
		for (Double v : f.getBatteryVoltage()) {
			boolean found = false;
			for (Double d : getBatteryVoltage()) {
				if (Math.abs(v - d) < Plane.getEPSILON()) {
					found = true;
				}
			}
			if (!found)
				toadd.add(v);
		}
		getBatteryVoltage().addAll(toadd);
		batteryPeakWatt += f.batteryPeakWatt;
		batteryWattHour += f.batteryWattHour;
	}

	public boolean check(ControllerFeatures f) {
		if (f == null)
			return false;
		if (getVexV5Motors() < f.getVexV5Motors())
			return false;
		if (getHiwonderBus() < f.getHiwonderBus())
			return false;
		if (getDynamixelBus() < f.getDynamixelBus())
			return false;
		if (getSteppers() < f.getSteppers())
			return false;
		if (servoChannels < f.servoChannels)
			return false;
		if (motorChannels < f.motorChannels)
			return false;
		if (analogSensorChannels < f.analogSensorChannels)
			return false;
		if (cameras < f.cameras)
			return false;
		if (digitalSensorChannels < f.digitalSensorChannels)
			return false;
		if (inertialSensors < f.inertialSensors)
			return false;
		if (distanceSensors < f.distanceSensors)
			return false;
		if (pointCloudSensors < f.pointCloudSensors)
			return false;
		for (Double v : f.getBatteryVoltage()) {
			boolean found = false;
			for (Double d : getBatteryVoltage()) {
				if (Math.abs(v - d) < Plane.getEPSILON()) {
					found = true;
				}
			}
			if (!found)
				return false;
			else
				break;
		}
		if (batteryPeakWatt < f.batteryPeakWatt)
			return false;
		if (batteryWattHour < f.batteryWattHour)
			return false;
		return true;
	}

	public void subtract(ControllerFeatures f) {
		if (f == null)
			return;
		vexV5Motors = getVexV5Motors() - f.vexV5Motors;
		hiwonderBus = getHiwonderBus() - f.hiwonderBus;
		dynamixelBus = getDynamixelBus() - f.dynamixelBus;
		steppers = getSteppers() - f.steppers;
		servoChannels -= f.servoChannels;
		motorChannels -= f.motorChannels;
		analogSensorChannels -= f.analogSensorChannels;
		cameras -= f.cameras;
		digitalSensorChannels -= f.digitalSensorChannels;
		inertialSensors -= f.inertialSensors;
		distanceSensors -= f.distanceSensors;
		pointCloudSensors -= f.pointCloudSensors;
		batteryPeakWatt -= f.batteryPeakWatt;
		batteryWattHour -= f.batteryWattHour;
	}

	@Override
	public String toString() {
		return "\n\tServos: " + servoChannels + "\n" + "\tmotorChannels: " + motorChannels + "\n"
				+ "\tanalogSensorChannels: " + analogSensorChannels + "\n" + "\tcameras: " + cameras + "\n"
				+ "\tdigitalSensorChannels: " + digitalSensorChannels + "\n" + "\tinertialSensors: " + inertialSensors
				+ "\n" + "\tdistanceSensors: " + distanceSensors + "\n" + "\tpointCloudSensors: " + pointCloudSensors
				+ "\n" + "\tbatteryPeakWatt: " + batteryPeakWatt + "\n" + "\tbatteryWattHour: " + batteryWattHour + "\n"
				+ "\tVoltages: " + voltages + "\n";
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
		if (voltages == null)
			voltages = new ArrayList<Double>();
		return voltages;
	}

	public double getBatteryPeakWatts() {
		return batteryPeakWatt;
	}

	public double getBatteryWattHours() {
		return batteryWattHour;
	}

	public int getVexV5Motors() {
		return vexV5Motors;
	}

	public int getHiwonderBus() {
		return hiwonderBus;
	}

	public int getDynamixelBus() {
		return dynamixelBus;
	}

	public int getSteppers() {
		return steppers;
	}

}
