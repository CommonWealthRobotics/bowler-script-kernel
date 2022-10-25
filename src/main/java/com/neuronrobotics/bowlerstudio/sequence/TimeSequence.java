package com.neuronrobotics.bowlerstudio.sequence;

import java.lang.reflect.Type;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.sdk.addons.kinematics.AbstractKinematicsNR;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.common.DeviceManager;

public class TimeSequence {
	// Create the type, this tells GSON what datatypes to instantiate when parsing
	// and saving the json
	private static Type TT_mapStringString = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
	}.getType();
	// chreat the gson object, this is the parsing factory
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

	public static HashMap<String, AbstractKinematicsNR> getDevices() {
		HashMap<String, AbstractKinematicsNR> map = new HashMap<>();
		for (String dev : DeviceManager.listConnectedDevice()) {
			Object specificDevice = DeviceManager.getSpecificDevice(dev);
			if (MobileBase.class.isInstance(specificDevice)) {
				MobileBase specificDevice2 = (MobileBase) specificDevice;
				loadMobileBase(map, specificDevice2, 0);
			}
		}
		return map;
	}

	private static void loadMobileBase(HashMap<String, AbstractKinematicsNR> map, MobileBase specificDevice2,
			int depth) {
		map.put("MobileBase:" + specificDevice2.getScriptingName() + " " + depth, specificDevice2);
		for (DHParameterKinematics pg : specificDevice2.getAllParallelGroups()) {
			map.put("ParallelGroup:" + pg.getScriptingName() + " " + depth, pg);
		}
		for (DHParameterKinematics kin : specificDevice2.getAllDHChains()) {
			if (specificDevice2.getParallelGroup(kin) == null)
				map.put("Appendage:" + kin.getScriptingName() + " " + depth, kin);
			for (int i = 0; i < kin.getNumberOfLinks(); i++) {
				MobileBase follower = kin.getSlaveMobileBase(i);
				if (follower != null) {
					loadMobileBase(map, follower, depth + 1);
				}
			}
		}
	}

	public static void execute(String content) throws Exception {
		// perfoem the GSON parse
		HashMap<String, HashMap<String, Object>> database = gson.fromJson(content, TT_mapStringString);

	}

}
