package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.IRegenerate;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

public class CaDoodleVitamin {
	public static CSG get(String typencoming, ArrayList<Object> args) {
		String name = args.get(0).toString();

		ArrayList<String>types=new ArrayList<>();
		types.addAll(Vitamins.listVitaminTypes());
		StringParameter typeParam = new StringParameter(name + "_CaDoodle_Vitamin_Type", typencoming,
				types);
		String type=typeParam.getStrValue();
		ArrayList<String> listVitaminSizes = Vitamins.listVitaminSizes(type);
		
		StringParameter size = new StringParameter(type + " Default", listVitaminSizes.get(0), listVitaminSizes);
		
		String strValue = size.getStrValue();
		if(strValue.length() == 0) {
			size.setStrValue(listVitaminSizes.get(0));
		}
		String string = "_CaDoodle_Vitamin_Size";
		StringParameter word = new StringParameter(name + string, strValue,
				listVitaminSizes);
		boolean sizeExists=false;
		for(String s:Vitamins.listVitaminSizes(type)) {
			if(s.contentEquals(word.getStrValue())) {
				sizeExists=true;
				break;
			}
		}
		if(!sizeExists) {
			
			word.setStrValue(strValue);
		}
		size.setStrValue(word.getStrValue());
		if (args.size() > 1) {
			HashMap<String, Object> object = (HashMap<String, Object>) args.get(1);
			if (!(Boolean) object.get("PreventBomAdd")) {
				
				VitaminLocation vl = new VitaminLocation(false, name, type, word.getStrValue(), new TransformNR());
				System.out.println("BoM update "+vl);
				CaDoodleFile.getBoM().addVitamin(vl, true);
			}
		}
		CSG part;
		try {
			System.out.println("Generating Vitamin "+type+" "+word.getStrValue()+" for vitamin named "+name);
			part = Vitamins.get(type, word.getStrValue()).setIsHole(true);
			CSGDatabase.saveDatabase();
			Set<String> params = part.getParameters();

			part.setParameter(word);
			part.setParameter(typeParam);
			params = part.getParameters();

			System.out.println("Parameters on Vitamin: "+name);
			for(String s:params) {
				System.out.println("\t"+s);
			}
			CSG back = part.setRegenerate(new IRegenerate() {
				@Override
				public CSG regenerate(CSG previous) {
					String name2 = previous.getName();
					
					System.out.println("Regenerating source \n\t"+name+" on part \n\t"+name2);
					ArrayList<Object> ar = new ArrayList<>();
					ar.addAll(args);
					ar.set(0, name2);
					
 					return CaDoodleVitamin.get(type, ar);
				}
			});
			
			return back;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new RuntimeException("Failed to load vitamin of type " + type);
	}
}
