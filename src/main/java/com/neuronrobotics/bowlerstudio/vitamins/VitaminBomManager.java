package com.neuronrobotics.bowlerstudio.vitamins;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

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
	public static final String MANUFACTURING_BOM_BASE = "manufacturing/bom";
	public static final String MANUFACTURING_BOM_JSON = MANUFACTURING_BOM_BASE + ".json";
	public static final String MANUFACTURING_BOM_CSV = MANUFACTURING_BOM_BASE + ".csv";
	private static boolean saving = false;

	private class VitaminElement {
		String name;
		String type;
		String size;
		TransformNR pose;
	}

	Type type = new TypeToken<HashMap<String, ArrayList<VitaminElement>>>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private HashMap<String, ArrayList<VitaminElement>> database = null;//
	private String baseURL;

	public VitaminBomManager(String url) throws IOException {
		baseURL = url;
		File baseWorkspaceFile = ScriptingEngine.getRepositoryCloneDirectory(baseURL);
		File bom = new File(baseWorkspaceFile.getAbsolutePath() + "/" + MANUFACTURING_BOM_JSON);
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
			String source;
			byte[] bytes;
			try {
				bytes = Files.readAllBytes(bom.toPath());
				source = new String(bytes, "UTF-8");
				database = gson.fromJson(source, type);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		if(database==null) {
			database=new HashMap<String, ArrayList<VitaminElement>>();
		}
	}

	public void set(String name, String type, String size, TransformNR location) {
		VitaminElement newElement = getElement(name);
		boolean toAdd = false;
		if (newElement == null) {
			newElement = new VitaminElement();
			newElement.name = name;
			toAdd = true;
		}
		newElement.pose = location;
		newElement.size = size;
		newElement.type = type;
		String key = type + ":" + size;
		// synchronized (database) {
		if (database.get(key) == null) {
			database.put(key, new ArrayList<VitaminBomManager.VitaminElement>());
		}
		if (toAdd)
			database.get(key).add(newElement);
		// }
		save();
		// newElement.url=(String) getConfiguration(name).get("source");
	}

	public CSG get(String name) {
		VitaminElement e = getElement(name);
		if (e == null)
			throw new RuntimeException("Vitamin must be defined before it is used: " + name);

		try {
			CSG transformed = Vitamins.get(e.type, e.size).transformed(TransformFactory.nrToCSG(e.pose));
			transformed.setManufacturing(incominng -> {
				return null;
			});
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
		double x = (double) getConfiguration(name).get("massCentroidX");
		double y = (double) getConfiguration(name).get("massCentroidY");

		double z = (double) getConfiguration(name).get("massCentroidZ");

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
		// synchronized (database) {
		for (String testName : database.keySet()) {
			ArrayList<VitaminElement> list = database.get(testName);
			for (VitaminElement el : list) {
				if (el.name.contentEquals(name))
					return el;
			}
		}
		// }
		return null;
	}

	public void clear() {
		// synchronized (database) {
		database.clear();
		// }
	}

	private void saveLocal() {
		saving = true;
		String csv = "name,qty,source\n";
		String content = null;

		content = gson.toJson(database);
		// String[] source = base.getGitSelfSource();

		for (String key : database.keySet()) {
			ArrayList<VitaminElement> list = database.get(key);
			if (list.size() > 0) {
				VitaminElement e = list.get(0);
				String size = database.get(key).size() + "";
				String URL = (String) getConfiguration(e.name).get("source");
				csv += key + "," + size + "," + URL + "\n";
			} else {
				System.out.println("Failure on " + key);
			}
		}

		try {
			String current = ScriptingEngine.codeFromGit(baseURL, MANUFACTURING_BOM_CSV)[0];
			String currentJ = ScriptingEngine.codeFromGit(baseURL, MANUFACTURING_BOM_JSON)[0];
			if (current.contentEquals(csv) && currentJ.contentEquals(content)) {
				System.out.println("No update, BoM current");
				saving = false;
				return;
			}
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		try {
			write(MANUFACTURING_BOM_JSON, content);
			write(MANUFACTURING_BOM_CSV, csv);
//			ScriptingEngine.commit(baseURL, ScriptingEngine.getBranch(baseURL), MANUFACTURING_BOM_JSON, content,
//					"Save Bill Of Material", true);
//			ScriptingEngine.commit(baseURL, ScriptingEngine.getBranch(baseURL), MANUFACTURING_BOM_CSV, csv,
//					"Save Bill Of Material", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		saving = false;

	}

	private void write(String file, String content)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		File f = ScriptingEngine.fileFromGit(baseURL, file);
		if (!f.getParentFile().exists())
			f.getParentFile().mkdir();
		if (!f.exists()) {
			f.createNewFile();
		}
		BufferedWriter writer = new BufferedWriter(new FileWriter(f.getAbsolutePath()));
		writer.write(content);
		writer.close();
	}

	public void save() {
		saveLocal();
	}

}
