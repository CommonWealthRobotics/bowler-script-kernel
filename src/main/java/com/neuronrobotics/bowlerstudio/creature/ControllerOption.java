package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

public class ControllerOption {
	public static final String URL_OF_OPTIONS = "https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git";
	@Expose(serialize = true, deserialize = true)
	String type;
	@Expose(serialize = true, deserialize = true)
	String imageGit;
	@Expose(serialize = true, deserialize = true)
	String imageFile;
	@Expose(serialize = true, deserialize = true)
	List<String> vitaminType;
	@Expose(serialize = true, deserialize = true)
	List<String> vitaminSize;
	@Expose(serialize = true, deserialize = true)
	String linkLoaderGit;
	@Expose(serialize = true, deserialize = true)
	String linkLoaderFile;
	@Expose(serialize = true, deserialize = true)
	String linkDeviceName;
	@Expose(serialize = true, deserialize = true)
	String linkDeviceType;
	@Expose(serialize = true, deserialize = true)
	String firmwareGit;
	@Expose(serialize = true, deserialize = true)
	String firmwareFile;
	@Expose(serialize = true, deserialize = true)
	ControllerFeatures provides;
	@Expose(serialize = true, deserialize = true)
	ControllerFeatures consumes;

	public File getImage() throws FileNotFoundException {
		try {
			return ScriptingEngine.fileFromGit(imageGit, imageFile);
		} catch (InvalidRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new FileNotFoundException();
	}

	public void runLinkLoader() throws FileNotFoundException {
		if (linkLoaderGit == null || linkLoaderFile == null) {
			System.out.println("Using built in link loaders");
			return;
		}
		try {
			ScriptingEngine.inlineGistScriptRun(linkLoaderGit, linkLoaderFile, null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new FileNotFoundException(linkLoaderGit + "/" + linkLoaderFile);
	}

	public File getFirmware() throws FileNotFoundException {
		try {
			return ScriptingEngine.fileFromGit(firmwareGit, firmwareFile);
		} catch (InvalidRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new FileNotFoundException();
	}

	public String getType() {
		return type;
	}

	public static ArrayList<ControllerOption> getOptions()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		try {
			Type TT_CaDoodleFile = new TypeToken<ArrayList<ControllerOption>>() {
			}.getType();
			Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
					.excludeFieldsWithoutExposeAnnotation().create();

			File f = ScriptingEngine.fileFromGit(URL_OF_OPTIONS, "controllerOptions.json");
			String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
			return gson.fromJson(content, TT_CaDoodleFile);
		} catch (Exception ex) {
			ex.printStackTrace();
			return new ArrayList<ControllerOption>();
		}
	}

	public String getLinkDeviceName() {
		return linkDeviceName;
	}

	public List<String> getVitaminType() {
		return vitaminType;
	}

	public List<String> getVitaminSize() {
		return vitaminSize;
	}

	public String getLinkDeviceType() {
		return linkDeviceType;
	}
}
