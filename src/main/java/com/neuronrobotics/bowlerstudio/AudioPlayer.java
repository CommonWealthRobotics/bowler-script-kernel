package com.neuronrobotics.bowlerstudio;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import marytts.util.data.audio.MonoAudioInputStream;
import marytts.util.data.audio.StereoAudioInputStream;

/**
 * A single Thread Audio Player Once used it has to be initialised again
 * 
 * @author GOXR3PLUS
 *
 */
public class AudioPlayer extends Thread {
	
	public static final int MONO = 0;
	public static final int STEREO = 3;
	public static final int LEFT_ONLY = 1;
	public static final int RIGHT_ONLY = 2;
	private AudioInputStream ais;
	private LineListener lineListener;
	private ISpeakingProgress speakProgress;
	private SourceDataLine line;
	private int outputMode;
	
	private Status status = Status.WAITING;
	private boolean exitRequested = false;
	private float gain = 1.0f;
	private static double threshhold = 600/65535.0;
	private static double lowerThreshhold = 100/65535.0;;
	private static int integralDepth=30;
	private static double integralGain = 1.0;
	private static double derivitiveGain =1.0;
	private static IAudioProcessingLambda lambda = new IAudioProcessingLambda() {
		
		@Override
		public AudioStatus update(AudioStatus currentStatus, double amplitudeUnitVector, double currentRollingAverage,
				double currentDerivitiveTerm) {
			switch(currentStatus) {
			case attack:
				if(amplitudeUnitVector>getThreshhold()) {
					currentStatus=AudioStatus.sustain;
				}
				break;
			case decay:
				if(amplitudeUnitVector<getLowerThreshhold()) {
					currentStatus=AudioStatus.release;
				}
				break;
			case release:
				if(amplitudeUnitVector>getThreshhold()) {
					currentStatus=AudioStatus.attack;
				}
				break;
			case sustain:
				if(amplitudeUnitVector<getLowerThreshhold()) {
					currentStatus=AudioStatus.decay;
				}
				break;
			default:
				break;
			}
			return currentStatus;
		}
		
		@Override
		public void startProcessing() {

		}
	};
	
	/**
	 * The status of the player
	 * 
	 * @author GOXR3PLUS
	 *
	 */
	public enum Status {
		/**
		 * 
		 */
		WAITING,
		/**
		* 
		*/
		PLAYING;
	}
	
	/**
	 * AudioPlayer which can be used if audio stream is to be set separately, using setAudio().
	 *
	 */
	public AudioPlayer() {
	}
	
	/**
	 * @param audioFile
	 * @throws IOException
	 * @throws UnsupportedAudioFileException
	 */
	public AudioPlayer(File audioFile) throws IOException, UnsupportedAudioFileException {
		this.ais = AudioSystem.getAudioInputStream(audioFile);
	}
	
	/**
	 * @param ais
	 */
	public AudioPlayer(AudioInputStream ais) {
		this.ais = ais;
	}
	
	/**
	 * @param audioFile
	 * @param lineListener
	 * @throws IOException
	 * @throws UnsupportedAudioFileException
	 */
	public AudioPlayer(File audioFile, LineListener lineListener) throws IOException, UnsupportedAudioFileException {
		this.ais = AudioSystem.getAudioInputStream(audioFile);
		this.lineListener = lineListener;
	}
	
	/**
	 * @param ais
	 * @param lineListener
	 */
	public AudioPlayer(AudioInputStream ais, LineListener lineListener) {
		this.ais = ais;
		this.lineListener = lineListener;
	}
	
	/**
	 * @param audioFile
	 * @param line
	 * @param lineListener
	 * @throws IOException
	 * @throws UnsupportedAudioFileException
	 */
	public AudioPlayer(File audioFile, SourceDataLine line, LineListener lineListener) throws IOException, UnsupportedAudioFileException {
		this.ais = AudioSystem.getAudioInputStream(audioFile);
		this.setLine(line);
		this.lineListener = lineListener;
	}
	
	/**
	 * @param ais
	 * @param line
	 * @param lineListener
	 */
	public AudioPlayer(AudioInputStream ais, SourceDataLine line, LineListener lineListener) {
		this.ais = ais;
		this.setLine(line);
		this.lineListener = lineListener;
	}
	
	/**
	 * 
	 * @param audioFile
	 *            audiofile
	 * @param line
	 *            line
	 * @param lineListener
	 *            lineListener
	 * @param outputMode
	 *            if MONO, force output to be mono; if STEREO, force output to be STEREO; if LEFT_ONLY, play a mono signal over the left channel of a
	 *            stereo output, or mute the right channel of a stereo signal; if RIGHT_ONLY, do the same with the right output channel.
	 * @throws IOException
	 *             IOException
	 * @throws UnsupportedAudioFileException
	 *             UnsupportedAudioFileException
	 */
	public AudioPlayer(File audioFile, SourceDataLine line, LineListener lineListener, int outputMode) throws IOException, UnsupportedAudioFileException {
		this.ais = AudioSystem.getAudioInputStream(audioFile);
		this.setLine(line);
		this.lineListener = lineListener;
		this.outputMode = outputMode;
	}
	
