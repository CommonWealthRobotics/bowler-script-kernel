package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.Transform;

import java.lang.reflect.Type;
import java.nio.file.Path;

public class UserManagedPrintBed implements IgenerateBed {

	private MobileBaseCadManager mobileBaseCadManager;
	private UserManagedPrintBedData data;
	Type type = new TypeToken<UserManagedPrintBedData>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

	public UserManagedPrintBed(File printArrangment, MobileBaseCadManager mobileBaseCadManager) throws IOException {
		this.mobileBaseCadManager = mobileBaseCadManager;
		String source = FileUtils.readFileToString(printArrangment, "UTF-8");
		data = gson.fromJson(source, type);

	}

	@Override
	public ArrayList<CSG> generateCad(DHParameterKinematics dh, int linkIndex) {
		throw new RuntimeException("This is not a cad generator");
	}

	@Override
	public ArrayList<CSG> generateBody(MobileBase base) {
		throw new RuntimeException("This is not a cad generator");

	}

	@Override
	public ArrayList<CSG> arrangeBed(MobileBase base) {
		ArrayList<CSG> parts = mobileBaseCadManager.getAllCad();
		HashMap<Integer, ArrayList<CSG>> beds = new HashMap<>();
		for (CSG bit : parts) {
			String name = bit.getName();
			int index = bit.getPrintBedIndex();
			bit = bit.prepForManufacturing();
			if (bit != null) {
				if (beds.get(index) == null) {
					beds.put(index, new ArrayList<CSG>());
				}
				TransformNR location = data.locations.get(name);
				if (location != null) {
					Transform csfMove = TransformFactory.nrToCSG(location);
					bit = bit.transformed(csfMove);
				}
				beds.get(index).add(bit);
			}
		}
		ArrayList<CSG> bedsOutputs = new ArrayList<CSG>();
		for (Integer i : beds.keySet()) {
			String name = "Print-Bed-" + i;
			ArrayList<CSG> bedComps = beds.get(i);
			CSG bed = null;
			for (CSG p : bedComps) {
				if (bed == null)
					bed = p;
				else {
					bed = bed.dumbUnion(p);
				}
			}
			if (bed != null)
				bed.setName(name);
			else {
				bed = new Cube().toCSG();
				bed.setManufacturing(incoming -> null);
			}
			bedsOutputs.add(bed);
		}
		return bedsOutputs;
	}

}
