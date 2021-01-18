package com.neuronrobotics.sdk.addons.gamepad;

import java.util.HashMap;
import java.util.Set;

import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;

public class PersistantControllerMap {

	public static String getMappedAxisName(String controllerName, String incomingName) {
		Object object = ConfigurationDatabase.getParamMap(controllerName).get(incomingName);
		if (object == null)
			return incomingName;
		return (String) object;
	}

	public static boolean isMapedAxis(String controllerName, String mappedValue) {
		return getMapedAxis(controllerName, mappedValue) != null;
	}

	public static String getMapedAxis(String controllerName, String mappedValue) {
		HashMap<String, Object> paramMap = ConfigurationDatabase.getParamMap(controllerName);
		for (String key : paramMap.keySet()) {
			String string = (String) paramMap.get(key);
			if (string.contentEquals(mappedValue)) {
				return key;
			}
		}
		return null;
	}

	public static void map(String name,String controllerVal, String persistantVal) {
		ConfigurationDatabase.setObject(name, controllerVal, persistantVal);
		ConfigurationDatabase.save();
	}

	public static Set<String> getMappedAxis(String name) {
		// TODO Auto-generated method stub
		return ConfigurationDatabase.getParamMap(name).keySet();
	}

	
}