	/**
	 * 
	 * @param ais
	 *            ais
	 * @param line
	 *            line
	 * @param lineListener
	 *            lineListener
	 * @param outputMode
	 *            if MONO, force output to be mono; if STEREO, force output to be STEREO; if LEFT_ONLY, play a mono signal over the left channel of a
	 *            stereo output, or mute the right channel of a stereo signal; if RIGHT_ONLY, do the same with the right output channel.
	 */
	public AudioPlayer(AudioInputStream ais, SourceDataLine line, LineListener lineListener, int outputMode) {
		this.ais = ais;
		this.setLine(line);
		this.lineListener = lineListener;
		this.outputMode = outputMode;
	}
	
	/**
	 * @param audio
	 */
	public void setAudio(AudioInputStream audio) {
		if (status == Status.PLAYING) {
			throw new IllegalStateException("Cannot set audio while playing");
		}
		this.ais = audio;
	}
	
	/**
	 * Cancel the AudioPlayer which will cause the Thread to exit
	 */
	public void cancel() {
		if (getLine() != null) {
			getLine().stop();
		}
		exitRequested = true;
	}
	
	/**
	 * @return The SourceDataLine
	 */
	public SourceDataLine getLine() {
		return line;
	}
	
	/**
	 * Returns the GainValue
	 */
	public float getGainValue() {
		return gain;
	}
	
	/**
	 * Sets Gain value. Line should be opened before calling this method. Linear scale 0.0  1.0 Threshold Coef. : 1/2 to avoid saturation.
	 * 
	 * @param fGain
	 */
	public void setGain(float fGain) {
		
		// if (line != null)
		// System.out.println(((FloatControl)
		// line.getControl(FloatControl.Type.MASTER_GAIN)).getValue())
		
		// Set the value
		gain = fGain;
		
		// Better type
		if (getLine() != null && getLine().isControlSupported(FloatControl.Type.MASTER_GAIN))
			( (FloatControl) getLine().getControl(FloatControl.Type.MASTER_GAIN) ).setValue((float) ( 20 * Math.log10(fGain <= 0.0 ? 0.0000 : fGain) ));
		// OR (Math.log(fGain == 0.0 ? 0.0000 : fGain) / Math.log(10.0))
		
		// if (line != null)
		// System.out.println(((FloatControl)
		// line.getControl(FloatControl.Type.MASTER_GAIN)).getValue())
	}
	
	@Override
	public void run() {
		
		status = Status.PLAYING;
		AudioFormat audioFormat = ais.getFormat();
		if (audioFormat.getChannels() == 1) {
			if (outputMode != 0) {
				ais = new StereoAudioInputStream(ais, outputMode);
				audioFormat = ais.getFormat();
			}
		} else {
			assert audioFormat.getChannels() == 2 : "Unexpected number of channels: " + audioFormat.getChannels();
			if (outputMode == 0) {
				ais = new MonoAudioInputStream(ais);
			} else if (outputMode == 1 || outputMode == 2) {
				ais = new StereoAudioInputStream(ais, outputMode);
			} else {
				assert outputMode == 3 : "Unexpected output mode: " + outputMode;
			}
		}
		
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
		
		try {
			if (getLine() == null) {
				boolean bIsSupportedDirectly = AudioSystem.isLineSupported(info);
				if (!bIsSupportedDirectly) {
					AudioFormat sourceFormat = audioFormat;
					AudioFormat targetFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sourceFormat.getSampleRate(), sourceFormat.getSampleSizeInBits(),
							sourceFormat.getChannels(), sourceFormat.getChannels() * ( sourceFormat.getSampleSizeInBits() / 8 ), sourceFormat.getSampleRate(),
							sourceFormat.isBigEndian());
					
					ais = AudioSystem.getAudioInputStream(targetFormat, ais);
					audioFormat = ais.getFormat();
				}
				info = new DataLine.Info(SourceDataLine.class, audioFormat);
				setLine((SourceDataLine) AudioSystem.getLine(info));
			}
			if (lineListener != null) {
				getLine().addLineListener(lineListener);
			}
			getLine().open(audioFormat);
		} catch (Exception ex) {
			Logger.getLogger(getClass().getName()).log(Level.WARNING, null, ex);
			return;
		}
		
		getLine().start();
		setGain(getGainValue());
		
