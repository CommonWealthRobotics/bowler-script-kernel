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

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

public class VitaminBomManager {
	public static final String MANUFACTURING_BOM_BASE = "manufacturing/bom";
	public static final String MANUFACTURING_BOM_JSON = MANUFACTURING_BOM_BASE + ".json";
	public static final String MANUFACTURING_BOM_CSV = MANUFACTURING_BOM_BASE + ".csv";
	private static boolean saving = false;

//	private class VitaminLocation {
//		String name;
//		String type;
//		String size;
//		TransformNR pose;
//	}

	Type type = new TypeToken<HashMap<String, ArrayList<VitaminLocation>>>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping()
			.excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
	private HashMap<String, ArrayList<VitaminLocation>> database = null;//
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
		} else {
			String source;
			byte[] bytes;
			try {
				bytes = Files.readAllBytes(bom.toPath());
				source = new String(bytes, "UTF-8");
				if(source.length()>0)
					database = gson.fromJson(source, type);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		if(database==null) {
			database=new HashMap<String, ArrayList<VitaminLocation>>();
			save();
		}
	}

//	public void set(String name, String type, String size, TransformNR location) {
//		VitaminLocation newElement = getElement(name);
//		if (newElement == null) {
//			newElement = new VitaminLocation(name,type,size,location);
//		}
//		newElement.setLocation(location);
//		newElement.setSize(size);
//		newElement.setType(type);
//		addVitamin(newElement);
//		// newElement.url=(String) getConfiguration(name).get("source");
//	}

	public void addVitamin(VitaminLocation newElement) {
		String key = newElement.getType() + ":" + newElement.getSize();
		// synchronized (database) {
		if (database.get(key) == null) {
			database.put(key, new ArrayList<VitaminLocation>());
		}
		boolean toAdd=!database.get(key).contains(newElement);
		if (toAdd)
			database.get(key).add(newElement);
		// }
		save();
	}

	public CSG get(String name) {
		VitaminLocation e = getElement(name);
		if (e == null)
			throw new RuntimeException("Vitamin must be defined before it is used: " + name);

		try {
			CSG transformed = Vitamins.get(e.getType(), e.getSize()).transformed(TransformFactory.nrToCSG(e.getLocation()));
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
		VitaminLocation e = getElement(name);
		double x = (double) getConfiguration(name).get("massCentroidX");
		double y = (double) getConfiguration(name).get("massCentroidY");

		double z = (double) getConfiguration(name).get("massCentroidZ");

		return e.getLocation().copy().translateX(x).translateY(y).translateZ(z);
	}

	public double getMassKg(String name) {
		return (double) getConfiguration(name).get("massKg");
	}

	public Map<String, Object> getConfiguration(String name) {
		VitaminLocation e = getElement(name);
		if (e == null)
			throw new RuntimeException("Vitamin must be defined before it is used: " + name);

		return Vitamins.getConfiguration(e.getType(), e.getSize());
	}

	private VitaminLocation getElement(String name) {
		// synchronized (database) {
		for (String testName : database.keySet()) {
			ArrayList<VitaminLocation> list = database.get(testName);
			for (VitaminLocation el : list) {
				if (el.getName().contentEquals(name))
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
		String csv = "name,qty,source,unit price (USD)\n";
		String content = null;

		content = gson.toJson(database);
		// String[] source = base.getGitSelfSource();

		for (String key : database.keySet()) {
			ArrayList<VitaminLocation> list = database.get(key);
			if (list.size() > 0) {
				VitaminLocation e = list.get(0);
				String size = database.get(key).size() + "";
				Map<String, Object> configuration = getConfiguration(e.getName());
				String URL = (String) configuration.get("source");
				if(URL==null) {
					URL="http://commonwealthrobotics.com";
				}
				String price =  configuration.get("price").toString();
				if(price==null) {
					price="0.01";
				}
				csv += key + "," + size + "," + URL +","+price+ "\n";
			} else {
				System.out.println("Failure on " + key);
			}
		}

		try {
			String current = ScriptingEngine.codeFromGit(baseURL, MANUFACTURING_BOM_CSV)[0];
			String currentJ = ScriptingEngine.codeFromGit(baseURL, MANUFACTURING_BOM_JSON)[0];
			if (current.contentEquals(csv) && currentJ.contentEquals(content)) {
				//System.out.println("No update, BoM current");
				saving = false;
				return;
			}
		} catch (Exception e1) {
			// file doesnt exist
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

	public void loadBaseVitamins(MobileBase base) {
		for(VitaminLocation v:base.getVitamins()) {
			addVitamin(v);
		}
		for(DHParameterKinematics k:base.getAllDHChains()) {
			for(int i=0;i<k.getNumberOfLinks();i++) {
				for(VitaminLocation v:k.getVitamins(i)) {
					addVitamin(v);
				}
				MobileBase b = k.getFollowerMobileBase(i);
				if(b!=null) {
					loadBaseVitamins(b);
				}
			}
		}
	}

}
