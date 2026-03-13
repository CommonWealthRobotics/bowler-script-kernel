package com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AbstractAddFrom;
import eu.mihosoft.vrl.v3d.CSG;

public class MakeRobot extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private List<String> assignedAsBase = new ArrayList<String>();
	// @Expose(serialize = true, deserialize = true)
	MobileBaseBuilder builder;

	@Override
	public String getType() {
		return "MakeRobot";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		try {
			getBuilder().build(getDb());
			for (CSG c : incoming) {
				for (String s : assignedAsBase) {
					if (c.getName().contentEquals(s)) {
						c.setMobileBaseName(getName());
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
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
		return assignedAsBase;
	}

	public void setNames(List<String> assignedAsBase) {
		if (assignedAsBase.size() == 0)
			throw new RuntimeException("Can not create a robot without specifying a base");
		this.assignedAsBase = assignedAsBase;
	}

	/**
	 * @return the builder
	 */
	public MobileBaseBuilder getBuilder() {
		if (builder == null) {
			String strValue = getCaDoodleFile().getSelf().getAbsolutePath();
			File parentFile = new File(strValue).getParentFile();
			String source = parentFile.getAbsolutePath();
			builder = new MobileBaseBuilder(getDb(), source, getName() + "-mobilbase.xml");
		}
		return builder;
	}

}
