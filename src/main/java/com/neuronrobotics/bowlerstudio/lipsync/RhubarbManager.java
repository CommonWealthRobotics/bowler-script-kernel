package com.neuronrobotics.bowlerstudio.lipsync;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import com.neuronrobotics.bowlerstudio.AudioStatus;
import com.neuronrobotics.bowlerstudio.IAudioProcessingLambda;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.video.OSUtil;

public class RhubarbManager implements IAudioProcessingLambda {
	ArrayList<HashMap<AudioStatus, Double>> timeCodedVisemes = null;
	private static String RhubarbVersion = "1.13.0";

	public void processRaw(File f) {
		String os = OSUtil.isLinux() ? "Linux" : OSUtil.isOSX() ? "macOS" : "Windows";
		// https://github.com/DanielSWolf/rhubarb-lip-sync/releases/download/v1.13.0/Rhubarb-Lip-Sync-1.13.0-Linux.zip
		File zipfile = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "Rhubarb-Lip-Sync-" + RhubarbVersion
				+ "-" + os + ".zip");
		if(!zipfile.exists()) {
			
		}

		timeCodedVisemes = new ArrayList<>();

	}

	@Override
	public AudioInputStream startProcessing(AudioInputStream ais) {
		File audio = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/tmp-tts.wav");
		try {
			System.out.println("Begin writing..");
			AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audio);

			// rhubarb!
			processRaw(audio);

			AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audio);
			System.out.println("Done writing!");
			return audioInputStream;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedAudioFileException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ais;
	}

	@Override
	public AudioStatus update(AudioStatus current, double amplitudeUnitVector, double currentRollingAverage,
			double currentDerivitiveTerm, double percent) {

		return current;
	}

}
