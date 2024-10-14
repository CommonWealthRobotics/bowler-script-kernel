package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class AddFromScript extends AbstractAddFrom implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private String gitULR = "";
	@Expose (serialize = true, deserialize = true)
	private String fileRel = "";
	@Expose (serialize = true, deserialize = true)
	private String name=null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR location =null;
	


	
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

	public AddFromScript setLocation(TransformNR location) {
		this.location = location;
		return this;
	}
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
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

}
