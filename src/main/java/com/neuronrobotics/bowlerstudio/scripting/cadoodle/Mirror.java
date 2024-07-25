package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class Mirror implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private TransformNR location=new TransformNR();
	@Expose (serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	
	@Override
	public String getType() {
		return "Move Center";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming
				.stream()
				.map(csg->{
					for(String name:names) {
						if(csg.getName().contentEquals(name))
							return mirror(csg, name)
									;
					}
					return csg;
				})
			    .collect(Collectors.toCollection(ArrayList::new))
			);
		return back;
	}

	private CSG mirror(CSG csg, String name) {
		CSG centered=csg.moveToCenter();
		if(location.getX()>0) {
			centered=centered.mirrorx();
		}
		if(location.getY()>0) {
			centered=centered.mirrory();
		}
		if(location.getZ()>0) {
			centered=centered.mirrorz();
		}		
		return centered	
				.move(csg.getCenter())
				.setName(name)
				.syncProperties(csg);
	}

	public TransformNR getLocation() {
		return location;
	}

	public Mirror setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

	public List<String> getNames() {
		return names;
	}

	public Mirror setNames(List<String> names) {
		this.names = names;
		return this;
	}

}
