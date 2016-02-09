package com.neuronrobotics.bowlerkernel;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.util.ThreadUtil;

public class BowlerDatabase {
	
	private static HashMap<String,Object> database=null;

	public static void set(String key, Object value){
		getDatabase().put(key, value);
	}
	public static Object get(String key){
		return getDatabase().get(key);
	}
	public static void delete(String key, Object value){
		 getDatabase().remove(key);
	}
	private static HashMap<String,Object> getDatabase() {
		if(database==null){
			new Thread(){
				public void run(){
					String jsonString;
					try {
						File db=new File(ScriptingEngine.getWorkspace()+"/database.json");
						if(!db.exists())
							db.createNewFile();
						jsonString = FileUtils.readFileToString(db);
						setDatabase(new Gson().fromJson(jsonString, new TypeToken<HashMap<String, Object>>(){}.getType()));
					} catch (Exception e) {
						setDatabase(new HashMap<>());
					}
					Runtime.getRuntime().addShutdownHook(new Thread() {
						@Override
						public void run() {
							saveDatabase();
						}
					});
				}
			}.start();
			while(database==null){
				ThreadUtil.wait(10);
			}
		}
		return database;
	}
	public static void saveDatabase(){
		String writeOut  =new Gson().toJson(database); 
		try {
			FileUtils.write(new File(ScriptingEngine.getWorkspace()+"/database.json"), writeOut);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public static void setDatabase(HashMap<String,Object> database) {
		BowlerDatabase.database = database;
	}
}
