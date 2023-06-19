package com.neuronrobotics.bowlerstudio.printbed;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.creature.UserManagedPrintBedData;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.Transform;
import javafx.scene.paint.Color;

public class PrintBedManager {

	private UserManagedPrintBedData database = null;
	Type type = new TypeToken<UserManagedPrintBedData>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	public static String file = "printbed.json";
	List<Color> colors = Arrays.asList(Color.WHITE, Color.GREY, Color.BLUE, Color.TAN);
	private String url;
	HashMap<Integer, CSG> bedReps = new HashMap<Integer, CSG>();
	ArrayList<PrintBedObject> objects = new ArrayList<PrintBedObject>();
	private ArrayList<CSG> parts;

	public PrintBedManager(String url, ArrayList<CSG> parts) {
		this.url = url;
		this.parts = parts;
		if (url == null)
			return;
		File f = new File(ScriptingEngine.getRepositoryCloneDirectory(url).getAbsolutePath() + "/" + file);
		if (f.exists()) {
			String source;
			byte[] bytes;
			try {
				bytes = Files.readAllBytes(f.toPath());
				source = new String(bytes, "UTF-8");
				database = gson.fromJson(source, type);
			} catch (Exception ex) {
				ex.printStackTrace();
			}

		}
		if (database == null) {
			database = new UserManagedPrintBedData();
			database.init();
		}
		for (CSG bit : parts) {
			int index = bit.getPrintBedIndex();
			int colorIndex = index % 4;
			double zrot = -90 * (index);
			double yval = index > 4 ? database.bedX * (index - 4) : 0;
			System.out.println(bit.getName() + " on " + index + " rot " + zrot + " y " + yval);

			CSG bed = new Cube(database.bedX, database.bedY, 1).toCSG().toXMin().toYMin().toZMax().rotz(zrot)
					.movey(yval);
			bed.setColor(colors.get(colorIndex));
			bedReps.put(index, bed);
			String name = bit.getName();
			CSG prepedBit = bit.prepForManufacturing();
			if (prepedBit != null && name.length() > 0) {
				if (database.locations.get(name) == null) {
					database.locations.put(name, new TransformNR());
				}
				PrintBedObject obj = new PrintBedObject(name, prepedBit, bed.getMaxX(), bed.getMinX(), bed.getMaxY(),
						bed.getMinY(), database.locations.get(name));
				objects.add(obj);
				obj.addSaveListener(() -> {
					obj.checkBounds();
					save();
				});
			}
		}
	}

	public ArrayList<CSG> makePrintBeds() {
		if (url == null)
			return parts;
		HashMap<Integer, ArrayList<CSG>> beds = new HashMap<>();
		for (CSG bit : parts) {
			ArrayList<String> formats = bit.getExportFormats();
			String name = bit.getName();
			int index = bit.getPrintBedIndex();
			bit = bit.prepForManufacturing();
			if (bit != null) {
				if (beds.get(index) == null) {
					beds.put(index, new ArrayList<CSG>());
				}
				TransformNR location = database.locations.get(name);
				if (location != null) {
					Transform csfMove = TransformFactory.nrToCSG(location);
					bit = bit.transformed(csfMove);
				}
				if (formats != null)
					for (String s : formats)
						bit.addExportFormat(s);
				beds.get(index).add(bit);
			}
		}
		ArrayList<CSG> bedsOutputs = new ArrayList<CSG>();
		for (Integer i : beds.keySet()) {
			String name = "Print-Bed-" + i;
			ArrayList<CSG> bedComps = beds.get(i);
			CSG bed = null;
			for (CSG p : bedComps) {
				ArrayList<String> formats = p.getExportFormats();
				if (bed == null)
					bed = p;
				else {
					bed = bed.dumbUnion(p);
				}
				if (formats != null)
					for (String s : formats)
						bed.addExportFormat(s);
			}
			if (bed != null)
				bed.setName(name);
			else {
				bed = new Cube().toCSG();
				bed.setManufacturing(incoming -> null);
			}
			bedsOutputs.add(bed);
		}
		bedsOutputs.addAll(parts);
		return bedsOutputs;
	}

	public ArrayList<CSG> get() {
		ArrayList<CSG> back = new ArrayList<>();
		for (CSG c : bedReps.values()) {
			back.add(c);
		}
		for (PrintBedObject pbo : objects) {
			back.addAll(pbo.get());
		}
		return back;
	}

	private synchronized void saveLocal() {
		String content = gson.toJson(database);
		try {
			ScriptingEngine.commit(url, ScriptingEngine.getBranch(url), file, content, "Save Print Bed Locations",
					true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void save() {
		new Thread(() -> {
			saveLocal();
		}).start();
	}

}
