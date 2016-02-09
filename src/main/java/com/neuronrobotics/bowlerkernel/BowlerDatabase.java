package com.neuronrobotics.bowlerkernel;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.util.ThreadUtil;

public class BowlerDatabase {
	
	private static HashMap<String,String> database=null;
	private static File db=new File(ScriptingEngine.getWorkspace()+"/database.json");
    private static final Type TT_mapStringString = new TypeToken<HashMap<String,String>>(){}.getType();
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	public static void set(String key, String value){
		synchronized(database){
			getDatabase().put(key, value);
		}
	}
	public static String get(String key){
		String ret =null;
		getDatabase();// load database before synchronization
		synchronized(database){
			ret=getDatabase().get(key);
		}
		return ret;
	}
	public static void set(String key, double value){
		set(key, new Double(value).toString());
	}
	public static void set(String key, int value){
		set(key, new Integer(value).toString());
	}
	public static double getDouble(String key){
		String ret =null;
		getDatabase();// load database before synchronization
		synchronized(database){
			ret=getDatabase().get(key);
		}
		return Double.parseDouble(ret);
	}
	public static int getInteger(String key){
		String ret =null;
		getDatabase();// load database before synchronization
		synchronized(database){
			ret=getDatabase().get(key);
		}
		return Integer.parseInt(ret);
	}
	public static void delete(String key){
		synchronized(database){
			getDatabase().remove(key);
		}
	}
	private static HashMap<String,String> getDatabase() {
		if(database==null){
			new Thread(){
				public void run(){
					String jsonString;
					try {
						
						if(!db.exists()){
							setDatabase(new HashMap<String,String>());
						}
						else{
					        InputStream in = null;
					        try {
					            in = FileUtils.openInputStream(db);
					            jsonString= IOUtils.toString(in);
					        } finally {
					            IOUtils.closeQuietly(in);
					        }
					        HashMap<String,String> tm=gson.fromJson(jsonString, TT_mapStringString);
					        
					        
					        if(tm!=null){
//					        	System.out.println("Hash Map loaded from "+jsonString);
//					        	for(String k:tm.keySet()){
//						        	System.out.println("Key: "+k+" vlaue= "+tm.get(k));
//						        }
					        	setDatabase(tm);
					        }
						}
					} catch (Exception e) {
						e.printStackTrace();
						setDatabase(new HashMap<String,String>());
					}
					Runtime.getRuntime().addShutdownHook(new Thread() {
						@Override
						public void run() {
							saveDatabase();
						}
					});
				}
			}.start();
			long start = System.currentTimeMillis();
			while(database==null){
				ThreadUtil.wait(10);
				if((System.currentTimeMillis()-start)>500){
					setDatabase(new HashMap<String,String>());
				}
			}
		}
		return database;
	}
	
	public static void loadDatabaseFromFile(File f){
		InputStream in = null;
		String jsonString;
        try {
            try {
				in = FileUtils.openInputStream(db);
				jsonString= IOUtils.toString(in);
		        HashMap<String,String> tm=gson.fromJson(jsonString, TT_mapStringString);
		        for(String k:tm.keySet()){
		        	set(k,tm.get(k));
		        }
		        saveDatabase();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
        } finally {
            IOUtils.closeQuietly(in);
        }
	}
	
	public static String getDataBaseString(){
		String writeOut=null;
		synchronized(database){
			 writeOut  =gson.toJson(database, TT_mapStringString); 
		}
		return writeOut;
	}
	
	public static void saveDatabase(){
		String writeOut=getDataBaseString();
		try {
			if(!db.exists()){
				db.createNewFile();
			}
	        OutputStream out = null;
	        try {
	            out = FileUtils.openOutputStream(db, false);
	            IOUtils.write(writeOut, out);
	            out.flush();
	            out.close(); // don't swallow close Exception if copy completes normally
	        } finally {
	            IOUtils.closeQuietly(out);
	        }
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private static void setDatabase(HashMap<String,String> database) {
		if(BowlerDatabase.database!=null){
			return;
		}
		BowlerDatabase.database = database;
	}
}
