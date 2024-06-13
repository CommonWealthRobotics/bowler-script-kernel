package com.neuronrobotics.bowlerstudio.assets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.IssueReportingExceptionHandler;
import com.neuronrobotics.bowlerstudio.scripting.IGithubLoginListener;
import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import java.nio.charset.*;
import org.apache.commons.io.*;
public class ConfigurationDatabase {

	//private static final String repo = "BowlerStudioConfiguration";
	//private static final String HTTPS_GITHUB_COM_NEURON_ROBOTICS_BOWLER_STUDIO_CONFIGURATION_GIT = "https://github.com/CommonWealthRobotics/"
	//		+ repo + ".git";

	//private static String gitSource = null; // madhephaestus
	private static String dbFile = "database.json";
	private static boolean checked;
	private static HashMap<String, HashMap<String, Object>> database = null;
	private static final Type TT_mapStringString = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
	}.getType();
	// chreat the gson object, this is the parsing factory
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static IssueReportingExceptionHandler reporter = new IssueReportingExceptionHandler();
	//private static String loggedInAs = null;
	static {

	}

	public static synchronized Object getObject(String paramsKey, String objectKey, Object defaultValue) {
		if (getParamMap(paramsKey).get(objectKey) == null) {
			//System.err.println("Cant find: " + paramsKey + ":" + objectKey);
			setObject(paramsKey, objectKey, defaultValue);
		}
		return getParamMap(paramsKey).get(objectKey);
	}

	public static HashMap<String, Object> getParamMap(String paramsKey) {
		if (getDatabase().get(paramsKey) == null) {
			getDatabase().put(paramsKey, new HashMap<String, Object>());
		}
		return getDatabase().get(paramsKey);
	}

	public static synchronized Object setObject(String paramsKey, String objectKey, Object value) {
		Object put = getParamMap(paramsKey).put(objectKey, value);
		save();
		return put;
	}

	public static synchronized Object removeObject(String paramsKey, String objectKey) {
		Object remove = getParamMap(paramsKey).remove(objectKey);
		save();
		return remove;
	}

	public static synchronized void save() {
		String writeOut = null;
		getDatabase();
		//synchronized(database){
			writeOut = gson.toJson(database, TT_mapStringString);
		//}
		File f=loadFile();
		

		try (PrintWriter out = new PrintWriter(f.getAbsolutePath())) {
		    out.println(writeOut);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		System.out.println("Saved "+f.getName());
	}

	@SuppressWarnings("unchecked")
	public static HashMap<String, HashMap<String, Object>> getDatabase() {
		if (database != null) {
			return database;
		}
		File loadFile = loadFile();
		if(loadFile.exists())
			try {
				database = (HashMap<String, HashMap<String, Object>>) ScriptingEngine.inlineFileScriptRun(loadFile, null);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		if (database == null) {
			database = new HashMap<String, HashMap<String, Object>>();
			// new Exception().printStackTrace();
		}

		return database;
	}

	public static File loadFile() {
		File f = new File(ScriptingEngine.getWorkspace().getAbsolutePath()+"/ConfigurationDatabase.json");
		if(!f.exists()) {
			try {
				f.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e.getMessage());
			}
			String username = PasswordManager.getLoginID();
		    try {
				File file =ScriptingEngine.fileFromGit("https://github.com/"+username+"/BowlerStudioConfiguration.git", "database.json");
				if(file.exists()) {
					String contents= FileUtils.readFileToString(file, StandardCharsets.UTF_8);
					try (PrintWriter out = new PrintWriter(f.getAbsolutePath())) {
					    out.println(contents);
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return f;
	}



}