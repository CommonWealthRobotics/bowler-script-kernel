package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

public class Sweep extends AbstractAddFrom{
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = null;
	private ArrayList<String> options = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	private Boolean preventBoM =false;
	
	public Sweep set(File source) throws Exception {
		if(!source.getName().toLowerCase().endsWith(".svg"))
			throw new Exception("Sweep can only take files with the .svg extention");
		AddFromFile.toLocal(source,getName());
		return this;
	}
	@Override
	public String getType() {
		// TODO Auto-generated method stub
		return "Sweep";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		nameIndex = 0;
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		if (getName() == null) {

		}
		try {
//			ArrayList<Object>args = new ArrayList<>();
//			args.addAll(Arrays.asList(getName() ));
			ArrayList<CSG> collect = new ArrayList<>();
			File file = getFile();
			if(!file.exists()) {
				throw new RuntimeException("Failed to find file");
			}
			
			ArrayList<Object>args = new ArrayList<>();
			args.addAll(Arrays.asList(name ));
			HashMap<String, Object> configs =new HashMap<String, Object>();
			configs.put("name", name);
			configs.put("PreventBomAdd", preventBoM);
			args.add(configs);
			List<CSG> flattenedCSGs = ScriptingEngine.flaten(file, CSG.class, args);
			for (int i = 0; i < flattenedCSGs.size(); i++) {
				CSG csg = flattenedCSGs.get(i);
				try {
					CSG processedCSG = processGiven(csg, i, getOrderedName());
					collect.add(processedCSG);
				}catch(Exception ex) {
					ex.printStackTrace();
				}
			}
			back.addAll(collect);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return back;
	}



	public File getFile() {
		StringParameter loc = new StringParameter("CaDoodle_File_Location", "NotSet", new ArrayList<String>());
		File parentFile = new File(loc.getStrValue()).getParentFile();
		for(String f:parentFile.list()) {
			if(f.contains(name)) {
				String pathname = parentFile.getAbsolutePath() + DownloadManager.delim() + f;
				return  new File(pathname);
			}
		}
		throw new RuntimeException("File not found! "+name);
	}


	private CSG processGiven(CSG csg, int i,  String name) {
		Transform nrToCSG = TransformFactory.nrToCSG(getLocation());
		String pathname = getFile().getAbsolutePath();

		StringParameter parameter=new StringParameter(name + "_CaDoodle_File", pathname, options);
		parameter.setStrValue(pathname);
		CSG processedCSG = csg
				.transformed(nrToCSG).syncProperties(csg).setParameter(parameter)
				.setRegenerate(previous -> {
					try {
						File file = getFile();
						String fileLocation = file.getAbsolutePath();
						com.neuronrobotics.sdk.common.Log.error("Regenerating " + fileLocation);
						List<CSG> flattenedCSGs = ScriptingEngine.flaten(file, CSG.class, null);
						CSG csg1 = flattenedCSGs.get(i);
						return processGiven(csg1, i, name);
					} catch (Exception e) {
						e.printStackTrace();
					}
					return previous;
				}).setName(name);
		MoveCenter.set(getName(), processedCSG, nrToCSG);
		return processedCSG;
	}

	public TransformNR getLocation() {
		if (location == null)
			location = new TransformNR();
		return location;
	}

	public Sweep setLocation(TransformNR location) {
		this.location = location;
		return this;
	}



	public Boolean getPreventBoM() {
		return preventBoM;
	}

	public Sweep setPreventBoM(Boolean preventBoM) {
		this.preventBoM = preventBoM;
		return this;
	}


}
