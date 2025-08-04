package com.neuronrobotics.bowlerstudio.creature;

import com.google.gson.annotations.Expose;

public class ControllerOption {
	@Expose(serialize = true, deserialize = true)
	String type;
	@Expose(serialize = true, deserialize = true)
	String cadGit;
	@Expose(serialize = true, deserialize = true)
	String cadFile;
	@Expose(serialize = true, deserialize = true)
	String linkLoaderGit;
	@Expose(serialize = true, deserialize = true)
	String linkLoaderFile;
	@Expose(serialize = true, deserialize = true)
	int servoChannels;
	@Expose(serialize = true, deserialize = true)
	int motorChannels;
	@Expose(serialize = true, deserialize = true)
	int analogSensorChannels;
	@Expose(serialize = true, deserialize = true)
	int cameras;
	@Expose(serialize = true, deserialize = true)
	int digitalSensorChannels;
	@Expose(serialize = true, deserialize = true)
	int inertialSensors;
	@Expose(serialize = true, deserialize = true)
	int distanceSensors;
	@Expose(serialize = true, deserialize = true)
	int pointCloudSensors;
	
}
