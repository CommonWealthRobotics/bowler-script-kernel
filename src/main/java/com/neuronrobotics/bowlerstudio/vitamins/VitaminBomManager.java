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
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

public class VitaminBomManager {
	private static final String MANUFACTURING_BOM_BASE = "manufacturing/BillOfMaterials";
	private static final String MANUFACTURING_BOM_JSON = getManufacturingBomBase() + ".json";
	private static final String MANUFACTURING_BOM_CSV = getManufacturingBomBase() + ".csv";
	private static boolean saving = false;

	Type type = new TypeToken<HashMap<String, ArrayList<VitaminLocation>>>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting()
			.create();
	private HashMap<String, ArrayList<VitaminLocation>> database = null;//
	private String baseURL = null;
	private File baseWorkspaceFile;

	public VitaminBomManager(String url) throws IOException {
		this(ScriptingEngine.getRepositoryCloneDirectory(url));
		baseURL = url;
	
	}

	public VitaminBomManager(File parentFile) {
		baseWorkspaceFile = parentFile;
		File bom = getBomFile();
		if (!bom.exists()) {
			if (!bom.getParentFile().exists()) {
				bom.getParentFile().mkdir();
			}
			try {
				bom.createNewFile();
			} catch (IOException e) {
				// Auto-generated catch block
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		} else {
			String source;
			byte[] bytes;
			try {
				bytes = Files.readAllBytes(bom.toPath());
				source = new String(bytes, "UTF-8");
				if (source.length() > 0)
					database = gson.fromJson(source, type);
			} catch (Exception ex) {
				com.neuronrobotics.sdk.common.Log.error(ex);;
			}
		}
		if (database == null) {
			database = new HashMap<String, ArrayList<VitaminLocation>>();
			save();
		}
	}
	public File getBomCsv() {
		return new File(baseWorkspaceFile.getAbsolutePath() + "/" + getManufacturingBomCsv());
	}
	public File getBomFile() {
		return new File(baseWorkspaceFile.getAbsolutePath() + "/" + getManufacturingBomJson());
	}
	public VitaminLocation getByName(String name) {
		for(String keys:database.keySet()) {
			ArrayList<VitaminLocation> arrayList = database.get(keys);
			for (int i = 0; i < arrayList.size(); i++) {
				VitaminLocation vl = arrayList.get(i);
				if(vl.getName().contentEquals(name)) {
					return vl;
				}
			}
		}
		return null;
	}
	public VitaminBomManager addVitamin(VitaminLocation newElement) {
		return addVitamin(newElement,true);
	}
	public VitaminBomManager addVitamin(VitaminLocation newElement, boolean save) {
		for(String keys:database.keySet()) {
			ArrayList<VitaminLocation> arrayList = database.get(keys);
			for (int i = 0; i < arrayList.size(); i++) {
				VitaminLocation vl = arrayList.get(i);
				if(vl.getName().contentEquals(newElement.getName())) {
					arrayList.remove(vl);
					break;
				}
			}
		}
		String key = newElement.getType() + ":" + newElement.getSize();
		// synchronized (database) {
		if (database.get(key) == null) {
			database.put(key, new ArrayList<VitaminLocation>());
		}
		ArrayList<VitaminLocation> arrayList = database.get(key);

		boolean toAdd = !arrayList.contains(newElement);
		for (int i = 0; i < arrayList.size(); i++) {
			VitaminLocation loc = arrayList.get(i);
			if(loc.getName().contentEquals(newElement.getName())) {
				arrayList.set(i,newElement);
				return this;
			}
		}
		if (toAdd)
			arrayList.add(newElement);
		// }
		if(save)save();
		return this;
	}

	public CSG get(String name) {
		VitaminLocation e = getElement(name);
		if (e == null)
			throw new RuntimeException("Vitamin must be defined before it is used: " + name);

		try {
			CSG transformed = MobileBaseCadManager.vitaminMakeCSG(e)
					.transformed(TransformFactory.nrToCSG(e.getLocation()));
			transformed.setManufacturing(incominng -> {
				return null;
			});
			transformed.setColor(Color.SILVER);
			return transformed;

		} catch (Exception e1) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e1);
		}
		return null;
	}

	public TransformNR getCoMLocation(String name) {
		VitaminLocation e = getElement(name);
		try {
			double x = (double) getConfiguration(name).get("massCentroidX");
			double y = (double) getConfiguration(name).get("massCentroidY");

			double z = (double) getConfiguration(name).get("massCentroidZ");

			return e.getLocation().copy().translateX(x).translateY(y).translateZ(z);
		} catch (Exception ex) {
			return e.getLocation().copy();
		}
	}

	public double getMassKg(String name) {
		try {
			return (double) getConfiguration(name).get("massKg");
		} catch (Exception ex) {
			com.neuronrobotics.sdk.common.Log.error(ex);;
			return 0.001;
		}
	}

	public Map<String, Object> getConfiguration(String name) throws Exception {
		VitaminLocation e = getElement(name);
		if (e == null)
			throw new RuntimeException("Vitamin must be defined before it is used: " + name);
		if (e.isScript())
			throw new RuntimeException("Script Vitamins do not have configurations");

		return Vitamins.getConfiguration(e.getType(), e.getSize());
	}

	public VitaminLocation getElement(String name) {
		// synchronized (database) {
		for (String testName : database.keySet()) {
			ArrayList<VitaminLocation> list = database.get(testName);
			for (VitaminLocation el : list) {
				String name2 = el.getName();
				if (name2.contentEquals(name))
					return el;
			}
		}
		// }
		return null;
	}

	public VitaminBomManager clear() {
		// synchronized (database) {
		database.clear();
		// }
		return this;
	}

	private synchronized void saveLocal() {
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
				String URL = null;
				Object object = null;
				if (!e.isScript())
					try {
						Map<String, Object> configuration = getConfiguration(e.getName());
						URL = (String) configuration.get("source");
						object = configuration.get("price");
					} catch (Exception ex) {
						com.neuronrobotics.sdk.common.Log.error(ex);;
					}

				if (URL == null) {
					URL = "http://commonwealthrobotics.com";
				}
				if (object == null)
					object = "0.01";

				csv += key + "," + size + "," + URL + "," + object + "\n";
			} 
		}
		if (baseURL != null)
			try {
				String current = ScriptingEngine.codeFromGit(baseURL, getManufacturingBomCsv())[0];
				String currentJ = ScriptingEngine.codeFromGit(baseURL, getManufacturingBomJson())[0];
				if (current.contentEquals(csv) && currentJ.contentEquals(content)) {
					// com.neuronrobotics.sdk.common.Log.error("No update, BoM current");
					saving = false;
					return;
				}
			} catch (Exception e1) {
				// file doesnt exist
			}
		try {
			write(getManufacturingBomJson(), content);
			write(getManufacturingBomCsv(), csv);
		} catch (Exception e) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		saving = false;

	}

	private void write(String file, String content)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		File f = new File(baseWorkspaceFile.getAbsolutePath() + "/" + file);
		if (!f.getParentFile().exists())
			f.getParentFile().mkdir();
		if (!f.exists()) {
			f.createNewFile();
		}
		BufferedWriter writer = new BufferedWriter(new FileWriter(f.getAbsolutePath()));
		writer.write(content);
		writer.close();
	}

	public VitaminBomManager save() {
		saveLocal();
		return this;
	}

	public VitaminBomManager loadBaseVitamins(MobileBase base) {
		for (VitaminLocation v : base.getVitamins()) {
			addVitamin(v);
		}
		for (DHParameterKinematics k : base.getAllDHChains()) {
			for (int i = 0; i < k.getNumberOfLinks(); i++) {
				for (VitaminLocation v : k.getVitamins(i)) {
					addVitamin(v);
				}
				MobileBase b = k.getFollowerMobileBase(i);
				if (b != null) {
					loadBaseVitamins(b);
				}
			}
		}
		return this;
	}

	public static String getManufacturingBomJson() {
		return MANUFACTURING_BOM_JSON;
	}

	public static String getManufacturingBomCsv() {
		return MANUFACTURING_BOM_CSV;
	}

	public static String getManufacturingBomBase() {
		return MANUFACTURING_BOM_BASE;
	}

	public void remove(VitaminLocation loc) {
		for(String keys:database.keySet()) {
			ArrayList<VitaminLocation> arrayList = database.get(keys);
			for (int i = 0; i < arrayList.size(); i++) {
				VitaminLocation vl = arrayList.get(i);
				if(vl.getName().contentEquals(loc.getName())) {
					arrayList.remove(vl);
					return;
				}
			}
		}
	}

}
