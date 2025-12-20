package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import eu.mihosoft.vrl.v3d.parametrics.IRegenerate;
import eu.mihosoft.vrl.v3d.parametrics.Parameter;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

public class CaDoodleVitamin {
	private CSGDatabaseInstance instance;
	public CaDoodleVitamin(CSGDatabaseInstance myinstance) {
		instance=myinstance;
	}
	public CSG get(String typencoming, ArrayList<Object> args) {
		String name = args.get(0).toString();
		ArrayList<String>types=new ArrayList<>();
		types.addAll(Vitamins.listVitaminTypes());
		StringParameter typeParam = new StringParameter(instance,name + "_CaDoodle_Vitamin_Type", typencoming,types);
		String type=typeParam.getStrValue();
		ArrayList<String> listVitaminSizes = Vitamins.listVitaminSizes(type);
		try {
			return get( type, listVitaminSizes.get(0),  args);
		}catch(Exception ex) {
			com.neuronrobotics.sdk.common.Log.error(ex);;
			throw ex;
		}
	}
	public boolean isVitamin(CSG c) {
		for(String s:instance.getParameters(c)) {
			//Log.debug("Checking "+s);
			if(s.contains("_CaDoodle_Vitamin_") && s.contains(c.getName())) {
				return true;
			}
		}
		return false;
	}
	public  CSG get(String typencoming,String defaultValue, ArrayList<Object> args) {
		String name = args.get(0).toString();

		ArrayList<String>types=new ArrayList<>();
		types.addAll(Vitamins.listVitaminTypes());
		StringParameter typeParam = new StringParameter(instance,name + "_CaDoodle_Vitamin_Type", typencoming,
				types);
		String type=typeParam.getStrValue();
		ArrayList<String> listVitaminSizes = Vitamins.listVitaminSizes(type);
		
		StringParameter size = new StringParameter(instance,type + " Default", defaultValue, listVitaminSizes);
		
		String strValue = size.getStrValue();
		if(strValue.length() == 0) {
			size.setStrValue(listVitaminSizes.get(0));
		}
		String string = "_CaDoodle_Vitamin_Size";
		StringParameter word = new StringParameter(instance,name + string, strValue,
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
				
//				VitaminLocation vl = new VitaminLocation(false, name, type, word.getStrValue(), new TransformNR());
//				//com.neuronrobotics.sdk.common.Log.debug("BoM update "+vl);
//				CaDoodleFile.getBoM().addVitamin(vl, true);
			}
		}
		CSG part;
		try {
//			com.neuronrobotics.sdk.common.Log.debug("Generating Vitamin "+type+" "+word.getStrValue()+" for vitamin named "+name);
			part = Vitamins.get(instance,type, word.getStrValue()).setIsHole(true);
			instance.saveDatabase();
			Set<String> params = part.getParameters(instance);

			part.setParameter(instance,word);
			part.setParameter(instance,typeParam);
			params = part.getParameters(instance);
			part.setName(name);
//			com.neuronrobotics.sdk.common.Log.debug("Parameters on Vitamin: "+name);
//			for(String s:params) {
//				com.neuronrobotics.sdk.common.Log.debug("\t"+s);
//			}
			CSG back = part.setRegenerate(new IRegenerate() {
				@Override
				public CSG regenerate(CSG previous) {
					Optional<Object> pv = previous.getStorage().getValue("PreviousName");
					String name2 = null;
					if(pv.isPresent())
						name2=pv.get().toString();
					else
						name2=name;
					//com.neuronrobotics.sdk.common.Log.debug("Regenerating source \n\t"+name+" on part \n\t"+name2);
					ArrayList<Object> ar = new ArrayList<>();
					HashMap<String, Object> object = (HashMap<String, Object>) args.get(1);
					HashMap<String, Object> objectNew = new HashMap<String, Object>();
					String name3 = previous.getName();
					objectNew.put("name", name3);
					objectNew.put("PreventBomAdd", object.get("PreventBomAdd"));
					ar.add( name3);
					ar.add( objectNew);
					Parameter s = instance.get(name2+"_CaDoodle_Vitamin_Size");
					Parameter t = instance.get(name2+"_CaDoodle_Vitamin_Type");
					if(t==null) {
						com.neuronrobotics.sdk.common.Log.debug(" Error, type is null, previous "+name2+" has no parameters somehow??");
					}
 					return get(t.getStrValue(),s.getStrValue(), ar);
				}
			});
			//back.getStorage().set("PreviousName", name);
			//back.setIsAlwaysShow(true);
			return back;
		} catch (Exception e) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		throw new RuntimeException("Failed to load vitamin of type " + type);
	}
}
