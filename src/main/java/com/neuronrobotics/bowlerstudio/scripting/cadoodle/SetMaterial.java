package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;

public class SetMaterial extends CaDoodleOperation {
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();

	@Expose(serialize = true, deserialize = true)
	private String materialType = null;

	@Expose(serialize = true, deserialize = true)
	private String material = null;

	@Expose(serialize = true, deserialize = true)
	private double infillPercent = -1;
	@Expose(serialize = true, deserialize = true)
	private double density = -1;

	@Override
	public String getType() {
		return "SetMaterial";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> replace = new ArrayList<CSG>();
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		for (CSG c : incoming) {
			if (c.isLock())
				continue;
			for (String name : names) {
				if (name.contentEquals(c.getName())) {
					replace.add(c);
					CSG b = c.clone().setRegenerate(c.getRegenerate())
							.syncProperties(getCaDoodleFile().getCsgDBinstance(), c);
					if (materialType != null)
						b.setMaterialType(materialType);
					if (material != null)
						b.setMaterial(material);
					if (infillPercent > 0)
						b.setMaterialInfillPercent(infillPercent);
					if (density > 0)
						b.setMaterialDensity(density);
					back.add(b);
				}
			}
		}
		for (CSG c : replace) {
			back.remove(c);
		}
		return back;
	}

	public List<String> getNamesAddedInThisOperation() {
		return names;
	}

	public SetMaterial setNames(List<String> names) {
		this.names = names;
		return this;
	}

	public SetMaterial setMaterialType(String materialType) {
		if (materialType.length() == 0)
			throw new RuntimeException("Can not set empty");
		this.materialType = materialType;
		return this;
	}

	public SetMaterial setMaterial(String material) {
		if (material.length() == 0)
			throw new RuntimeException("Can not set empty");
		this.material = material;
		return this;
	}

	public SetMaterial setDensity(double Density) {
		this.density = Density;
		return this;
	}

	public SetMaterial setInfillPercent(double infillPercent) {
		this.infillPercent = infillPercent;
		return this;
	}
}
