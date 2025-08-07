package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

public class LimbOption {
	@Expose(serialize = true, deserialize = true)
	LimbType type;
	@Expose(serialize = true, deserialize = true)
	String name;
	@Expose(serialize = true, deserialize = true)
	String url;
	@Expose(serialize = true, deserialize = true)
	String file;
	@Expose(serialize = true, deserialize = true)
	boolean composite;
	@Expose(serialize = true, deserialize = true)
	ControllerFeatures consumes;
	@Expose(serialize = true, deserialize = true)
	ControllerFeatures provides;

	public DHParameterKinematics getLimb(String uniqueName) throws Exception {
		String xmlContent = ScriptingEngine.codeFromGit(url, file)[0];
		if (!composite) {
			DHParameterKinematics newLimb = new DHParameterKinematics(null, IOUtils.toInputStream(xmlContent, "UTF-8"));
			newLimb.setScriptingName(uniqueName);
			return newLimb;
		} else {
			MobileBase base = (MobileBase) ScriptingEngine.gitScriptRun(url, file);
			DHParameterKinematics newLimb = base.getAllDHChains().get(0);
			newLimb.setScriptingName(uniqueName);
			return newLimb;
		}
	}

	public static ArrayList<LimbOption> getOptions()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		try {
			Type TT_CaDoodleFile = new TypeToken<ArrayList<LimbOption>>() {
			}.getType();
			Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
					.excludeFieldsWithoutExposeAnnotation().create();
			File f = ScriptingEngine.fileFromGit(ControllerOption.URL_OF_OPTIONS, "limbOptions.json");
			String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
			return gson.fromJson(content, TT_CaDoodleFile);
		} catch (Exception ex) {
			ex.printStackTrace();
			return new ArrayList<LimbOption>();
		}
	}

	@Override
	public String toString() {
		return type + " " + name + " " + url + "/" + file + "\n\tConsumes:" + getConsumes() + "\n\tProvides:" + getProvides();
	}

	public ControllerFeatures getConsumes() {
		return consumes;
	}

	public ControllerFeatures getProvides() {
		return provides;
	}
}
