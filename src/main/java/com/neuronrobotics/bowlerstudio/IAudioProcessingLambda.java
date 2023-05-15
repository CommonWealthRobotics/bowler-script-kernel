package com.neuronrobotics.bowlerstudio;

public interface IAudioProcessingLambda {

	public void startProcessing();
	public AudioStatus update(AudioStatus current, double amplitudeUnitVector, double currentRollingAverage, double currentDerivitiveTerm);
}
