package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.eclipse.jgit.api.errors.GitAPIException;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.scene.paint.Color;

public class AddFromScript extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private String gitULR = "";
	@Expose(serialize = true, deserialize = true)
	private String fileRel = "";

	@Expose(serialize = true, deserialize = true)
	private TransformNR location = null;
	@Expose(serialize = true, deserialize = true)
	private Boolean preventBoM = false;

	public AddFromScript set(String git, String f) {
		gitULR = git;
		fileRel = f;
		return this;
	}

	@Override
	public String getType() {
		return "AddFromScript";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		return process(incoming, fileRel);
	}

	public List<CSG> process(List<CSG> incoming, String fileName) {

		nameIndex = 0;
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		boolean isDoodle = fileName.toLowerCase().endsWith(".doodle");

		try {
			ArrayList<Object> args = new ArrayList<>();
			args.addAll(Arrays.asList(getName()));
			HashMap<String, Object> configs = new HashMap<String, Object>();
			configs.put("name", getName());
			configs.put("PreventBomAdd", preventBoM);
			args.add(configs);
			CSGDatabaseInstance instance = getCaDoodleFile().getCsgDBinstance();
			if (isDoodle || instance == null) {
				Path tempFile = Files.createTempFile("CSGDatabase", ".tmp");
				instance = (new CSGDatabaseInstance(tempFile.toFile()));
			}
			List<CSG> flaten = ScriptingEngine.flaten(instance, gitULR, fileName, CSG.class, args);
			for (CSG c : flaten)
				for (String s : c.getParameters(instance)) {
					Log.debug("Parameter added " + s);
				}
			ArrayList<CSG> collect = new ArrayList<>();
			collect.addAll(flaten);
			if (collect.size() == 0) {
				collect.add(new Cube(20).toCSG().setColor(Color.PINK));
			}
			for (int i = 0; i < collect.size(); i++) {
				CSG csg = collect.get(i);
				if (isDoodle) {
					csg.setStorage(new PropertyStorage());
				}
				Transform nrToCSG = TransformFactory.nrToCSG(getLocation());
				String orderedName = getOrderedName();
				CSG tmp = csg.transformed(nrToCSG).syncProperties(getCaDoodleFile().getCsgDBinstance(), csg)
						.setRegenerate(csg.getRegenerate()).setName(orderedName);
				collect.set(i, tmp);
				MoveCenter.set(getName(), tmp, nrToCSG);
			}

			back.addAll(collect);
		} catch (Exception e) {
			Log.error(e);
			throw new RuntimeException(e);
		}

		if (back.size() == 0)
			throw new RuntimeException("AddFromScript must return at least one CSG! " + getName());
		return back;
	}

	public TransformNR getLocation() {
		if (location == null)
			location = new TransformNR();
		return location;
	}

	public AddFromScript setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

	@Override
	public File getFile() {
		// Auto-generated method stub
		try {
			return ScriptingEngine.fileFromGit(gitULR, fileRel);
		} catch (GitAPIException | IOException e) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
			return null;
		}
	}

	public Boolean getPreventBoM() {
		return preventBoM;
	}

	public AddFromScript setPreventBoM(Boolean preventBoM) {
		this.preventBoM = preventBoM;
		return this;
	}

}
