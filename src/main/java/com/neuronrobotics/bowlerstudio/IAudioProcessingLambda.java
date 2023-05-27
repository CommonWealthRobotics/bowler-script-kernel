package com.neuronrobotics.bowlerstudio;

import javax.sound.sampled.AudioInputStream;

public interface IAudioProcessingLambda {

	public void startProcessing(AudioInputStream ais);
	public AudioStatus update(AudioStatus current, double amplitudeUnitVector, double currentRollingAverage, double currentDerivitiveTerm, double percent);
}
