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

import org.eclipse.jgit.api.errors.GitAPIException;

public class TimeSequence {
	// Create the type, this tells GSON what datatypes to instantiate when parsing
	// and saving the json
	private static Type TT_mapStringString = new TypeToken<HashMap<String, HashMap<String, Object>>>() {
	}.getType();
	private static Type TT_listString = new TypeToken<ArrayList<String>>() {
	}.getType();
	private static Type TT_SequenceEvent = new TypeToken<SequenceEvent>() {
	}.getType();
	// chreat the gson object, this is the parsing factory
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

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

	public static void execute(String content) throws Exception {

		HashMap<String, HashMap<String, Object>> database = gson.fromJson(content, TT_mapStringString);
		HashMap<String, Object> initialize = database.get("initialize");
		if (initialize == null)
			throw new RuntimeException("Cant initialize!");
		String url = initialize.get("url").toString();
		String file = initialize.get("file").toString();
		String wavurl = initialize.get("wavURL").toString();
		String wavfile = initialize.get("wavFile").toString();
		long duration = Long.parseLong(initialize.get("msDuration").toString());
		ArrayList<String> devicesInSequence = gson.fromJson(gson.toJson(initialize.get("devices")), TT_listString);

		System.out.println("Initialize Sequence");
		ScriptingEngine.gitScriptRun(url, file);

		HashMap<String, AbstractKinematicsNR> devices = getDevices();
		long start = System.currentTimeMillis();
		ArrayList<Thread> threads = new ArrayList<Thread>();
		if (wavurl != null && wavfile != null) {
			File path = ScriptingEngine.fileFromGit(wavurl, null, // branch
					wavfile);
			AudioInputStream audioStream = AudioSystem.getAudioInputStream(path);
			Clip audioClip = AudioSystem.getClip();
			audioClip.open(audioStream);
			double len = (double) audioClip.getMicrosecondLength() / 1000.0;
			if(duration < len)
				duration =(long) len;
			threads.add(new Thread(() -> {
				try {

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

				} catch (Exception e) {

				}

			}));
		}
		long finalDur = duration;
		for (String key : devices.keySet()) {
			for (String mine : devicesInSequence)
				if (mine.contentEquals(key)) {
					System.out.println("Found Device " + key);
					HashMap<String, Object> devSeq = database.get(key);
					Thread t = new Thread(() -> {
						for (int i = 0; i < finalDur && !Thread.interrupted(); i++) {
							SequenceEvent event = gson.fromJson(gson.toJson(devSeq.get("" + i)), TT_SequenceEvent);
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
		System.out.println("Running complete, took " + (System.currentTimeMillis() - start) + " expcted " + duration);
	}

}
