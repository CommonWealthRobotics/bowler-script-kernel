package com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AbstractAddFrom;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

public class MakeRobot extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	//@Expose(serialize = true, deserialize = true)
	MobileBaseBuilder builder;

	@Override
	public String getType() {
		return "MakeRobot";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		try {
			getBuilder().build();
			for(CSG c:incoming) {
				for(String s:names) {
					if(c.getName().contentEquals(s)) {
						c.setMobileBaseName(getName());
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return incoming;
	}

	@Override
	public List<String> getNamesAddedInThisOperation() {
		return new ArrayList<String>();
	}

	@Override
	public File getFile() throws NoSuchFileException {
		try {
			return getBuilder().getFile();
		} catch (Exception e) {
			throw new NoSuchFileException(getName());
		}
	}

	public List<String> getNames() {
		return names;
	}

	public void setNames(List<String> assignedAsBase) {
		if(assignedAsBase.size()==0)
			throw new RuntimeException("Can not create a robot without specifying a base");
		this.names = assignedAsBase;
	}

	/**
	 * @return the builder
	 */
	public MobileBaseBuilder getBuilder() {
		if (builder == null) {
			StringParameter loc = new StringParameter("CaDoodle_File_Location", "NotSet", new ArrayList<String>());
			String strValue = loc.getStrValue();
			File parentFile = new File(strValue).getParentFile();
			String source = parentFile.getAbsolutePath();
			builder = new MobileBaseBuilder(source, getName() + "-mobilbase.xml");
		}
		return builder;
	}

}
