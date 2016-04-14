package com.neuronrobotics.bowlerstudio.vitamins;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.neuronrobotics.imageprovider.NativeResource;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.STL;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import javafx.scene.paint.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
public class Vitamins {
	
	private static final Map<String,CSG> fileLastLoaded = new HashMap<String,CSG>();
	private static final Map<String,HashMap<String,HashMap<String,Object>>> databaseSet = 
			new HashMap<String, HashMap<String,HashMap<String,Object>>>();
	private static String gitRpoDatabase = "https://github.com/madhephaestus/Hardware-Dimensions.git";
	//Create the type, this tells GSON what datatypes to instantiate when parsing and saving the json
	private static Type TT_mapStringString = new TypeToken<HashMap<String,HashMap<String,Object>>>(){}.getType();
	//chreat the gson object, this is the parsing factory
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static boolean checked;

	public static CSG get(File resource ){
		
		if(fileLastLoaded.get(resource.getAbsolutePath()) ==null ){
			// forces the first time the files is accessed by the application tou pull an update
			try {
				fileLastLoaded.put(resource.getAbsolutePath(), STL.file(resource.toPath()) );
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return fileLastLoaded.get(resource.getAbsolutePath()).clone() ;
	}
	
	public static CSG get(String type,String id) throws Exception{
		
		if(fileLastLoaded.get(type+id) ==null ){
			CSG newVitamin=null;
			HashMap<String, Object> script = getMeta( type);
			ArrayList<Object> servoMeasurments = new ArrayList<Object>();
			servoMeasurments.add(id);
			newVitamin=(CSG)ScriptingEngine
            .gitScriptRun(
            		script.get("scriptGit").toString(), // git location of the library
            		script.get("scriptFile").toString(), // file to load
                      servoMeasurments
            );
			
			fileLastLoaded.put(type+id, newVitamin );

		}
		return fileLastLoaded.get(type+id).clone() ;
	}
	
	
	
	public static HashMap<String, Object> getMeta(String type){
		return getConfiguration(type,"meta");
	}
	public static void setScript(String type, String git, String file) throws Exception{
		setParameter(type,"meta","scriptGit",git);
		setParameter(type,"meta","scriptFile",file);
	}
	public static HashMap<String, Object> getConfiguration(String type,String id){
		HashMap<String, HashMap<String, Object>> database = getDatabase(type);
		if(database.get(id)==null){
			database.put(id, new  HashMap<String, Object>());
		}
		if(database.get(id).isEmpty()){
			//check to see if this is emptybecause it is a new file in the upstream database
			databaseSet.remove(type);
			database =  getDatabase(type);
		}
		return database.get(id);
	}
	
	public static void saveDatabase(String type) throws Exception{
		
		// Save contents and publish them
		String jsonString = gson.toJson(getDatabase( type), TT_mapStringString); 
		try{
			ScriptingEngine.pushCodeToGit(
					getGitRpoDatabase() ,// git repo, change this if you fork this demo
				"master", // branch or tag
				"json/"+type+".json", // local path to the file in git
				jsonString, // content of the file
				"Pushing changed Database");//commit message
			
		}catch(org.eclipse.jgit.api.errors.TransportException ex){
			System.out.println("You need to fork "+getGitRpoDatabase() +" to have permission to save");
			System.out.println("You do not have permission to push to this repo, change the GIT repo to your fork with setGitRpoDatabase(String gitRpoDatabase) ");
			throw ex;
		}

	}
	
	public static void newVitamin(String type, String id) throws Exception{
		HashMap<String, HashMap<String, Object>> database = getDatabase(type);
		if(database.keySet().size()>0){
			String exampleKey =null;
			for(String key: database.keySet()){
				if(!key.contains("meta")){
					exampleKey=key;
				}
			}
			if(exampleKey!=null){
				// this database has examples, load an example
				HashMap<String, Object> exampleConfiguration = getConfiguration(type,exampleKey);
				HashMap<String, Object> newConfig = getConfiguration( type, id);
				for(String key: exampleConfiguration.keySet()){
					newConfig.put(key, exampleConfiguration.get(key));
				}
			}
		}
		
		getConfiguration( type, id);
		//saveDatabase(type);
		
	}
	
	public static void setParameter(String type, String id, String parameterName, Object parameter) throws Exception{
		
		HashMap<String, Object> config = getConfiguration( type, id);
		config.put(parameterName, parameter);
		//saveDatabase(type);
	}
	
	public static HashMap<String,HashMap<String,Object>> getDatabase(String type){
		if(databaseSet.get(type)==null){
			// we are using the default vitamins configuration
			//https://github.com/madhephaestus/Hardware-Dimensions.git
	
			// create some variables, including our database
			String jsonString;
			InputStream inPut = null;
	
			// attempt to load the JSON file from the GIt Repo and pars the JSON string
			File f;
			try {
				f = ScriptingEngine
										.fileFromGit(
												getGitRpoDatabase(),// git repo, change this if you fork this demo
											"json/"+type+".json"// File from within the Git repo
										);
				inPut = FileUtils.openInputStream(f);
				
				jsonString= IOUtils.toString(inPut);
				// perfoem the GSON parse
				HashMap<String,HashMap<String,Object>> database=gson.fromJson(jsonString, TT_mapStringString);
	
				databaseSet.put(type, database);
				
			} catch (Exception e) {
				databaseSet.put(type, new HashMap<String,HashMap<String,Object>>());
			}
		}
		return databaseSet.get(type);

	}
	
	public static ArrayList<String> listVitaminTypes(){
		
		ArrayList<String> types = new ArrayList<String>();
		File folder;
		try {
			folder = ScriptingEngine
					.fileFromGit(
							getGitRpoDatabase() ,// git repo, change this if you fork this demo
						"json/hobbyServo.json"// File from within the Git repo
					);
			File[] listOfFiles = folder.getParentFile().listFiles();
			
			for(File f:listOfFiles){
				if(!f.isDirectory() && f.getName().endsWith(".json"))
					types.add(f.getName().substring(0, f.getName().indexOf(".json")));
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return types;
	}
	
	public static ArrayList<String> listVitaminSizes(String type){
		
		ArrayList<String> types = new ArrayList<String>();
		HashMap<String, HashMap<String, Object>> database = getDatabase( type);
		Set<String> keys = database.keySet();
		for(String s:keys){
			if(!s.contains("meta"))
				types.add(s);
		}
		
		return types;
	}
	
	
	public static String getGitRpoDatabase() throws IOException {
		if(!checked){
			checked=true;
			ScriptingEngine.setAutoupdate(true);
			org.kohsuke.github.GitHub github = ScriptingEngine.getGithub();
			GHMyself self = github.getMyself();
			Map<String, GHRepository> myPublic = self.getAllRepositories();
			for (String myRepo :myPublic.keySet()){
				if(myRepo.contentEquals("Hardware-Dimensions")){
					GHRepository ghrepo= myPublic.get(myRepo);
					String myAssets = ghrepo.getGitTransportUrl().replaceAll("git://", "http://");
					System.out.println("Using my version of Viamins: "+myAssets);
					setGitRpoDatabase(myAssets);
				}
			}
		}
		return gitRpoDatabase;
	}
	public static void setGitRpoDatabase(String gitRpoDatabase) {
		Vitamins.gitRpoDatabase = gitRpoDatabase;
	}
	
	
	
}
