package com.neuronrobotics.bowlerstudio.lipsync;

import com.neuronrobotics.bowlerstudio.AudioStatus;

public class TimeCodedViseme {
	AudioStatus status;
	double start;
	double end;
	double total;

	public TimeCodedViseme(AudioStatus st, double s, double e, double t) {
		status = st;
		start = s;
		end = e;
		total = t;
	}

	double getStartPercentage() {
		return ((start / total) * 100.0);
	}

	double getEndPercentage() {
		return ((end / total) * 100.0);
	}

	public String toString() {
		return status.toString() + " start percent " + getStartPercentage() + " ends at " + getEndPercentage();
	}
}
