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
	ControllerFeatures consumes;
	@Expose(serialize = true, deserialize = true)
	ControllerFeatures provides;
	
	public DHParameterKinematics getLimb() throws Exception {
		String xmlContent = ScriptingEngine.codeFromGit(url, file)[0];
		DHParameterKinematics newLimb = new DHParameterKinematics(null, IOUtils.toInputStream(xmlContent, "UTF-8"));
		newLimb.setScriptingName(name);
		return newLimb;
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
		return type+" "+name+" "+url+"/"+file+"\n\tConsumes:"+consumes+"\n\tProvides:"+provides;
	}
}
