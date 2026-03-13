package com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.creature.ControllerOption;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AbstractAddFrom;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.MoveCenter;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;

public class AddRobotController extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private String builderName;
	@Expose(serialize = true, deserialize = true)
	private ControllerOption controller;
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = null;
	@Expose(serialize = true, deserialize = true)
	private List<String> names;

	@Override
	public void pruneCleanup() {
		if (builderName != null) {
			MobileBaseBuilder builder = getRobots().get(getBuilderName());
			builder.removeController(this);
		}
	}

	@Override
	public File getFile() throws NoSuchFileException {
		throw new NoSuchFileException("");
	}

	@Override
	public String getType() {
		return "AddRobotController";
	}

	public ArrayList<VitaminLocation> getVitamins(String prefix) {
		return controller.getVitamins(location, prefix);
	}

	public AddRobotController setNames(List<String> names) {
		this.names = names;
		return this;
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		nameIndex = 0;
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		builderName = getBuilder(names, incoming);
		try {
			controller.runLinkLoader();
		} catch (FileNotFoundException e) {
		}
		for (int i = 0; i < controller.getVitaminNumber(); i++) {
			CSG csg = controller.getVitaminCSG(getCaDoodleFile().getCsgDBinstance(), i).cloneShallow();
			TransformNR offset = getLocation().times(controller.getVitaminPose(i));
			Transform nrToCSG = TransformFactory.nrToCSG(offset);
			String orderedName = getOrderedName();
			csg.setIsHole(true);
			csg.setNoScale(true);
			csg.setIsAlwaysShow(true);
			if (getBuilderName() != null) {
				csg.setMobileBaseName(getBuilderName());
			}
			CSG tmp = csg.transformed(nrToCSG).syncProperties(getCaDoodleFile().getCsgDBinstance(), csg)
					.setRegenerate(csg.getRegenerate()).setName(orderedName);
			back.add(tmp);
			MoveCenter.set(getName(), tmp, nrToCSG);
		}
		if (builderName != null) {
			MobileBaseBuilder builder = getRobots().get(getBuilderName());
			builder.addController(this);
		}
		return back;
	}

	public ControllerOption getController() {
		return controller;
	}

	public AddRobotController setController(ControllerOption controller) {
		this.controller = controller;
		return this;
	}

	public TransformNR getLocation() {
		if (location == null)
			location = new TransformNR();
		return location;
	}

	public AddRobotController setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

	public String getBuilderName() {
		return builderName;
	}

}
