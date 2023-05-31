package com.neuronrobotics.bowlerstudio;

import javax.sound.sampled.AudioInputStream;

public interface IAudioProcessingLambda {

	public AudioInputStream startProcessing(AudioInputStream ais, String ttsString);
	public AudioStatus update(AudioStatus current, double amplitudeUnitVector, double currentRollingAverage, double currentDerivitiveTerm, double percent);
}
