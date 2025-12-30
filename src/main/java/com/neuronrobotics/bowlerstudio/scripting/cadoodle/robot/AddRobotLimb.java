package com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.creature.ControllerOption;
import com.neuronrobotics.bowlerstudio.creature.LimbOption;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AbstractAddFrom;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.Group;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class AddRobotLimb extends AbstractAddFrom{
	@Expose(serialize = true, deserialize = true)
	private String builderName;
	@Expose(serialize = true, deserialize = true)
	private LimbOption limb;
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = null;
	@Expose(serialize = true, deserialize = true)
	private List<String> names;
	public boolean forceLoad = false;
	@Override
	public void pruneCleanup() {
		if (getBuilderName() != null) {
			MobileBaseBuilder builder = getRobots().get(getBuilderName());
			builder.removeLimb(this);
		}
	}
	@Override
	public File getFile() throws NoSuchFileException {
		throw new NoSuchFileException("");
	}

	@Override
	public String getType() {
		return "AddRobotLimb";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		nameIndex=0;
		if(builderName==null)
			setBuilderName(getBuilder(names, incoming));
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		if(getBuilderName()!=null) {
			MobileBaseBuilder builder = getRobots().get(getBuilderName());
			builder.addLimb(this,forceLoad);
			try {
				builder.build(getDb());
			} catch (Exception e) {
				com.neuronrobotics.sdk.common.Log.error(e);
			}
			
			DHParameterKinematics newLimb = builder.getMobileBase().getLimbByName(getName());
			if(newLimb==null)
				throw new RuntimeException("Failed to create a limb!");
			MobileBaseCadManager manager=builder.getCadManager();
			ArrayList<CSG> limbCad = manager.generateCad(getDb(),newLimb);
			for(CSG c:limbCad) {
				c.setName(getOrderedName());
				c.setLimbName(name);
				c.setMobileBaseName(getBuilderName());
				c.setNoScale(true);
				c.setIsMotionLock(true);
				back.add(c);
			}
			manager.render();
		}
		return back;
	}
	public AddRobotLimb setLimb(LimbOption o) {
		limb = o;
		return this;
	}
	public AddRobotLimb setNames(List<String> names) {
		this.names = names;
		return this;
	}
	public LimbOption getLimb() {
		return limb;
	}
	public AddRobotLimb setLocation(TransformNR location) {
		this.location = location.copy();
		return this;
	}
	public TransformNR getLocation() {
		if(location==null)
			location=new TransformNR();
		return location;
	}
	public String getBuilderName() {
		return builderName;
	}
	public void setBuilderName(String builderName) {
		this.builderName = builderName;
	}
	
	public MobileBaseBuilder getMobilBaseBuilder() {
		return getRobots().get(getBuilderName());
	}
}
