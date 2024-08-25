package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
	@Expose (serialize = false, deserialize = false)
	private int nameIndex = 0;
	@Expose (serialize = false, deserialize = false)
	private HashSet<String> namesAdded = new HashSet<>();
	
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
		String result;
		if(nameIndex==0)
			result= name;
		else {
			result= name+"_"+nameIndex;
		}
		nameIndex++;
		namesAdded.add(result);
		return result;
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
					.flaten(gitULR, fileRel, CSG.class,args)
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

	public HashSet<String> getNamesAdded() {
		return namesAdded;
	}

}
