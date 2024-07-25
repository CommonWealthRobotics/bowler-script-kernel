package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

public class ToSolid implements ICaDoodleOpperation {
	@Expose (serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose (serialize = true, deserialize = true)
	private Color color = null;
	@Override
	public String getType() {
		return "To Solid";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		for(CSG c: incoming) {
			for(String name:names) {
				if(name.contentEquals(c.getName())) {
					c.setIsHole(false);
					if(color!=null) {
						c.setColor(color);
					}
				}
			}
		}
		return incoming;
	}

	public List<String> getNames() {
		return names;
	}

	public ToSolid setNames(List<String> names) {
		this.names = names;
		return this;
	}

	public Color getColor() {
		return color;
	}

	public ToSolid setColor(Color color) {
		this.color = color;
		return this;
	}

}
