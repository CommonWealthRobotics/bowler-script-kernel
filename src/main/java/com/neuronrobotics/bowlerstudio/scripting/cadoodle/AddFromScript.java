package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.List;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.CSG;

public class AddFromScript implements ICaDoodleOpperation {
	private String gitULR = "";
	private String fileRel = "";

	public void set(String git, String f) {
		gitULR = git;
		fileRel = f;
	}

	@Override
	public String getType() {
		return "AddObject";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		try {
			back.addAll(ScriptingEngine.flaten(gitULR, fileRel, CSG.class));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return back;
	}

}
