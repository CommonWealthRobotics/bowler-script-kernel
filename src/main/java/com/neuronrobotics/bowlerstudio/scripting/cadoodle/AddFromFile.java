package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class AddFromFile implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private String fileLocation=null;
	@Expose (serialize = true, deserialize = true)
	private String name=null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = null;
	
	private int nameIndex = 0;
	public AddFromFile set(File source) {
		fileLocation=source.getAbsolutePath();
		return this;
	}

	@Override
	public String getType() {
		return "Add Object";
	}
	
	private String getOrderedName() {
		if(name==null) {
			name=RandomStringFactory.generateRandomString();
		}
		if(nameIndex==0)
			return name;
		nameIndex++;
		return name+"_"+nameIndex;
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		if(name==null) {
			name=RandomStringFactory.generateRandomString();
		}
		try {
			ArrayList<Object>args = new ArrayList<>();
			args.addAll(Arrays.asList(name ));
			back.addAll(ScriptingEngine
					.flaten(new File(fileLocation), CSG.class,args)
					.stream()
					.map(csg->{
						return csg
								.moveToCenterX()
								.moveToCenterY()
								.toZMin()
								.transformed(TransformFactory.nrToCSG( getLocation() ))
								.syncProperties(csg)
								.setName(getOrderedName());
					})
				    .collect(Collectors.toCollection(ArrayList::new))
					);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return back;
	}

	public TransformNR getLocation() {
		if(location==null)
			location=new TransformNR();
		return location;
	}

	public AddFromFile setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

}
