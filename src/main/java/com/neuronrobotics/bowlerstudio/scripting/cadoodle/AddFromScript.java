package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class AddFromScript extends AbstractAddFrom implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private String gitULR = "";
	@Expose (serialize = true, deserialize = true)
	private String fileRel = "";

	@Expose(serialize = true, deserialize = true)
	private TransformNR location =null;
	@Expose(serialize = true, deserialize = true)
	private Boolean preventBoM =false;


	
	public AddFromScript set(String git, String f) {
		gitULR = git;
		fileRel = f;
		return this;
	}

	@Override
	public String getType() {
		return "Add Object";
	}
	

	@Override
	public List<CSG> process(List<CSG> incoming) {

		nameIndex=0;
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);

		try {
			ArrayList<Object>args = new ArrayList<>();
			args.addAll(Arrays.asList(getName() ));
			HashMap<String, Object> configs =new HashMap<String, Object>();
			configs.put("name", getName());
			configs.put("PreventBomAdd", preventBoM);
			args.add(configs);
			List<CSG> flaten = ScriptingEngine
					.flaten(gitULR, fileRel, CSG.class,args);
			ArrayList<CSG> collect = new ArrayList<>();
			collect.addAll(flaten);
			for(int i=0;i<collect.size();i++) {
				CSG csg=collect.get(i);
				CSG tmp=csg
						.transformed(TransformFactory.nrToCSG( getLocation() ))
						.syncProperties(csg)
						.setRegenerate(csg.getRegenerate())
						.setName(getOrderedName());
				collect.set(i, tmp);
			}
			back.addAll(collect);
			VitaminBomManager boM = CaDoodleFile.getBoM();
			VitaminLocation loc = boM.getByName(getName());
			if(loc!=null) {
				loc.setLocation(location);
				boM.save();
			}
		} catch (Exception e) {
			if(!fileRel.contains("generated"))
			try {
				fileRel="generated/"+fileRel;
				return process(incoming);
			}catch(Exception e2) {
				e2.printStackTrace();
			}
		}
		return back;
	}



	public TransformNR getLocation() {
		if(location==null)
			location=new TransformNR();
		return location;
	}

	public AddFromScript setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

	@Override
	public File getFile() {
		// TODO Auto-generated method stub
		try {
			return ScriptingEngine.fileFromGit(gitULR, fileRel);
		} catch (GitAPIException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	public Boolean getPreventBoM() {
		return preventBoM;
	}

	public AddFromScript setPreventBoM(Boolean preventBoM) {
		this.preventBoM = preventBoM;
		return this;
	}

}
