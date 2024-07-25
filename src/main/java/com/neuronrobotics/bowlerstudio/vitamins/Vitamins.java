package com.neuronrobotics.bowlerstudio.vitamins;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.STL;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.IssueReportingExceptionHandler;
import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;
//import com.neuronrobotics.bowlerstudio.BowlerStudio;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
//import com.neuronrobotics.bowlerstudio.util.FileChangeWatcher;
//import com.neuronrobotics.bowlerstudio.util.IFileChangeListener;
//import com.neuronrobotics.bowlerstudio.util.FileChangeWatcher;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.TransportException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class Vitamins {

	private static String jsonRootDir = "json/";
	private static final Map<String, CSG> fileLastLoaded = new HashMap<String, CSG>();
	private static final Map<String, HashMap<String, HashMap<String, Object>>> databaseSet = new HashMap<String, HashMap<String, HashMap<String, Object>>>();
	private static final String defaultgitRpoDatabase = "https://github.com/madhephaestus/Hardware-Dimensions.git";
	private static String gitRpoDatabase = defaultgitRpoDatabase;
	// Create the type, this tells GSON what datatypes to instantiate when parsing
	// and saving the json
	private static Type TT_mapStringString = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
	}.getType();
	// chreat the gson object, this is the parsing factory
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static boolean checked;
	private static HashMap<String,Runnable> changeListeners = new HashMap<String, Runnable>();
	public static void clear() {
		System.out.println("Vitamins Database Cleraing, reloading files");
		for(String keys:databaseSet.keySet()) {
			HashMap<String, HashMap<String, Object>> data = databaseSet.get(keys);
			for(String key2:data.keySet()) {
				HashMap<String, Object> data2 = data.get(key2);
				data2.clear();
			}
			data.clear();
		}
		databaseSet.clear();
		fileLastLoaded.clear();
	}
	public static CSG get(File resource) {
		return get(resource,false);
	}
	public static CSG get(File resource, boolean forceRefresh) {

		if (fileLastLoaded.get(resource.getAbsolutePath()) == null||forceRefresh) {
			// forces the first time the files is accessed by the application tou pull an
			// update
			try {
				fileLastLoaded.put(resource.getAbsolutePath(), STL.file(resource.toPath()));
//				try {
//					FileChangeWatcher f = FileChangeWatcher.watch(resource);
//					f.addIFileChangeListener(new IFileChangeListener() {
//						@Override
//						public void onFileDelete(File fileThatIsDeleted) {
//							fileLastLoaded.remove(resource.getAbsolutePath());
//							f.close();
//						}
//						
//						@Override
//						public void onFileChange(File fileThatChanged, WatchEvent event) {
//							fileLastLoaded.remove(resource.getAbsolutePath());	
//							f.close();
//						}
//					});
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return fileLastLoaded.get(resource.getAbsolutePath()).clone();
	}

	public static CSG get(String type, String id, String purchasingVariant) throws Exception {
		String key = type + id + purchasingVariant;
		if (fileLastLoaded.get(key) == null) {
			PurchasingData purchasData = Purchasing.get(type, id, purchasingVariant);
			for (String variable : purchasData.getVariantParameters().keySet()) {
				double data = purchasData.getVariantParameters().get(variable);
				LengthParameter parameter = new LengthParameter(variable, data,
						(ArrayList<Double>) Arrays.asList(data, data));
				parameter.setMM(data);
			}

			try {
				fileLastLoaded.put(key, get(type, id));
			} catch (Exception e) {
				e.printStackTrace();

				gitRpoDatabase = defaultgitRpoDatabase;
				clear();
				return get(type, id);
			}

		}

		CSG vitToGet = fileLastLoaded.get(type + id);
		// System.err.println("Loading "+vitToGet);
		return vitToGet;
	}

	public static boolean isGitURL(String text2) {
		if (!text2.endsWith(".git"))
			return false;
		if (text2.length() <= 5)
			return false;
		try {
			new java.net.URL(text2);
		} catch (Exception ex) {
			if (!text2.startsWith("git@")) {
				ex.printStackTrace();
				return false ;
			}
		}
		return true;
	}
	public static void flatten(ArrayList<CSG> flat, Object o) {
		ScriptingEngine.flatten(flat, o);
	}
	
	public static CSG get(String type, String id) throws Exception {
		if(isGitURL(type)) {
			Object o =ScriptingEngine.gitScriptRun(type, id);
			ArrayList<CSG> flat= new ArrayList<CSG>();
			Vitamins.flatten(flat,o);
			return  CSG.unionAll( flat);
		}
		return get(type, id, 0);
	}

	private static CSG get(String type, String id, int depthGauge) throws Exception {
		String key = type + id;

		try {
			CSG newVitamin = null;
			Map<String, Object> script = getMeta(type);
			StringParameter size = new StringParameter(type + " Default", id, Vitamins.listVitaminSizes(type));
			size.setStrValue(id);
			Object file = script.get("scriptGit");
			Object repostring = script.get("scriptFile");
			Object repo = repostring;
			if (file != null && repo != null) {
				ArrayList<Object> servoMeasurments = new ArrayList<Object>();
				servoMeasurments.add(id);
				newVitamin = (CSG) ScriptingEngine.gitScriptRun(script.get("scriptGit").toString(), // git location of
																									// the library
						repostring.toString(), // file to load
						servoMeasurments);
				Map<String, Object> configuration = Vitamins.getConfiguration(type, id);
				newVitamin.setName(type +"-"+ id);
				newVitamin.setManufacturing(incoming -> null);
				try {
					Transform com = new Transform()
										.movex(Double.parseDouble(configuration.get("massCentroidX").toString()))
										.movey(Double.parseDouble(configuration.get("massCentroidY").toString()))
										.movez(Double.parseDouble(configuration.get("massCentroidZ").toString()));
					newVitamin.getStorage().set("massKg", configuration.get("massKg"));
					newVitamin.getStorage().set("massCentroid", com);
					
					return newVitamin;
				}catch(Exception ex) {
					//System.err.println(type +"-"+ id+" Failed");
					//ex.printStackTrace();
					return newVitamin;
				}
			} else {
				Log.error(key + " Failed to load from script");
				return null;
			}
		} catch (Exception e) {
			e.printStackTrace();
			gitRpoDatabase = defaultgitRpoDatabase;
			clear();
			if (depthGauge < 2) {
				return get(type, id, depthGauge + 1);
			} else {
				return null;
			}
		}
	}
	public static String getScriptGitURL(String type) {
		Map<String, Object> script = getMeta(type);
		
	
		return script.get("scriptGit").toString();

	}
	public static File getScriptFile(String type) {
		Map<String, Object> script = getMeta(type);
		
		try {
			return ScriptingEngine.fileFromGit(script.get("scriptGit").toString(), script.get("scriptFile").toString());
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
		return null;
	}

	public static Map<String, Object> getMeta(String type) {
		return getConfigurationRW(type, "meta");
	}

	public static void setScript(String type, String git, String file) throws Exception {
		setParameter(type, "meta", "scriptGit", git);
		setParameter(type, "meta", "scriptFile", file);
	}
	
	public static Map<String, Object> getConfiguration(String type, String id){
		return Collections.unmodifiableMap(getConfigurationRW( type,  id));
	}
	public static void putMeasurment(String type, String size,String measurementName, Object measurmentValue) {
		getConfigurationRW(type,size).put(measurementName, measurmentValue);
	}
	public static Object getMeasurement(String type, String size,String measurementName) {
		return getConfigurationRW(type,size).get(measurementName);
	}
	public static HashMap<String, Object> getConfigurationRW(String type, String id) {
		HashMap<String, HashMap<String, Object>> database = getDatabase(type);
		if (database.get(id) == null) {
			database.put(id, new HashMap<String, Object>());
		}
		for(int j=0;j<5;j++) {
			try {
				HashMap<String, Object> hashMap = database.get(id);
				Object[] array = hashMap.keySet().toArray();
				for (int i=0;i<array.length;i++) {
					String key = (String)array[i];
					sanatize(key,  hashMap);
				}
				return hashMap;
			}catch (java.util.ConcurrentModificationException ex) {
				if(j==4) {
					new IssueReportingExceptionHandler().except(ex);
				}else {
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}
		return new HashMap<String, Object>();
	}

	public static String makeJson(String type) {
		return gson.toJson(getDatabase(type), TT_mapStringString);
	}

	public static void saveDatabase(String type) throws Exception {

		// Save contents and publish them
		String jsonString = makeJson(type);
		try {
			//new Exception().printStackTrace();
			ScriptingEngine.pushCodeToGit(getGitRepoDatabase(), // git repo, change this if you fork this demo
					ScriptingEngine.getFullBranch(getGitRepoDatabase()), // branch or tag
					getRootFolder() + type + ".json", // local path to the file in git
					jsonString, // content of the file
					"Making changes to "+type+" by "+PasswordManager.getUsername()+"\n\nAuto-save inside com.neuronrobotics.bowlerstudio.vitamins.Vitamins inside bowler-scripting-kernel");// commit message
			//System.err.println(jsonString);
			System.out.println("Database saved "+getVitaminFile(type,null,false).getAbsolutePath());
		} catch (org.eclipse.jgit.api.errors.TransportException ex) {
			System.out.println("You need to fork " + defaultgitRpoDatabase + " to have permission to save");
			System.out.println(
					"You do not have permission to push to this repo, change the GIT repo to your fork with setGitRpoDatabase(String gitRpoDatabase) ");
			throw ex;
		}

	}
	public static void saveDatabaseForkIfMissing(String type) throws Exception {

		org.kohsuke.github.GitHub github = PasswordManager.getGithub();
		GHRepository repo = github.getRepository("madhephaestus/Hardware-Dimensions");
		try {
			saveDatabase(type);
		} catch (org.eclipse.jgit.api.errors.TransportException ex) {
			System.err.println("Forked repo is missing!");
			
			GHRepository newRepo = repo.fork();
			Thread.sleep(6000);
			Vitamins.gitRpoDatabase = newRepo.getGitTransportUrl().replaceAll("git://", "https://");
			saveDatabase(type);
			
		}
		if(PasswordManager.getUsername().contentEquals("madhephaestus"))
			return;
		try {
			GHRepository myrepo = github.getRepository(PasswordManager.getUsername()+"/Hardware-Dimensions");
			List<GHPullRequest> asList1 = myrepo.queryPullRequests().state(GHIssueState.OPEN).head("madhephaestus:master")
			            .list().asList();
			Thread.sleep(200);// Some asynchronus delay here, not sure why...
			if(asList1.size()==0) {
				try {
					GHPullRequest request = myrepo.createPullRequest("Update from source", 
							"madhephaestus:master", 
							"master", 
							"## Upstream add vitamins", 
							false, false);
					if(request!=null) {
						processSelfPR(request);
					}
				}catch(org.kohsuke.github.HttpException ex) {
					// no commits have been made to master
				}
				
			}else {
				processSelfPR(asList1.get(0));
			}
			String head = PasswordManager.getUsername()+":master";
			List<GHPullRequest> asList = repo.queryPullRequests()
		            .state(GHIssueState.OPEN)
		            .head(head)
		            .list().asList();
			if(asList.size()==0) {
				System.err.println("Creating PR for "+head);
				GHPullRequest request = repo.createPullRequest("User Added vitamins to "+type, 
					head, 
					"master", 
					"## User added vitamins", 
					true, true);
				try {
					BowlerKernel.upenURL(request.getHtmlUrl().toURI());
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else {
				
			}
		}catch(Exception ex) {
			new IssueReportingExceptionHandler().uncaughtException(Thread.currentThread(),ex);
		}
	

	}

	private static void processSelfPR(GHPullRequest request) throws IOException {
		if(request== null)
			return;
		try {
			if (request.getMergeable()) {
				request.merge("Auto Merging Master");
				reLoadDatabaseFromFiles();
				System.out.println("Merged Hardware-Dimensions madhephaestus:master into "+PasswordManager.getUsername()+":master");
			} else {
				try {
					BowlerKernel.upenURL(request.getHtmlUrl().toURI());
				} catch (URISyntaxException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}catch(java.lang.NullPointerException ex) {
			ex.printStackTrace();
		}
	}
	public static void newVitamin(String type, String id) throws Exception {
		HashMap<String, HashMap<String, Object>> database = getDatabase(type);
		if (database.keySet().size() > 0) {
			String exampleKey = null;
			for (String key : database.keySet()) {
				if (!key.contains("meta")) {
					exampleKey = key;
				}
			}
			if (exampleKey != null) {
				// this database has examples, load an example
				Map<String, Object> exampleConfiguration = getConfigurationRW(type, exampleKey);
				Map<String, Object> newConfig = getConfigurationRW(type, id);
				for (String key : exampleConfiguration.keySet()) {
					newConfig.put(key, exampleConfiguration.get(key));
				}
			}
		}

		getConfigurationRW(type, id);
		// saveDatabase(type);

	}

	public static void setParameter(String type, String id, String parameterName, Object parameter) throws Exception {

		HashMap<String, Object> config = getConfigurationRW(type, id);
		config.put(parameterName, parameter);
		sanatize(parameterName,  config);

		// saveDatabase(type);
	}

	private static void sanatize(String parameterName,  HashMap<String, Object> config) {
		Object parameter=config.get(parameterName);
		try {
			config.put(parameterName, Double.parseDouble(parameter.toString()));
		} catch (NumberFormatException ex) {
			config.put(parameterName, parameter);
		}
	}

	public static HashMap<String, HashMap<String, Object>> getDatabase(String type) {
		if (databaseSet.get(type) == null) {
			// we are using the default vitamins configuration
			// https://github.com/madhephaestus/Hardware-Dimensions.git

			// create some variables, including our database
			String jsonString;
			InputStream inPut = null;

			// attempt to load the JSON file from the GIt Repo and pars the JSON string
			File f;
			try {
				
				Runnable onChange=null;
				if(changeListeners.get(type)==null) {
					changeListeners.put(type,() -> {
						// If the file changes, clear the database and load the new data
						System.out.println("Re-loading "+type);
						databaseSet.put(type,null);
						new RuntimeException().printStackTrace();
					});
					onChange=changeListeners.get(type);
				}
				
				
				f = getVitaminFile(type,onChange,true);

				HashMap<String, HashMap<String, Object>> database;
				if(f.exists()) {
				
					inPut = FileUtils.openInputStream(f);
	
					jsonString = IOUtils.toString(inPut);
					inPut.close();
					System.out.println("JSON loading Loading "+type+" "+jsonString.length());
					// perfoem the GSON parse
					database = gson.fromJson(jsonString, TT_mapStringString);
					if(database==null)
						throw new RuntimeException("Database failed to read");
				}else {
					database=new HashMap<String, HashMap<String,Object>>();
				}
				databaseSet.put(type, database);

				for (String key : databaseSet.get(type).keySet()) {
					HashMap<String, Object> conf = database.get(key);
					for (String confKey : conf.keySet()) {
						try {
							double num = Double.parseDouble(conf.get(confKey).toString());
							conf.put(confKey, num);
						} catch (NumberFormatException ex) {
							// ex.printStackTrace();
							// leave as a string
							conf.put(confKey, conf.get(confKey).toString());
						}
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
				databaseSet.put(type, new HashMap<String, HashMap<String, Object>>());
			}
		}
		return databaseSet.get(type);

	}

	public static File getVitaminFile(String type, Runnable onChange, boolean oneShot)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		
		
		File f= ScriptingEngine.fileFromGit(getGitRepoDatabase(), // git repo, change this if you fork this demo
				getRootFolder() + type + ".json"// File from within the Git repo
		);
		if(onChange!=null) {
//			FileChangeWatcher watcher = FileChangeWatcher.watch(f);
//			watcher.addIFileChangeListener((fileThatChanged, event) -> {
//				onChange.run();
//			});
		}
		return f;
	}

	private static String getRootFolder() {
		return getJsonRootDir();
	}
	public static ArrayList<String> listVitaminActuators() {
		ArrayList<String> actuators = new  ArrayList<String>();
		
		for (String vitaminsType : Vitamins.listVitaminTypes()) {
			if (isActuator( vitaminsType))
				actuators.add(vitaminsType);
		}
		return actuators;
	}
	public static ArrayList<String> listVitaminShafts() {
		ArrayList<String> actuators = new  ArrayList<String>();
		for (String vitaminsType : Vitamins.listVitaminTypes()) {
			if (isShaft( vitaminsType))
				actuators.add(vitaminsType);
		}
		return actuators;
	}
	
	public static boolean isShaft(String vitaminsType) {
		Map<String, Object> meta = Vitamins.getMeta(vitaminsType);
		if (meta != null && meta.containsKey("shaft"))
			return true;
		return false;
	}
	public static boolean isActuator(String vitaminsType) {
		Map<String, Object> meta = Vitamins.getMeta(vitaminsType);
		if (meta != null && meta.containsKey("actuator"))
			return true;
		return false;
	}
	public static void setIsShaft(String type) {
		Vitamins.getMeta(type).remove("motor");
		Vitamins.getMeta(type).put("shaft", "true");
	}

	public static void setIsActuator(String type) {
		Vitamins.getMeta(type).remove("shaft");
		Vitamins.getMeta(type).put("actuator", "true");
	}
	public static ArrayList<String> listVitaminTypes() {

		ArrayList<String> types = new ArrayList<String>();
		File folder;
		try {		
			folder = new File(ScriptingEngine.getRepositoryCloneDirectory(getGitRepoDatabase()).getAbsoluteFile()+"/"+getRootFolder());
			File[] listOfFiles = folder.listFiles();

			for (File f : listOfFiles) {
				if (!f.isDirectory() && f.getName().endsWith(".json")) {
					types.add(f.getName().substring(0, f.getName().indexOf(".json")));
				}
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Collections.sort(types);
		return types;
	}

	public static ArrayList<String> listVitaminSizes(String type) {

		ArrayList<String> types = new ArrayList<String>();
		HashMap<String, HashMap<String, Object>> database = getDatabase(type);
		Set<String> keys = database.keySet();
		for (Iterator<String> iterator = keys.iterator(); iterator.hasNext();) {
			String s = iterator.next();
			if (s != null) {
				if (!s.contains("meta")) {
					types.add(s);
				}
			}
		}
		Collections.sort(types);
		return types;
	}

	public static String getGitRepoDatabase()  {
		if (!checked) {
			checked = true;
			try {
				if (PasswordManager.getUsername() != null) {
					//ScriptingEngine.setAutoupdate(true);
					org.kohsuke.github.GitHub github = PasswordManager.getGithub();
					try {
						GHRepository repo =github.getRepository(PasswordManager.getLoginID() + "/Hardware-Dimensions" ); 
						if(repo!=null) {
							String myAssets = repo.getGitTransportUrl().replaceAll("git://", "https://");
							// System.out.println("Using my version of Viamins: "+myAssets);
							setGitRepoDatabase(myAssets);
						}else {
							throw new org.kohsuke.github.GHFileNotFoundException();
						}
					}catch(Exception ex) {
						setGitRepoDatabase(defaultgitRpoDatabase);
					}
				}
			} catch (Exception ex) {
				new IssueReportingExceptionHandler().uncaughtException(Thread.currentThread(), ex);
			}
			ScriptingEngine.cloneRepo(gitRpoDatabase, "master");
			try {
				ScriptingEngine.pull(gitRpoDatabase);
			} catch (IOException | GitAPIException e) {
				ScriptingEngine.deleteRepo(gitRpoDatabase);
				ScriptingEngine.cloneRepo(gitRpoDatabase, "master");
			}

		}
		return gitRpoDatabase;
	}

	public static void reLoadDatabaseFromFiles() {
		
		setGitRepoDatabase(getGitRepoDatabase());
		try {
			ScriptingEngine.pull(getGitRepoDatabase());
		}catch (CheckoutConflictException|NoHeadException e) {
			ScriptingEngine.deleteRepo(getGitRepoDatabase());
			try {
				ScriptingEngine.pull(getGitRepoDatabase());
			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}catch (Exception e) {
			new IssueReportingExceptionHandler().uncaughtException(Thread.currentThread(), e);
		}
		listVitaminTypes();
		
	}
	public static void setGitRepoDatabase(String gitRpoDatabase) {
		Vitamins.gitRpoDatabase = gitRpoDatabase;
		databaseSet.clear();
		fileLastLoaded.clear();

	}

	public static String getJsonRootDir() {
		return jsonRootDir;
	}

	public static void setJsonRootDir(String jsonRootDir) throws IOException {
		Vitamins.jsonRootDir = jsonRootDir;
		setGitRepoDatabase(getGitRepoDatabase());
	}



}
