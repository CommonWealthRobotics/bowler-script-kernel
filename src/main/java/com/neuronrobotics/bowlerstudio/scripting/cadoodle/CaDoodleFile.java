package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.CSG;

public class CaDoodleFile {
	@Expose (serialize = true, deserialize = true)
	private ArrayList<ICaDoodleOpperation> opperations = new ArrayList<ICaDoodleOpperation>();
	@Expose (serialize = true, deserialize = true)
	private int currentIndex =0;
	@Expose (serialize = true, deserialize = true)
	private String projectName ="NoName";
	@Expose (serialize = false, deserialize = false)
	private File self;
	@Expose (serialize = false, deserialize = false)
	private List<CSG> currentState = new ArrayList<CSG>();
	@Expose (serialize = false, deserialize = false)
	private HashMap<ICaDoodleOpperation, List<CSG>> cache =new HashMap<ICaDoodleOpperation, List<CSG>>();
	private static Type TT_CaDoodleFile = new TypeToken<CaDoodleFile>() {}.getType();
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()    
			.excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapterFactory(new ICaDoodleOperationAdapterFactory())
			.create();
	
	public  List<CSG> addOpperation(ICaDoodleOpperation op) {
		if(currentIndex != getOpperations().size()) {
			for(int i=currentIndex;i<getOpperations().size();i++) {
				List<CSG> back = cache.remove(getOpperations().get(i));
				back.clear();
			}
			List<ICaDoodleOpperation> subList = (List<ICaDoodleOpperation>) getOpperations().subList(0, currentIndex);
			ArrayList<ICaDoodleOpperation> newList=new ArrayList<ICaDoodleOpperation>();
			newList.addAll(subList);
			setOpperations(newList);
		}
		try {
			List<CSG> process = op.process(getCurrentState());
			cache.put(op,process);
			setCurrentState(process);
			currentIndex++;
			getOpperations().add(op);
		}catch(Exception ex) {
			ex.printStackTrace();
		}
		return currentState;
	}
	public void back() {
		if(currentIndex>0)
			currentIndex-=1;
		updateCurrentFromCache();
	}
	private void updateCurrentFromCache() {
		ICaDoodleOpperation key = getOpperations().get(currentIndex-1);
		List<CSG> currentState2 = cache.get(key);
		setCurrentState(currentState2);
	}
	public void forward() {
		if(currentIndex<getOpperations().size()-1)
			currentIndex+=1;
		updateCurrentFromCache();
	}
	public File getSelf() {
		return self;
	}
	public CaDoodleFile setSelf(File self) {
		this.self = self;
		return this;
	}
	public List<CSG> getCurrentState() {
		return currentState;
	}
	private void setCurrentState(List<CSG> currentState) {
		this.currentState = currentState;
	}
	public String getProjectName() {
		return projectName;
	}
	public CaDoodleFile setProjectName(String projectName) {
		this.projectName = projectName;
		return this;
	}
	public String toJson() {
		return gson.toJson(this);
	}
	public File save() throws IOException {
		File ret = self==null?File.createTempFile(DownloadManager.sanitizeString(projectName), ".doodle"):self;
		String contents =  toJson();
		FileUtils.write(ret, contents,StandardCharsets.UTF_8, false);
		return ret;
	}
	public static CaDoodleFile fromJsonString(String content ) throws Exception {
		CaDoodleFile file =gson.fromJson(content, TT_CaDoodleFile);
		file.initialize();
		return file;
	}
	private void initialize() {
		for(int i=0;i<opperations.size();i++) {
			ICaDoodleOpperation op =opperations.get(i);
			currentState= op.process(getCurrentState());
			cache.put(op,currentState);
		}
		updateCurrentFromCache();
	}
	public static CaDoodleFile fromFile(File f) throws Exception {
		String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
		CaDoodleFile file =fromJsonString(content);
		file.setSelf(f);
		return file;
	}
	public ArrayList<ICaDoodleOpperation> getOpperations() {
		return opperations;
	}
	public void setOpperations(ArrayList<ICaDoodleOpperation> opperations) {
		this.opperations = opperations;
	}
}
