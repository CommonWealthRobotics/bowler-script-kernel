package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class Delete implements ICaDoodleOpperation {
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = new TransformNR();
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();

	@Override
	public String getType() {
		return "Delete";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);

		for (String s : names) {
			CaDoodleFile.applyToAllConstituantElements(false, s, back, new ICadoodleRecursiveEvent() {
				@Override
				public ArrayList<CSG> process(CSG incoming) {

					ArrayList<CSG> b = new ArrayList<>();
					b.add(null);
					return b;
				}
			});
		}
		return back;
	}

	public TransformNR getLocation() {
		return location;
	}

	public Delete setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

	public List<String> getNames() {
		return names;
	}

	public Delete setNames(List<String> names) {
		this.names = names;
		return this;
	}
}
