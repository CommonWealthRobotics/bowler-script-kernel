package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class Resize implements ICaDoodleOpperation {

	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();

	@Expose(serialize = true, deserialize = true)
	private TransformNR height = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR rightFront = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR leftRear = null;

	@Override
	public String getType() {
		return "Resize";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming.stream().map(starting -> {
			for (String name : names) {
				if (starting.getName().contentEquals(name)) {
					return performResize(starting, name);
				}
			}
			return starting;
		}).collect(Collectors.toCollection(ArrayList::new)));
		return back;
	}

	private CSG performResize(CSG starting, String name) {
		CSG resizeUp = starting.scaleToMeasurmentZ(Math.abs(height.getZ()))
				.toZMin()
				.movez(starting.getMinZ());
		double xdimen = rightFront.getX()-leftRear.getX();
		double ydimen = rightFront.getY()-leftRear.getY();
		resizeUp=resizeUp.scaleToMeasurmentX(Math.abs(xdimen))
						 .scaleToMeasurmentY(Math.abs(ydimen));
		resizeUp=resizeUp
					.toXMin()
					.toYMin()
					.movex(leftRear.getX())
					.movey(rightFront.getY());
		return resizeUp;
	}

	public Resize setResize(TransformNR h, TransformNR rf, TransformNR lr) {
		height = h;
		rightFront = rf;
		leftRear = lr;
		return this;
	}

	public List<String> getNames() {
		return names;
	}

	public Resize setNames(List<String> names) {
		this.names = names;
		return this;
	}

}
