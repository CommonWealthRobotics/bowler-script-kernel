package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.CSG;

public class AddFromScript implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private String gitULR = "";
	@Expose (serialize = true, deserialize = true)
	private String fileRel = "";
	@Expose (serialize = true, deserialize = true)
	private String name=null;
	@Expose (serialize = true, deserialize = true)
	private int nameIndex = 0;
	
	public AddFromScript set(String git, String f) {
		gitULR = git;
		fileRel = f;
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
		try {
			back.addAll(ScriptingEngine
					.flaten(gitULR, fileRel, CSG.class)
					.stream()
					.map(csg->{
						return csg
								.moveToCenterX()
								.moveToCenterY()
								.toZMin()
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

}
