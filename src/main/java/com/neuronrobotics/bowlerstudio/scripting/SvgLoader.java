package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import eu.mihosoft.vrl.v3d.svg.SVGLoad;

public class SvgLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		SVGLoad s = new SVGLoad(code.toURI());
		if(args==null) {
			args=	new ArrayList<>(Arrays.asList(new Double(10),new Double(0.01)));
		}
		return s.extrude((Double)args.get(0),(Double)args.get(1));
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		SVGLoad s = new SVGLoad(code);
		if(args==null) {
			args=	new ArrayList<>(Arrays.asList(new Double(10),new Double(0.01)));
		}
		return s.extrude((Double)args.get(0),(Double)args.get(1));
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

}
