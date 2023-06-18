package com.neuronrobotics.bowlerstudio.vitamins;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

public class VitaminBomManager {

	private class VitaminElement {
		String name;
		String type;
		String size;
		TransformNR pose;
	}

	Type type = new TypeToken<HashMap<String, ArrayList<VitaminElement>>>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private HashMap<String, ArrayList<VitaminElement>> database = new HashMap<String, ArrayList<VitaminElement>>();
	private String baseURL;

	public VitaminBomManager(String url) throws IOException {
		baseURL = url;
		File baseWorkspaceFile = ScriptingEngine.getRepositoryCloneDirectory(baseURL);
		File bom = new File(baseWorkspaceFile.getAbsolutePath() + "/manufacturing/bom.json");
		if (!bom.exists()) {
			if (!bom.getParentFile().exists()) {
				bom.getParentFile().mkdir();
			}
			try {
				bom.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			save();
		} else {
			String source = FileUtils.readFileToString(bom, "UTF-8");
			database = gson.fromJson(source, type);
		}
	}

	public void set(String name, String type, String size, TransformNR location) {
		VitaminElement newElement = getElement(name);
		boolean toAdd=false;
		if(newElement==null) {
			newElement = new VitaminElement();
			newElement.name = name;
			toAdd=true;
		}
		newElement.pose = location;
		newElement.size = size;
		newElement.type = type;
		String key = type+":"+size;
		if(database.get(key)==null) {
			database.put(key,new ArrayList<VitaminBomManager.VitaminElement>());
		}
		if(toAdd)
			database.get(key).add(newElement);
		
		save();
		// newElement.url=(String) getConfiguration(name).get("source");
	}

	public CSG get(String name) {
		VitaminElement e = getElement(name);
		if (e == null)
			throw new RuntimeException("Vitamine must be defined before it is used: " + name);

		try {
			CSG transformed = Vitamins.get(e.type, e.size).transformed(TransformFactory.nrToCSG(e.pose));
			transformed.setManufacturing(incominng->{return null;});
			transformed.setColor(Color.SILVER);
			return transformed;

		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		return null;
	}
	
	public TransformNR getCoMLocation(String name) {
		VitaminElement e = getElement(name);
		double x=(double) getConfiguration(name).get("massCentroidX");
		double y=(double) getConfiguration(name).get("massCentroidY");

		double z=(double) getConfiguration(name).get("massCentroidZ");

		return e.pose.copy().translateX(x).translateY(y).translateZ(z);
	}
	
	public double getMassKg(String name) {
		return (double) getConfiguration(name).get("massKg");
	}

	public Map<String, Object> getConfiguration(String name) {
		VitaminElement e = getElement(name);
		if (e == null)
			throw new RuntimeException("Vitamin must be defined before it is used: " + name);

		return Vitamins.getConfiguration(e.type, e.size);
	}

	private VitaminElement getElement(String name) {
		for (String testName : database.keySet()) {
			ArrayList<VitaminElement> list=database.get(testName);
			for (VitaminElement el : list) {
				if (el.name.contentEquals(name))
					return el;
			}
		}
		return null;
	}

	public void clear() {
		database.clear();
	}
	private synchronized void saveLocal() {
		String content = gson.toJson(database);
		//String[] source = base.getGitSelfSource();
		String csv ="name,qty,source";
		for(String key:database.keySet()) {
			VitaminElement e  =database.get(key).get(0);
			String size = database.get(key).size()+"";
			String URL = (String) getConfiguration(e.name).get("source");
			csv+=key+","+size+","+URL+"\n";
		}
				
		try {
			ScriptingEngine.commit(baseURL, ScriptingEngine.getBranch(baseURL),"manufacturing/bom.json", content,
					"Save Bill Of Material", true);
			ScriptingEngine.commit(baseURL, ScriptingEngine.getBranch(baseURL),"manufacturing/bom.csv", csv,
					"Save Bill Of Material", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void save() {
		new Thread(()->{
			saveLocal();
		}).start();
	}

}
