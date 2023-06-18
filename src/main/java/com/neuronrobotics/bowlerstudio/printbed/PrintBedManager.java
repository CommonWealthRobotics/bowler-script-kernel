package com.neuronrobotics.bowlerstudio.printbed;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.creature.UserManagedPrintBedData;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import javafx.scene.paint.Color;

public class PrintBedManager {

	private UserManagedPrintBedData database;
	Type type = new TypeToken<UserManagedPrintBedData>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	String file = "manufacturing/printbed.json";
	List<Color> colors = Arrays.asList(Color.WHITE,Color.GREY,Color.BLUE,Color.TAN);
	private String url;
	HashMap<Integer ,CSG>  bedReps = new HashMap<Integer, CSG>();
	ArrayList<PrintBedObject> objects = new ArrayList<PrintBedObject>();

	public PrintBedManager(String url, List<CSG> parts) {
		this.url = url;
		File f = new File(ScriptingEngine.getRepositoryCloneDirectory(url).getAbsolutePath() + "/" + file);
		if (f.exists()) {
			String source;
			try {
				source = FileUtils.readFileToString(f, "UTF-8");
				database = gson.fromJson(source, type);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else {
			database=new UserManagedPrintBedData();
			database.init();
		}
		for(CSG bit:parts) {
			int index = bit.getPrintBedIndex();
			int colorIndex = index%4;
			CSG bed = new Cube(database.bedX,database.bedY,1).toCSG()
						.toXMin()
						.toYMax()
						.toZMax()
						.rotz(90*index)
						.movey(index>4?database.bedX*(index-4):0);
			bed.setColor(colors.get(colorIndex));
			bedReps.put(index, bed);
			String name = bit.getName();
			CSG prepedBit=bit.prepForManufacturing();
			if(prepedBit!=null) {
				if(database.locations.get(name)==null) {
					database.locations.put(name,new TransformNR());
				}
				PrintBedObject obj  = new PrintBedObject(name, prepedBit, bed.getMaxX(), bed.getMinX(), bed.getMaxY(), bed.getMinY(), database.locations.get(name));
				objects.add(obj);
				obj.addSaveListener(() ->{ 
					obj.checkBounds();
					save();
				});
			}
		}
	}
	public ArrayList<CSG> get(){
		ArrayList<CSG> back = new ArrayList<>();
		for(CSG c:bedReps.values()) {
			back.add(c);
		}
		for(PrintBedObject pbo:objects) {
			back.addAll(pbo.get());
		}
		return back;
	}
	private synchronized void saveLocal() {
		String content = gson.toJson(database);
		try {
			ScriptingEngine.commit(url, ScriptingEngine.getBranch(url),file, content,
					"Save Print Bed Locations", true);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private void save() {
		new Thread(()->{
			saveLocal();
		}).start();
	}

}
