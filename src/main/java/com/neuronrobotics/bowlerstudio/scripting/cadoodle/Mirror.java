package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Transform;

public class Mirror implements ICaDoodleOpperation {
	@Expose(serialize = true, deserialize = true)
	private MirrorOrentation location;
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	private TransformNR workplane = null;

	@Override
	public String getType() {
		return "Move Center";
	}

	private CSG sync(CSG incoming, CSG c) {
		return c.syncProperties(incoming).setName(incoming.getName()).setColor(incoming.getColor());
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		for (String name : names) {
			for (CSG csg : incoming) {
				if(!csg.getName().contentEquals(name))
					continue;
				CSG base = csg.transformed(TransformFactory.nrToCSG(getWorkplane()).inverse());
				Transform mirroringCenter = new Transform().movex(base.getCenterX()).movey(base.getCenterY())
						.movez(base.getCenterZ());
				CaDoodleFile.applyToAllConstituantElements(false, name, back, (incoming1, depth) -> {
					ArrayList<CSG> b = new ArrayList<>();
					CSG t = incoming1.transformed(TransformFactory.nrToCSG(getWorkplane()).inverse());
					CSG centered = t.transformed(mirroringCenter.inverse());
					if (location == MirrorOrentation.x) {
						centered = centered.mirrorx();
					}
					if (location == MirrorOrentation.y) {
						centered = centered.mirrory();
					}
					if (location == MirrorOrentation.z) {
						centered = centered.mirrorz();
					}
					centered = centered.transformed(mirroringCenter);
					centered = centered.transformed(TransformFactory.nrToCSG(getWorkplane()));
					CSG tf = centered.setName(name).syncProperties(incoming1);
					sync(incoming1, tf);
					b.add(tf);
					return b;
				}, 1);
			}
		}
		return back;
//		back.addAll(incoming
//				.stream()
//				.map(csg->{
//					
//					for(String name:names) {
//						if(csg.isLock())
//							continue;
//						if(csg.getName().contentEquals(name))
//							return mirror(csg, name)
//									;
//					}
//					return csg;
//				})
//			    .collect(Collectors.toCollection(ArrayList::new))
//			);
//		return back;
	}

	private CSG mirror(CSG csg, String name) {
		CSG t = csg.transformed(TransformFactory.nrToCSG(getWorkplane()).inverse());
		Transform mirroringCenter = new Transform().movex(t.getCenterX()).movex(t.getCenterY()).movez(t.getCenterZ());

		CSG centered = t.transformed(mirroringCenter.inverse());
		if (location == MirrorOrentation.x) {
			centered = centered.mirrorx();
		}
		if (location == MirrorOrentation.y) {
			centered = centered.mirrory();
		}
		if (location == MirrorOrentation.z) {
			centered = centered.mirrorz();
		}
		centered = centered.transformed(mirroringCenter);
		centered = centered.transformed(TransformFactory.nrToCSG(getWorkplane()));
		return centered.setName(name).syncProperties(csg);
	}

	public MirrorOrentation getLocation() {
		return location;
	}

	public Mirror setLocation(MirrorOrentation location) {
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

	public TransformNR getWorkplane() {
		if (workplane == null)
			workplane = new TransformNR();
		return workplane;
	}

	public Mirror setWorkplane(TransformNR workplane) {
		this.workplane = workplane;
		return this;
	}

}
