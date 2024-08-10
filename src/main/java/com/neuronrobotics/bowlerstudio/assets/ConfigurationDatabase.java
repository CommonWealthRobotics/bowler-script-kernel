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
import java.nio.charset.*;
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
				//System.err.println("Cant find: " + paramsKey + ":" + objectKey);
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
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
		System.out.println("Saved "+f.getName());
	}

	@SuppressWarnings("unchecked")
	public static void getDatabase() {
		if (database != null) {
			return ;
		}
		File loadFile = loadFile();
		if(loadFile.exists())
			try {
				database = Collections.synchronizedMap((HashMap<String, HashMap<String, Object>>) ScriptingEngine.inlineFileScriptRun(loadFile, null));
				
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
		File f = new File(ScriptingEngine.getWorkspace().getAbsolutePath()+"/ConfigurationDatabase.json");
		if(!f.exists()) {
			try {
				f.createNewFile();
			} catch (IOException e) {
				throw new RuntimeException(e.getMessage());
			}
			if(!PasswordManager.isAnonMode()) {
				String username = PasswordManager.getLoginID();
				if(username!=null)
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
		}
		return f;
	}




}