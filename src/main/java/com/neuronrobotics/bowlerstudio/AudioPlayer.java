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
	private static int threshhold = 600;
	private static int lowerThreshhold = 100;
	
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
		int[] intData = new int[abData.length/2];
		int total = 0;
		AudioStatus status = AudioStatus.release;

		while ( ( nRead != -1 ) && ( !exitRequested )) {
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
						int b1=abData[i];
						if(b1<0)
							b1+=256;
						int b2=abData[i+1];
						if(b2<0)
							b2+=256;
						int amplitude16Bit=(b1<<8)+(b2);
						intData[i/2]=amplitude16Bit;
						boolean change=false;
						switch(status) {
						case attack:
							if(amplitude16Bit>getThreshhold()) {
								status=AudioStatus.sustain;
							}
							break;
						case decay:
							if(amplitude16Bit<getLowerThreshhold()) {
								status=AudioStatus.release;
							}
							break;
						case release:
							if(amplitude16Bit>getThreshhold()) {
								status=AudioStatus.attack;
								change=true;
							}
							break;
						case sustain:
							if(amplitude16Bit<getLowerThreshhold()) {
								status=AudioStatus.decay;
								change=true;
							}
							break;
						default:
							break;
						}
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
	public static int getThreshhold() {
		return threshhold;
	}

	/**
	 * @param threshhold the threshhold to set
	 */
	public static void setThreshhold(int t) {
		threshhold = t;
	}

	/**
	 * @return the lowerThreshhold
	 */
	public static int getLowerThreshhold() {
		return lowerThreshhold;
	}

	/**
	 * @param lowerThreshhold the lowerThreshhold to set
	 */
	public static void setLowerThreshhold(int lt) {
		lowerThreshhold = lt;
	}
	
}