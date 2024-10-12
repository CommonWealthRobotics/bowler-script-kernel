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
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

public class AddFromFile extends AbstractAddFrom implements ICaDoodleOpperation {

	@Expose (serialize = true, deserialize = true)
	private String name=null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = null;
	private ArrayList<String> options = new ArrayList<String>();
	private StringParameter parameter=null;
	public AddFromFile set(File source) {
		getParameter().setStrValue(source.getAbsolutePath());
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
		if(getName()==null) {
			
		}
		try {
//			ArrayList<Object>args = new ArrayList<>();
//			args.addAll(Arrays.asList(getName() ));
			ArrayList<CSG> collect = new ArrayList<>();
			List<CSG> flattenedCSGs = ScriptingEngine.flaten(new File(getParameter().getStrValue()), CSG.class, null);
			System.out.println("Initial Loading "+getParameter().getStrValue());
			for (int i = 0; i < flattenedCSGs.size(); i++) {
			    CSG csg = flattenedCSGs.get(i);
			    CSG processedCSG = processGiven(csg,i,getParameter(),getOrderedName());
			    
			    collect.add(processedCSG);
			}
			back.addAll(collect
					);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return back;
	}

	private CSG processGiven(CSG csg,int i,StringParameter parameter,String name) {
		CSG processedCSG = csg
//		    .moveToCenterX()
//		    .moveToCenterY()
//		    .toZMin()
		    .transformed(TransformFactory.nrToCSG(getLocation()))
		    .syncProperties(csg)
		    .setRegenerate(previous -> {
				try {
					String fileLocation=parameter.getStrValue();
					System.out.println("Regenerating "+fileLocation);
					List<CSG> flattenedCSGs = ScriptingEngine.flaten(new File(fileLocation), CSG.class, null);
					 CSG csg1 = flattenedCSGs.get(i);
					 return processGiven(csg1,i,parameter,name);
				} catch (Exception e) {
					e.printStackTrace();
				}
				return previous;
			})
		    .setName(name);
		return processedCSG;
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

	public String getName() {
		if(name==null) {
			setName(RandomStringFactory.generateRandomString());
		}
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public StringParameter getParameter() {
		if(parameter==null)
			setParameter(new StringParameter(getName()+"_CaDoodle_File_Location", "UnKnown", options));
		return parameter;
	}

	public void setParameter(StringParameter parameter) {
		this.parameter = parameter;
	}

}
