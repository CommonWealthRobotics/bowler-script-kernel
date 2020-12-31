package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.svg.SVGExporter;
import eu.mihosoft.vrl.v3d.svg.SVGLoad;
import javafx.scene.paint.Color;

public class SvgLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		SVGLoad s = new SVGLoad(code.toURI());
		return run(s);
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		SVGLoad s = new SVGLoad(code);
		return run(s);
	}
	
	private Object run(SVGLoad s) {
		
		ArrayList<Object> polys= new ArrayList<>();
		HashMap<String, List<Polygon>> polygons = s.toPolygons();
		for(String key:polygons.keySet()) {
			for(Polygon P:polygons.get(key)) {
				polys.add(P);
			}
		}
		double depth =5;
		List<String> layers = s.getLayers();
		for(int i=0;i<layers.size();i++) {
			String layerName=layers.get(i);
			CSG extrudeLayerToCSG = s.extrudeLayerToCSG(depth,layerName);
			extrudeLayerToCSG.setColor(Color.web(SVGExporter.colorNames.get(i)));
			polys.add(extrudeLayerToCSG);
			depth+=5;
		}
		
		return polys;
	}

	@Override
	public String getShellType() {
		return "SVG";
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		return new ArrayList<>(Arrays.asList("SVG","svg"));
	}

	@Override
	public boolean getIsTextFile() {
		return true;
	}
	
	@Override
	public	String getDefaultContents() {
		  return new SVGExporter().make();
	  }

}
