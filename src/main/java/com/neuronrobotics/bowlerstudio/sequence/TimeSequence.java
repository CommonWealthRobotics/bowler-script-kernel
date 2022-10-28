package com.neuronrobotics.bowlerstudio.sequence;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.AbstractKinematicsNR;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.common.DeviceManager;
import com.neuronrobotics.sdk.util.ThreadUtil;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

public class TimeSequence {
	// Create the type, this tells GSON what datatypes to instantiate when parsing
	// and saving the json
	private static Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
	}.getType();
	private static Type TT_listString = new TypeToken<ArrayList<String>>() {
	}.getType();
	private static Type TT_SequenceEvent = new TypeToken<SequenceEvent>() {
	}.getType();
	private static Type TT_mapSequence = new TypeToken<HashMap<String, SequenceEvent>>() {
	}.getType();
	// chreat the gson object, this is the parsing factory
	private Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private HashMap<String, Object> database;
	private HashMap<String, Object> initialize;
	private String url;
	private String file;
	private String wavurl;
	private String wavfile;
	private long duration;
	private ArrayList<String> devicesInSequence;

	public static HashMap<String, AbstractKinematicsNR> getDevices() {
		HashMap<String, AbstractKinematicsNR> map = new HashMap<>();
		for (String dev : DeviceManager.listConnectedDevice()) {
			Object specificDevice = DeviceManager.getSpecificDevice(dev);
			if (MobileBase.class.isInstance(specificDevice)) {
				MobileBase specificDevice2 = (MobileBase) specificDevice;
				loadMobileBase(map, specificDevice2, 0);
			}
		}
		return map;
	}

	private static void loadMobileBase(HashMap<String, AbstractKinematicsNR> map, MobileBase specificDevice2,
			int depth) {
		map.put("MobileBase:" + specificDevice2.getScriptingName() + " " + depth, specificDevice2);
		for (DHParameterKinematics pg : specificDevice2.getAllParallelGroups()) {
			map.put(specificDevice2.getScriptingName() + " " + "ParallelGroup:" + pg.getScriptingName() + " " + depth,
					pg);
		}
		for (DHParameterKinematics kin : specificDevice2.getAllDHChains()) {
			if (specificDevice2.getParallelGroup(kin) == null)
				map.put(specificDevice2.getScriptingName() + " " + "Appendage:" + kin.getScriptingName() + " " + depth,
						kin);
			for (int i = 0; i < kin.getNumberOfLinks(); i++) {
				MobileBase follower = kin.getSlaveMobileBase(i);
				if (follower != null) {
					loadMobileBase(map, follower, depth + 1);
				}
			}
		}
	}

	public void execute(String content) throws Exception {

		load(content);

		runSequence();
	}

	public void runSequence() throws Exception {
		System.out.println("Initialize Sequence");
		ScriptingEngine.gitScriptRun(getUrl(), getFile());

		HashMap<String, AbstractKinematicsNR> devices = getDevices();
		long start = System.currentTimeMillis();
		ArrayList<Thread> threads = new ArrayList<Thread>();
		if (getWavurl() != null && getWavfile() != null) {
			addWavFileRun(threads);
		}
		long finalDur = getDuration();
		for (String mine : devices.keySet()) {
			for (String key : getDevicesInSequence())
				if (mine.contentEquals(key)) {
					System.out.println("Found Device " + key);
					HashMap<String, SequenceEvent> devSeq = getSequence(key);
					Thread t = new Thread(() -> {
						for (int i = 0; i < finalDur && !Thread.interrupted(); i++) {
							SequenceEvent event = devSeq.get("" + i);
							if (event != null) {
								System.out.println(key + " Execute @ " + i);
								event.execute((DHParameterKinematics) devices.get(key));
							}
							try {
								Thread.sleep(1);
							} catch (InterruptedException e) {
								return;
							}
						}
					});
					threads.add(t);
				}
		}
		System.out.println("Running sequence");
		for (Thread t : threads) {
			t.start();
		}
		try {
			for (Thread t : threads) {
				t.join();
			}
		} catch (java.lang.InterruptedException ex) {
			for (Thread t : threads) {
				t.interrupt();
			}
		}
		System.out.println(
				"Running complete, took " + (System.currentTimeMillis() - start) + " expcted " + getDuration());
	}

	private void addWavFileRun(ArrayList<Thread> threads) throws InvalidRemoteException, TransportException,
			GitAPIException, IOException, UnsupportedAudioFileException, LineUnavailableException {

		threads.add(new Thread(() -> {
			try {
				File path = ScriptingEngine.fileFromGit(getWavurl(), null, // branch
						getWavfile());
				AudioInputStream audioStream = AudioSystem.getAudioInputStream(path);
				Clip audioClip = AudioSystem.getClip();
				audioClip.addLineListener(event -> {
					if (LineEvent.Type.STOP.equals(event.getType())) {
						audioClip.close();
					}
				});
				audioClip.open(audioStream);
				double len = (double) audioClip.getMicrosecondLength() / 1000.0;
				if (getDuration() < len)
					setDuration((long) len);
				audioClip.start();
				ThreadUtil.wait(1);
				try {
					while (audioClip.isRunning() && !Thread.interrupted()) {
						double pos = (double) audioClip.getMicrosecondPosition() / 1000.0;
						double percent = pos / len * 100.0;
						// System.out.println("Current " + pos + " Percent = " + percent);
						ThreadUtil.wait(10);
					}
				} catch (Throwable t) {

				}
				audioClip.stop();
				audioClip.close();
				((AudioInputStream) audioStream).close();
				System.out.println("Audio clip exited "+getWavurl()+" : "+getWavfile());
			} catch (Exception e) {
				e.printStackTrace();
			}

		}));
	}

	public void load(String content) {
		setDatabase(gson.fromJson(content, TT_mapStringString));
		setInitialize(gson.fromJson(gson.toJson(getDatabase().get("initialize")), TT_mapStringString));
		if (getInitialize() == null)
			throw new RuntimeException("Cant initialize!");
		setUrl(getInitialize().get("url").toString());
		setFile(getInitialize().get("file").toString());
		setWavurl(getInitialize().get("wavURL").toString());
		setWavfile(getInitialize().get("wavFile").toString());
		setDuration(Long.parseLong(getInitialize().get("msDuration").toString()));
		setDevicesInSequence(gson.fromJson(gson.toJson(getInitialize().get("devices")), TT_listString));
		for (String key : devicesInSequence) {
			getSequence(key);
		}
	}

	public HashMap<String, SequenceEvent> getSequence(String d) {
		String device = getDevice(d);
		HashMap<String, SequenceEvent> devSeq = gson.fromJson(gson.toJson(getDatabase().get(device)), TT_mapSequence);
		if (devSeq == null) {
			devSeq = new HashMap<>();
		}
		getDatabase().put(device, devSeq);

		return devSeq;
	}

	private String getDevice(String d) {
		for (String s : devicesInSequence) {
			if (s.contentEquals(d))
				return d;
		}
		devicesInSequence.add(d);
		return d;
	}

	public String save() {
		return gson.toJson(getDatabase());
	}

	public ArrayList<String> getDevicesInSequence() {
		return devicesInSequence;
	}

	public void setDevicesInSequence(ArrayList<String> devicesInSequence) {
		this.devicesInSequence = devicesInSequence;
		initialize.put("devices", devicesInSequence);
	}

	public long getDuration() {
		return duration;
	}

	public void setDuration(long duration) {
		this.duration = duration;
	}

	public String getWavfile() {
		return wavfile;
	}

	public void setWavfile(String wavfile) {
		this.wavfile = wavfile;
	}

	public String getWavurl() {
		return wavurl;
	}

	public void setWavurl(String wavurl) {
		this.wavurl = wavurl;
	}

	public String getFile() {
		return file;
	}

	public void setFile(String file) {
		this.file = file;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public HashMap<String, Object> getInitialize() {
		return initialize;
	}

	public void setInitialize(HashMap<String, Object> initialize) {
		this.initialize = initialize;
		database.put("initialize", initialize);
	}

	public HashMap<String, Object> getDatabase() {
		return database;
	}

	public void setDatabase(HashMap<String, Object> database) {
		this.database = database;
	}

}