		int nRead = 0;
		byte[] abData = new byte[6553];
		int total = 0;
		AudioStatus status = AudioStatus.release;
		int integralIndex=0;
		double integralTotal=0;
		double[] buffer =null;
		Double previousValue=null;
		while ( ( nRead != -1 ) && ( !exitRequested ) && (!Thread.interrupted())) {
			try {
				Thread.sleep(0,1);
			} catch (InterruptedException e) {
				break;
			}
			try {
				nRead = ais.read(abData, 0, abData.length);
			} catch (IOException ex) {
				Logger.getLogger(getClass().getName()).log(Level.WARNING, null, ex);
			}
			int lastIndex=0;
			int amountToRead = nRead;
			
			if (nRead >= 0) {

				if(speakProgress!=null) {

					for(int i=0;i<nRead-1;i+=2) {
						if(Thread.interrupted()) {
							exitRequested=true;
							break;
						}
						int upperByteOfAmplitude=abData[i];
						if(upperByteOfAmplitude<0)
							upperByteOfAmplitude+=256;
						int lowerByteOfAmplitude=abData[i+1];
						if(lowerByteOfAmplitude<0)
							lowerByteOfAmplitude+=256;
						double amplitude16Bit=(upperByteOfAmplitude<<8)+(lowerByteOfAmplitude);
						double amplitudeUnitVector = amplitude16Bit/65535.0;// scale the amplitude to a 0-1.0 scale
						if(previousValue==null) {
							// initialize the previous value
							previousValue=amplitudeUnitVector;
						}
						if(buffer==null) {
							// initialize the integral term
							integralTotal=amplitudeUnitVector*getIntegralDepth();
							buffer=getIntegralBuffer();
							for(int j=0;j<getIntegralDepth();j++) {
								buffer[j]=amplitudeUnitVector;
							}
						}
						// update the rolling total
						integralTotal=integralTotal+amplitudeUnitVector-buffer[integralIndex];
						// add current value to the buffer, overwriting previous buffer value
						buffer[integralIndex]=amplitudeUnitVector;
						integralIndex++;
						// wrap the buffer circularly
						if(integralIndex==getIntegralDepth())
							integralIndex=0;
						// @Finn here are the integral and derivitives of amplitude to work with
						double currentRollingAverage=  integralTotal/getIntegralDepth()* getIntegralGain();
						double currentDerivitiveTerm= (amplitudeUnitVector-previousValue)*getDerivitiveGain();
						AudioStatus newStat = lambda.update(status, amplitudeUnitVector, currentRollingAverage, currentDerivitiveTerm);
						boolean change=newStat!=status;
						status=newStat;
						if(i==(nRead-2)) {
							change=true;
						}
						if(change) {
							amountToRead=i-lastIndex;
							total+=amountToRead;
							
							double len = (ais.getFrameLength()*2);
							if(total>=(len-2)) {
								status=AudioStatus.decay;
							}
							double now = total;
							double percent = now/len*100.0;
							speakProgress.update(percent,status);
							getLine().write(abData, lastIndex, amountToRead);
							lastIndex=i;
						}
					}
				}else {
					total+=nRead;
					double len = (ais.getFrameLength()*2);
					double now = total;
					double percent = now/len*100.0;
					if(speakProgress!=null)
						speakProgress.update(percent,status);
					getLine().write(abData, lastIndex, amountToRead);
				}

			}
		}
		if(speakProgress!=null)
			speakProgress.update(100,AudioStatus.decay);
		if (!exitRequested) {
			getLine().drain();
		}
		getLine().close();
	}

	public void setLine(SourceDataLine line) {
		this.line = line;
	}

	/**
	 * @return the speakProgress
	 */
	public ISpeakingProgress getSpeakProgress() {
		return speakProgress;
	}

	/**
	 * @param speakProgress the speakProgress to set
	 */
	public void setSpeakProgress(ISpeakingProgress speakProgress) {
		this.speakProgress = speakProgress;
	}

	/**
	 * @return the threshhold
	 */
	public static double getThreshhold() {
		return threshhold;
	}

	/**
	 * @param threshhold the threshhold to set
	 */
	public static void setThreshhold(double t) {

		threshhold = t;
	}

	/**
	 * @return the lowerThreshhold
	 */
	public static double getLowerThreshhold() {
		return lowerThreshhold;
	}

	/**
	 * @param lowerThreshhold the lowerThreshhold to set
	 */
	public static void setLowerThreshhold(double lt) {

		lowerThreshhold = lt;
	}

	/**
	 * @return the integralDepth
	 */
	public static int getIntegralDepth() {
		return integralDepth;
	}

	/**
	 * @param integralDepth the integralDepth to set
	 */
	public static void setIntegralDepth(int integralDepth) {
		AudioPlayer.integralDepth = integralDepth;
	}

	/**
	 * @return the integralBuffer
	 */
	private double[] getIntegralBuffer() {
		double[] ds = new double[getIntegralDepth()];
		return ds;
	}

	/**
	 * @return the integralGain
	 */
	public static double getIntegralGain() {
		return integralGain;
	}

	/**
	 * @param integralGain the integralGain to set
	 */
	public static void setIntegralGain(double integralGain) {
		AudioPlayer.integralGain = integralGain;
	}

	/**
	 * @return the derivitiveGain
	 */
	public static double getDerivitiveGain() {
		return derivitiveGain;
	}

	/**
	 * @param derivitiveGain the derivitiveGain to set
	 */
	public static void setDerivitiveGain(double derivitiveGain) {
		AudioPlayer.derivitiveGain = derivitiveGain;
	}

	/**
	 * @return the lambda
	 */
	public static IAudioProcessingLambda getLambda() {
		return lambda;
	}

	/**
	 * @param lambda the lambda to set
	 */
	public static void setLambda(IAudioProcessingLambda lambda) {
		AudioPlayer.lambda = lambda;
	}


	
}