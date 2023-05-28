package com.neuronrobotics.bowlerstudio.lipsync;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.AudioStatus;
import com.neuronrobotics.bowlerstudio.IAudioProcessingLambda;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.video.OSUtil;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

public class RhubarbManager implements IAudioProcessingLambda {
	ArrayList<HashMap<AudioStatus, Double>> timeCodedVisemes = null;
	private static String RhubarbVersion = "1.13.0";

	public void processRaw(File f) throws Exception {
		String os = OSUtil.isLinux() ? "Linux" : OSUtil.isOSX() ? "macOS" : "Windows";
		String exeExtention = OSUtil.isWindows() ? ".exe" : "";
		File exe = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/Rhubarb-Lip-Sync/Rhubarb-Lip-Sync-"
				+ RhubarbVersion + "-" + os + "/rhubarb" + exeExtention);
		timeCodedVisemes = new ArrayList<>();
		if (!exe.exists()) {
			System.out.println("Downloading " + exe.getAbsolutePath());
			String zipfileName = "Rhubarb-Lip-Sync-" + RhubarbVersion + "-" + os + ".zip";
			String urlStr = "https://github.com/DanielSWolf/rhubarb-lip-sync/releases/download/v" + RhubarbVersion + "/"
					+ zipfileName;
			File zipfile = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/" + zipfileName);
			if (!zipfile.exists()) {
				URL url = new URL(urlStr);
				BufferedInputStream bis = new BufferedInputStream(url.openStream());
				FileOutputStream fis = new FileOutputStream(zipfile);
				byte[] buffer = new byte[1024];
				int count = 0;
				while ((count = bis.read(buffer, 0, 1024)) != -1) {
					fis.write(buffer, 0, count);
				}
				fis.close();
				bis.close();

				String source = zipfile.getAbsolutePath();
				String destination = ScriptingEngine.getWorkspace().getAbsolutePath() + "/Rhubarb-Lip-Sync/";
				try {
					ZipFile zipFile = new ZipFile(source);
					zipFile.extractAll(destination);
				} catch (ZipException e) {
					e.printStackTrace();
				}
			}
		}
		File homeDirectory = exe.getParentFile();
		boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

		Process process;

		process = Runtime.getRuntime().exec(exe + " --machineReadable -f json " + f.getAbsolutePath());

		int exitCode = process.waitFor();

		InputStream is = process.getInputStream();
		StringWriter writer = new StringWriter();
		Charset utf8 = StandardCharsets.UTF_8;
		IOUtils.copy(is, writer, utf8);
		String result = writer.toString();
		// System.out.println(status);
		// System.out.println(result);
		Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
		}.getType();
		Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

		HashMap<String, Object> resultParsed = gson.fromJson(result, TT_mapStringString);
		double duration = Double
				.parseDouble(((Map<String, Object>) resultParsed.get("metadata")).get("duration").toString());
		List<Map<String, Object>> cues = (List<Map<String, Object>>) resultParsed.get("mouthCues");
		for (Map<String, Object> cue : cues) {
			double end = Double.parseDouble(cue.get("end").toString());
			double percent = end / duration * 100.0;

			AudioStatus val = AudioStatus.get(cue.get("value").toString().charAt(0));
			System.out.println("End at " + percent + " " + val);
			HashMap<AudioStatus, Double> map = new HashMap<>();
			map.put(val, percent);
			timeCodedVisemes.add(map);
		}

	}

	@Override
	public AudioInputStream startProcessing(AudioInputStream ais) {
		File audio = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/tmp-tts.wav");
		try {
			System.out.println("Begin writing..");
			AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audio);
			ais = AudioSystem.getAudioInputStream(audio);
			// rhubarb!
			processRaw(audio);
			System.out.println("Done writing!");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ais;
	}

	@Override
	public AudioStatus update(AudioStatus current, double amplitudeUnitVector, double currentRollingAverage,
			double currentDerivitiveTerm, double percent) {
		if (timeCodedVisemes.size() > 0) {
			HashMap<AudioStatus, Double> map = timeCodedVisemes.get(0);
			AudioStatus key = (AudioStatus) map.keySet().toArray()[0];
			double value = map.get(key);
			if(percent > value) {
				timeCodedVisemes.remove(0);
				return update(key, amplitudeUnitVector, currentRollingAverage, currentDerivitiveTerm, percent);
			}
			return key;
		}
		return current;
	}

}
