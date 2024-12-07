package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashMap;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.IRegenerate;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

public class CaDoodleVitamin {
	public static CSG get(String type, ArrayList<Object> args) {
		ArrayList<String> listVitaminSizes = Vitamins.listVitaminSizes(type);
		String name = args.get(0).toString();
		StringParameter size = new StringParameter(type + " Default", listVitaminSizes.get(0), listVitaminSizes);
		if (size.getStrValue().length() == 0)
			size.setStrValue(listVitaminSizes.get(0));
		StringParameter word = new StringParameter(name + "_CaDoodle_Vitamin_Size", size.getStrValue(),
				listVitaminSizes);
		size.setStrValue(word.getStrValue());
		if (args.size() > 1) {
			HashMap<String, Object> object = (HashMap<String, Object>) args.get(1);
			if (!(Boolean) object.get("PreventBomAdd")) {
				VitaminLocation vl = new VitaminLocation(false, name, type, word.getStrValue(), new TransformNR());
				CaDoodleFile.getBoM().addVitamin(vl, true);
			}
		}
		CSG part;
		try {
			part = Vitamins.get(type, word.getStrValue()).setIsHole(true);
			CSGDatabase.saveDatabase();
			return part.setParameter(word).setRegenerate(new IRegenerate() {
				@Override
				public CSG regenerate(CSG previous) {
					ArrayList<Object> ar = new ArrayList<>();
					ar.addAll(args);
					ar.set(0, previous.getName());
 					return CaDoodleVitamin.get(type, ar);
				}
			});
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new RuntimeException("Failed to load vitamin of type " + type);
	}
}
