package com.neuronrobotics.bowlerstudio.assets;

import java.io.File;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

public class ConfigurationDatabase {

  private static final String repo = "BowlerStudioConfiguration";
  private static final String HTTPS_GITHUB_COM_NEURON_ROBOTICS_BOWLER_STUDIO_CONFIGURATION_GIT =
      "https://github.com/CommonWealthRobotics/" + repo + ".git";

  private static String gitSource = null; // madhephaestus
  private static String dbFile = "database.json";
  private static boolean checked;
  private static HashMap<String, HashMap<String, Object>> database = null;
  private static final Type TT_mapStringString = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
  }.getType();
  //chreat the gson object, this is the parsing factory
  private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();


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
    //synchronized(database){
    writeOut = gson.toJson(database, TT_mapStringString);
    //}
    try {
      ScriptingEngine
          .pushCodeToGit(getGitSource(), ScriptingEngine.getFullBranch(getGitSource()), getDbFile(),
              writeOut, "Saving database");
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  @SuppressWarnings("unchecked")
  public static HashMap<String, HashMap<String, Object>> getDatabase() {
    if (database != null) {
      return database;
    }
    try {
    	
      database = (HashMap<String, HashMap<String, Object>>) ScriptingEngine
          .inlineFileScriptRun(loadFile(), null);

    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    if (database == null) {
      database = new HashMap<String, HashMap<String, Object>>();
    }
    return database;
  }

  public static File loadFile() throws Exception {
    return ScriptingEngine.fileFromGit(getGitSource(), // git repo, change
        getDbFile()
    );
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
      checked = true;
      if (ScriptingEngine.hasNetwork() && ScriptingEngine.isLoginSuccess()) {

        ScriptingEngine.setAutoupdate(true);
        org.kohsuke.github.GitHub github = PasswordManager.getGithub();
        GHMyself self = github.getMyself();
        Map<String, GHRepository> myPublic = self.getAllRepositories();
        gitSource=null;
        for (Map.Entry<String, GHRepository> entry : myPublic.entrySet()) {
          if (entry.getKey().contentEquals(repo) && entry.getValue().getOwnerName()
              .equals(self.getName())) {
            GHRepository ghrepo = entry.getValue();
            setRepo(ghrepo);
          }
        }
        if (gitSource == null) {
          GHRepository defaultRep = github.getRepository("CommonWealthRobotics/" + repo);
          GHRepository forkedRep = defaultRep.fork();
          setRepo(forkedRep);
        }
      } else {
    	  if (PasswordManager.getUsername()  != null) {
    	        ConfigurationDatabase
                .setGitSource("https://github.com/"+PasswordManager.getUsername()+"/" + repo + ".git");
			} else {
		        ConfigurationDatabase
	            .setGitSource(HTTPS_GITHUB_COM_NEURON_ROBOTICS_BOWLER_STUDIO_CONFIGURATION_GIT);
			}

      }
     
	  ScriptingEngine.pull(gitSource);
    }
    return gitSource;

  }

  private static void setRepo(GHRepository forkedRep) {
    String myAssets = forkedRep.getGitTransportUrl().replaceAll("git://", "https://");
    setGitSource(myAssets);
  }

  public static void setGitSource(String myAssets) {
	System.out.println("Using my version of configuration database: " + myAssets);
    database = null;
    gitSource = myAssets;
    getDatabase();
  }

  public static String getDbFile() {
    return dbFile;
  }

  public static void setDbFile(String dbFile) {
    ConfigurationDatabase.dbFile = dbFile;
    setGitSource(gitSource);
  }
}