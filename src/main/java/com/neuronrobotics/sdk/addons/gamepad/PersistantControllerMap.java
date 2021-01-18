package com.neuronrobotics.sdk.addons.gamepad;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;

import org.kohsuke.github.GHRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

public class PersistantControllerMap {

	private static final String repo = "GameControllerMap";
	private static final String HTTPS_GITHUB_COM_NEURON_ROBOTICS_BOWLER_STUDIO_CONFIGURATION_GIT = "https://github.com/CommonWealthRobotics/"
			+ repo + ".git";

	private static String gitSource = null; // madhephaestus
	private static String dbFile = "map.json";
	private static boolean checked;
	private static HashMap<String, HashMap<String, Object>> database = null;
	private static final Type TT_mapStringString = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
	}.getType();
	// chreat the gson object, this is the parsing factory
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

	public static String getMappedAxisName(String controllerName, String incomingName) {
		Object object = getParamMap(controllerName).get(incomingName);
		if (object == null)
			return incomingName;
		return (String) object;
	}

	public static boolean isMapedAxis(String controllerName, String mappedValue) {
		return getMapedAxis(controllerName, mappedValue) != null;
	}

	public static String getMapedAxis(String controllerName, String mappedValue) {
		HashMap<String, Object> paramMap = getParamMap(controllerName);
		for (String key : paramMap.keySet()) {
			String string = (String) paramMap.get(key);
			if (string.contentEquals(mappedValue)) {
				return key;
			}
		}
		return null;
	}

	public static Object getObject(String paramsKey, String objectKey, Object defaultValue) {
		if (getParamMap(paramsKey).get(objectKey) == null) {
			System.err.println("Cant find: " + paramsKey + ":" + objectKey);
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

	public static Object setObject(String paramsKey, String objectKey, Object value) {
		return getParamMap(paramsKey).put(objectKey, value);
	}

	public static Object removeObject(String paramsKey, String objectKey) {
		return getParamMap(paramsKey).remove(objectKey);
	}

	public static void save() {
		String writeOut = null;
		getDatabase();
		// synchronized(database){
		writeOut = gson.toJson(database, TT_mapStringString);
		// }
		for (int i = 0; i < 5; i++) {
			try {
				ScriptingEngine.pushCodeToGit(getGitSource(), ScriptingEngine.getFullBranch(getGitSource()),
						getDbFile(), writeOut, "Saving database");
				return;
			} catch (Exception e) {
				try {
					ScriptingEngine.deleteRepo(getGitSource());
					Thread.sleep(500);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}

		}

	}

	@SuppressWarnings("unchecked")
	public static HashMap<String, HashMap<String, Object>> getDatabase() {
		if (database != null) {
			return database;
		}
		try {
			ScriptingEngine.pull(getGitSource());
			database = (HashMap<String, HashMap<String, Object>>) ScriptingEngine.inlineFileScriptRun(loadFile(), null);
			// new Exception().printStackTrace();
		} catch (Exception e) {
			// oignore and use new one
		}
		if (database == null) {
			database = new HashMap<String, HashMap<String, Object>>();
			// new Exception().printStackTrace();
		}
		return database;
	}

	public static File loadFile() throws Exception {
		return ScriptingEngine.fileFromGit(getGitSource(), // git repo, change
				getDbFile());
	}

	public static void loginEvent(String username) {
		checked = false;
		try {
			getGitSource();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static String getGitSource() throws Exception {
		if (!checked) {
			if (ScriptingEngine.hasNetwork() && ScriptingEngine.isLoginSuccess()) {
				org.kohsuke.github.GitHub github = PasswordManager.getGithub();
				try {
					GHRepository myConfigRepo = github.getRepository(PasswordManager.getLoginID() + "/" + repo);
					setRepo(myConfigRepo);
					checked = true;
				} catch (Throwable t) {
					if (gitSource == null) {
						GHRepository defaultRep = github.getRepository("CommonWealthRobotics/" + repo);
						GHRepository forkedRep = defaultRep.fork();
						setRepo(forkedRep);
						checked = true;
					}
					if (PasswordManager.getUsername() != null) {
						setGitSource("https://github.com/" + PasswordManager.getUsername() + "/" + repo + ".git");
					} else {
						setGitSource(HTTPS_GITHUB_COM_NEURON_ROBOTICS_BOWLER_STUDIO_CONFIGURATION_GIT);
					}

				}

				// ScriptingEngine.pull(gitSource);
			} else if (ScriptingEngine.isLoginSuccess()) {
				// no network but has logged in before
				setGitSource("https://github.com/" + PasswordManager.getUsername() + "/" + repo + ".git");
			}

		}
		return gitSource;

	}

	private static void setRepo(GHRepository forkedRep) {
		String myAssets = forkedRep.getGitTransportUrl().replaceAll("git://", "https://");
		setGitSource(myAssets);
	}

	public static void setGitSource(String myAssets) {
		System.out.println("Using my version of configuration database: " + myAssets);
		if (myAssets != null && gitSource != null && myAssets.contentEquals(gitSource))
			return;
		database = null;
		// new Exception("Changing from "+gitSource+" to "+myAssets).printStackTrace();
		checked = false;
		gitSource = myAssets;
		getDatabase();
	}

	public static String getDbFile() {
		return dbFile;
	}

	public static void setDbFile(String d) {
		dbFile = d;
		setGitSource(gitSource);
	}
}
