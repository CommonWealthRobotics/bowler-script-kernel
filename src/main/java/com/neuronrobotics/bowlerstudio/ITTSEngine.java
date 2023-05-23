package com.neuronrobotics.bowlerstudio;

public interface ITTSEngine {
	/**
	 * Transform text to speech
	 * 
	 * @param text
	 *            The text that will be transformed to speech
	 * @param daemon
	 *            <br>
	 *            <b>True</b> The thread that will start the text to speech Player will be a daemon Thread <br>
	 *            <b>False</b> The thread that will start the text to speech Player will be a normal non daemon Thread
	 * @param join
	 *            <br>
	 *            <b>True</b> The current Thread calling this method will wait(blocked) until the Thread which is playing the Speech finish <br>
	 *            <b>False</b> The current Thread calling this method will continue freely after calling this method
	 * @param progress 
	 */
	public int speak(String text , float gainValue , boolean daemon , boolean join, ISpeakingProgress progress) ;
}
