package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import eu.mihosoft.vrl.v3d.PropertyStorage;

public class CaDoodleFile {
	public static final String NO_NAME = "NoName";
	@Expose (serialize = true, deserialize = true)
	private ArrayList<ICaDoodleOpperation> opperations = new ArrayList<ICaDoodleOpperation>();
	@Expose (serialize = true, deserialize = true)
	private int currentIndex =0;
	@Expose (serialize = true, deserialize = true)
	private String projectName =NO_NAME;
	@Expose (serialize = false, deserialize = false)
	private File self;
//	@Expose (serialize = false, deserialize = false)
//	private List<CSG> currentState = new ArrayList<CSG>();
	@Expose (serialize = false, deserialize = false)
	private HashMap<ICaDoodleOpperation, List<CSG>> cache =new HashMap<ICaDoodleOpperation, List<CSG>>();
	private static Type TT_CaDoodleFile = new TypeToken<CaDoodleFile>() {}.getType();
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()    
			.excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapterFactory(new ICaDoodleOperationAdapterFactory())
			.create();
	private ArrayList<ICaDoodleStateUpdate> listeners = new ArrayList<ICaDoodleStateUpdate>();
	
	public CaDoodleFile clearListeners() {
		listeners.clear();
		return this;
	}
	public CaDoodleFile removeListener(ICaDoodleStateUpdate l) {
		if(listeners.contains(l))
			listeners.remove(l);
		return this;
	}
	public CaDoodleFile addListener(ICaDoodleStateUpdate l) {
		if(!listeners.contains(l))
			listeners.add(l);
		return this;
	}
	private void initialize() {
		int indexStarting = currentIndex;
		currentIndex=0;
		for(int i=0;i<opperations.size();i++) {
			ICaDoodleOpperation op =opperations.get(i);
			process(op);
		}
		currentIndex=indexStarting;
		updateCurrentFromCache();
	}
	private void process(ICaDoodleOpperation op) {
		storeResultInCache(op, op.process(getCurrentState()));
		currentIndex++;
		setCurrentState(op);
	}
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
			getOpperations().add(op);
			process(op);
		}catch(Exception ex) {
			ex.printStackTrace();
		}
		return getCurrentState();
	}
	private void storeResultInCache(ICaDoodleOpperation op, List<CSG> process) {
		ArrayList<CSG> cachedCopy = new ArrayList<CSG>();
		HashSet<String> names = new HashSet<>();
		for(CSG c:process) {
			if(names.contains(c.getName()))
				throw new RuntimeException("There can not be 2 objects with the same name after an opperation!");
			names.add(c.getName());
			cachedCopy.add(c.clone().setStorage(new PropertyStorage()).syncProperties(c).setName(c.getName()));
		}
		cache.put(op,cachedCopy);
	}
	public void back() {
		if(isBackAvailible())
			currentIndex-=1;
		updateCurrentFromCache();
	}
	public boolean isBackAvailible() {
		return currentIndex>1;
	}
	private void updateCurrentFromCache() {
		ICaDoodleOpperation key = currentOpperation();
		System.out.println("Current opperation results: "+key.getType());
		setCurrentState(key);
	}
	private ICaDoodleOpperation currentOpperation() {
		return getOpperations().get(currentIndex-1);
	}
	public void forward() {
		if(isForwardAvailible())
			currentIndex+=1;
		updateCurrentFromCache();
	}
	public boolean isForwardAvailible() {
		return currentIndex<getOpperations().size();
	}
	public File getSelf() {
		return self;
	}
	public CaDoodleFile setSelf(File self) {
		this.self = self;
		return this;
	}
	public List<CSG> getCurrentState() {
		if(currentIndex==0)
			return new ArrayList<CSG>();
		return cache.get(currentOpperation());
	}
	private void setCurrentState(ICaDoodleOpperation op) {

		for(ICaDoodleStateUpdate l:listeners) {
			try {
				l.onUpdate(getCurrentState(),op,this);
			}catch(Throwable e){
				e.printStackTrace();
			}
		}
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
	public File  save() throws IOException {
		
		if(self==null){
			self=File.createTempFile(DownloadManager.sanitizeString(projectName), ".doodle");
		}
		synchronized(self) {
			String contents =  toJson();
			FileUtils.write(self, contents,StandardCharsets.UTF_8, false);
		}
		return self;
	}


	public static CaDoodleFile fromJsonString(String content ) throws Exception{
		return fromJsonString(content, null);
	}
	public static CaDoodleFile fromJsonString(String content ,ICaDoodleStateUpdate listener) throws Exception {
		CaDoodleFile file =gson.fromJson(content, TT_CaDoodleFile);
		if(listener!=null) {
			file.addListener(listener);
		}
		file.initialize();
		return file;
	}
	public static CaDoodleFile fromFile(File f ) throws Exception{
		return fromFile(f,null);
	}
	public static CaDoodleFile fromFile(File f,ICaDoodleStateUpdate listener ) throws Exception {
		String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
		CaDoodleFile file =fromJsonString(content,listener);
		file.setSelf(f);
		System.out.println("CaDoodle file loaded from "+f.getAbsolutePath());
		return file;
	}
	public ArrayList<ICaDoodleOpperation> getOpperations() {
		return opperations;
	}
	public void setOpperations(ArrayList<ICaDoodleOpperation> opperations) {
		this.opperations = opperations;
	}
}
