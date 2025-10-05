package com.neuronrobotics.bowlerstudio.assets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.*;
public class ConfigurationDatabase {

	//private static final String repo = "BowlerStudioConfiguration";
	//private static final String HTTPS_GITHUB_COM_NEURON_ROBOTICS_BOWLER_STUDIO_CONFIGURATION_GIT = "https://github.com/CommonWealthRobotics/"
	//		+ repo + ".git";

	//private static String gitSource = null; // madhephaestus
	private static String dbFile = "database.json";
	private static boolean checked;
	private static Map<String, HashMap<String, Object>> database = null;
	private static final Type TT_mapStringString = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
	}.getType();
	// chreat the gson object, this is the parsing factory
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static IssueReportingExceptionHandler reporter = new IssueReportingExceptionHandler();
	//private static String loggedInAs = null;

	public static void clear(String key) {
		getDatabase();
		synchronized(database){
			getParamMap(key).clear();
		}
	
	}
	public static Set<String> keySet(String name) {
		Set<String> keySet ;
		getDatabase();
		synchronized(database){
			keySet= ConfigurationDatabase.getParamMap(name).keySet();
		}
		return keySet;
	}
	public static boolean containsKey(String paramsKey, String string) {
		boolean containsKey = false;
		getDatabase();
		synchronized(database){
			containsKey = ConfigurationDatabase.getParamMap(paramsKey).containsKey(string);
		}
		return containsKey;

	}
	public static String getKeyFromValue(String controllerName, String mappedValue) {
		String ret=null;
		getDatabase();
		synchronized(database){
			HashMap<String, Object> paramMap = ConfigurationDatabase.getParamMap(controllerName);
			for (String key : paramMap.keySet()) {
				String string = (String) paramMap.get(key);
				if (string.contentEquals(mappedValue)) {
					ret= key;
					break;
				}
			}
		}
		return ret;
	}
	public static  Object get(String paramsKey, String objectKey) {
		return getObject(paramsKey, objectKey, null);
	}
	public static  Object get(String paramsKey, String objectKey, Object defaultValue) {
		return getObject(paramsKey, objectKey, defaultValue);
	}
	public static  Object getObject(String paramsKey, String objectKey, Object defaultValue) {
		Object ret=null;
		getDatabase();
		synchronized(database){
			if (getParamMap(paramsKey).get(objectKey) == null) {
				//com.neuronrobotics.sdk.common.Log.error("Cant find: " + paramsKey + ":" + objectKey);
				setObject(paramsKey, objectKey, defaultValue);
			}
			ret= getParamMap(paramsKey).get(objectKey);
		}
		return ret;
	}

	public static HashMap<String, Object> getParamMap(String paramsKey) {
		if (database.get(paramsKey) == null) {
			database.put(paramsKey, new HashMap<String, Object>());
		}
		return database.get(paramsKey);
	}
	public static Object put(String paramsKey, String objectKey, Object value) {
		return setObject(paramsKey, objectKey, value);
	}
	
	public static  Object setObject(String paramsKey, String objectKey, Object value) {
		Object put =null;
		getDatabase();
		synchronized(database){
			put=getParamMap(paramsKey).put(objectKey, value);
		}
		save();
		return put;
	}
	public static Object remove(String paramsKey, String objectKey) {
		return removeObject(paramsKey, objectKey);
	}
	public static  Object removeObject(String paramsKey, String objectKey) {
		Object remove=null;
		getDatabase();
		synchronized(database){
			remove= getParamMap(paramsKey).remove(objectKey);
		}
		save();
		return remove;
	}

	public static  void save() {
		String writeOut = null;
		getDatabase();
		synchronized(database){
			writeOut = gson.toJson(database, TT_mapStringString);
		}
		File f=loadFile();
		

		try (PrintWriter out = new PrintWriter(f.getAbsolutePath())) {
		    out.println(writeOut);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
			return;
		}
		//com.neuronrobotics.sdk.common.Log.error("Saved "+f.getName());
	}

	@SuppressWarnings("unchecked")
	public static void getDatabase() {
		if (database != null) {
			return ;
		}
		File loadFile = loadFile();
		if(loadFile.exists())
			try {
				Object inlineFileScriptRun = ScriptingEngine.inlineFileScriptRun(CSGDatabase.getInstance(),loadFile, null);
				database = Collections.synchronizedMap((HashMap<String, HashMap<String, Object>>) inlineFileScriptRun);
				
			} catch (Exception e) {
				// databse is empty
			}
		
		if (database == null) {
			database = new HashMap<String, HashMap<String, Object>>();
			// new Exception().printStackTrace();
		}

		return ;
	}

	public static File loadFile() {
		Path appDataDirectory = getAppDataDirectory();
		File dir = appDataDirectory.toFile();
		if(!dir.exists()) {
			dir.mkdirs();
		}
		File f = new File(appDataDirectory+"/ConfigurationDatabase.json");
		if(!f.exists()) {
			try {
				f.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e.getMessage());
			}
		}
		return f;
	}
	public static Path getAppDataDirectory() {
		String appName="CaDoodle";
		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("win")) {
			return getWindowsAppData(appName);
		} else if (os.contains("mac")) {
			return getMacAppData(appName);
		} else {
			return getLinuxAppData(appName);
		}
	}

	public static Path getWindowsAppData(String appName) {
		// Try LOCALAPPDATA first (safe, never synced to OneDrive)
		String localAppData = System.getenv("LOCALAPPDATA");
		if (localAppData != null && !localAppData.isEmpty()) {
			return Paths.get(localAppData, appName);
		}

		// Next try APPDATA
		String appData = System.getenv("APPDATA");
		if (appData != null && !appData.isEmpty()) {
			return ensureNoOneDrive(Paths.get(appData), appName);
		}

		// Fallback to user.home
		String userHome = System.getProperty("user.home");
		Path homePath = Paths.get(userHome);
		homePath = stripOneDrive(homePath); // sanitize
		return homePath.resolve("AppData").resolve("Local").resolve(appName);
	}

	private static Path ensureNoOneDrive(Path path, String appName) {
		Path sanitized = stripOneDrive(path);
		return sanitized.resolve(appName);
	}

	private static Path stripOneDrive(Path path) {
		// Look for "OneDrive" component in the path and cut everything after it
		for (int i = 0; i < path.getNameCount(); i++) {
			if (path.getName(i).toString().equalsIgnoreCase("OneDrive")) {
				// Return path up to but not including "OneDrive"
				return path.getRoot().resolve(path.subpath(0, i));
			}
		}
		return path;
	}

	private static Path getMacAppData(String appName) {
		String userHome = System.getProperty("user.home");
		return Paths.get(userHome, "Library", "Application Support", appName);
	}

	private static Path getLinuxAppData(String appName) {
		// Follow XDG Base Directory Specification
		String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
		if (xdgConfigHome != null && !xdgConfigHome.isEmpty()) {
			return Paths.get(xdgConfigHome, appName);
		}

		String userHome = System.getProperty("user.home");
		return Paths.get(userHome, ".config", appName);
	}

	public static void ensureDirectoryExists(Path directory) {
		try {
			Files.createDirectories(directory);
		} catch (IOException e) {
			throw new RuntimeException("Failed to create app data directory: " + directory, e);
		}
	}




}