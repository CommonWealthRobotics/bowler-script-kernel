package com.neuronrobotics.sdk.addons.gamepad;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;

public class PersistantControllerMap {
	
	public static List<String> getDefaultMaps() {
		return Arrays.asList("l-joy-up-down", "l-joy-left-right", "r-joy-up-down", "r-joy-left-right", "l-trig-button",
				"r-trig-button", "x-mode", "y-mode", "a-mode", "b-mode", "start", "select", "analog-trig",
				"arrow-up-down",
				"arrow-left-right");
	}
	
	public static boolean areAllAxisMapped(String controllerName) {
		for(String axis:getDefaultMaps()) {
			if(!isMapedAxis(controllerName,axis)) {
				return false;
			}
		}
		return true;
	}
	public static void clearMapping(String controllerName) {
		ConfigurationDatabase.clear(controllerName);
		
	}
	public static String getMappedAxisName(String controllerName, String incomingName) {
		Object object = ConfigurationDatabase.getObject(controllerName,incomingName,incomingName);
		if (object == null)
			return incomingName;
		return (String) object;
	}

	public static boolean isMapedAxis(String controllerName, String mappedValue) {
		return getHardwareAxisFromMappedValue(controllerName, mappedValue) != null;
	}
	

	public static String getHardwareAxisFromMappedValue(String controllerName, String mappedValue) {
		return ConfigurationDatabase.getKeyFromValue(controllerName, mappedValue);
	}

	public static void map(String name,String controllerVal, String persistantVal) {
		ConfigurationDatabase.setObject(name, controllerVal, persistantVal);
		ConfigurationDatabase.save();
	}

	public static Set<String> getMappedAxis(String name) {
		return ConfigurationDatabase.keySet(name);
	}

	
}
