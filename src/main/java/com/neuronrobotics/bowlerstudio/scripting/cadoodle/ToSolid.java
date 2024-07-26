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
	private double r=1.0;
	@Expose (serialize = true, deserialize = true)
	private double g=0;
	@Expose (serialize = true, deserialize = true)
	private double b=0;
	@Expose (serialize = true, deserialize = true)
	private double a=1.0;
	@Expose (serialize = true, deserialize = true)
	private boolean useColor=false;
	
	@Override
	public String getType() {
		return "To Solid";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> replace = new ArrayList<CSG>();
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		for(CSG c: incoming) {
			for(String name:names) {
				if(name.contentEquals(c.getName())) {
					replace.add(c);
					CSG t=c.clone().syncProperties(c);
					t.setIsHole(false);
					if(useColor) {
						t.setColor(getColor());
					}
					back.add(t);
				}
			}
		}
		for(CSG c:replace) {
			back.remove(c);
		}
		return back;
	}

	public List<String> getNames() {
		return names;
	}

	public ToSolid setNames(List<String> names) {
		this.names = names;
		return this;
	}

	public Color getColor() {
		return new Color(r,g,b,a);
	}

	public ToSolid setColor(Color color) {
		r=color.getRed();
		g=color.getGreen();
		b=color.getBlue();
		a=color.getOpacity();
		useColor=true;
		return this;
	}

}
