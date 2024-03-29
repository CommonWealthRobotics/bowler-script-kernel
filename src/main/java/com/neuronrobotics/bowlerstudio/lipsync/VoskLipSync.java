package com.neuronrobotics.bowlerstudio.lipsync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neuronrobotics.bowlerstudio.AudioStatus;
import com.neuronrobotics.bowlerstudio.IAudioProcessingLambda;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.vosk.Model;
import org.vosk.Recognizer;
import net.lingala.zip4j.ZipFile;

public class VoskLipSync implements IAudioProcessingLambda {
	private static double PercentageTimeOfLipSyncReadahead = 2;
	private static VoskLipSync singelton = null;

	private VoskLipSync() {

	}

	public static VoskLipSync get() {
		if (singelton == null) {
			singelton = new VoskLipSync();
		}
		return singelton;
	}

	class VoskResultWord {
		double conf;
		double end;
		double start;
		String word;

		public String toString() {
			return "\n'" + word + "' \n\tstarts at " + start + " ends at " + end + " confidence " + conf;
		}
	}

	class VoskPartial {
		String partial;
		List<VoskResultWord> partial_result;
	}

	class VoskResultl {
		String text;
		List<VoskResultWord> result;
	}

	Type partailType = new TypeToken<VoskPartial>() {
	}.getType();
	Type resultType = new TypeToken<VoskResultl>() {
	}.getType();

	static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private static Model model;
	private static String modelName;
	private static PhoneticDictionary dict;
	private static AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 60000, 16, 2, 4, 44100, false);

	static {

		setModelName("vosk-model-en-us-daanzu-20200905");
		loadDictionary();
	}

	public static void loadDictionary() {
		try {
			File phoneticDatabaseFile = ScriptingEngine
					.fileFromGit("https://github.com/madhephaestus/TextToSpeechASDRTest.git", "cmudict-0.7b.txt");
			dict = new PhoneticDictionary(phoneticDatabaseFile);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	/**
	 * @return the modelName
	 */
	public static String getModelName() {
		return modelName;
	}

	/**
	 * @param modelName the modelName to set
	 */
	public static void setModelName(String modelName) {
		VoskLipSync.modelName = modelName;
		String pathTOModel = ScriptingEngine.getWorkspace().getAbsolutePath() + "/" + getModelName() + ".zip";
		File zipfile = new File(pathTOModel);
		try {
			if (!zipfile.exists()) {

				String urlStr = "https://alphacephei.com/vosk/models/" + getModelName() + ".zip";
				URL url = new URL(urlStr);
				BufferedInputStream bis = new BufferedInputStream(url.openStream());
				FileOutputStream fis = new FileOutputStream(zipfile);
				byte[] buffer = new byte[1024];
				int count = 0;
				System.out.println("Downloading Vosk Model " + getModelName());
				while ((count = bis.read(buffer, 0, 1024)) != -1) {
					fis.write(buffer, 0, count);
					System.out.print(".");
				}
				fis.close();
				bis.close();

				String source = zipfile.getAbsolutePath();
				String destination = ScriptingEngine.getWorkspace().getAbsolutePath();
				System.out.println("Unzipping Vosk Model " + getModelName());
				ZipFile zipFile = new ZipFile(source);
				zipFile.extractAll(destination);
			}
			model = new Model(ScriptingEngine.getWorkspace().getAbsolutePath() + "/" + getModelName() + "/");
		} catch (Throwable t) {
			t.printStackTrace();
			model = null;
		}
	}

	int numBytesRead = 0;
	int CHUNK_SIZE = 4096;
	byte[] abData = new byte[CHUNK_SIZE];
	ArrayList<TimeCodedViseme> timeCodedVisemes = null;
	ArrayList<TimeCodedViseme> timeCodedVisemesCache = new ArrayList<TimeCodedViseme>();
	int words = 0;
	private double positionInTrack;
	private double timeLeadLag = 0.5;

	private AudioStatus toStatus(String phoneme) {
		AudioStatus s = AudioStatus.getFromPhoneme(phoneme);
		if (s != null)
			return s;
		// println "Unknown phoneme "+phoneme
		return AudioStatus.X_NO_SOUND;
	}

	private void addWord(VoskResultWord word, long len) {

		double secLen = ((double) len) / 1000.0;
		String w = word.word;
		if (w == null)
			return;

		double wordStart = word.start;
		double wordEnd = word.end;
		double wordLen = wordEnd - wordStart;
		ArrayList<String> phonemes = dict.find(w);
		// println w + ", " + wordStart + ", " + phonemes;
		if (phonemes == null) {
			// println "\n\n unknown word "+w+"\n\n"
			return;
		}

		double phonemeLength = wordLen / phonemes.size();

		// Random rand = new Random();
		double timeLeadLag = -(1 / 24.0 / 2048); // -0.0416667 // rand.nextDouble() / 10.0 //0.04

		// @finn this is where to adjust the lead/lag of the lip sync with the audio
		// playback
		// mtc -- this is where we can fuck with sequencing and add transition frames.
		// the transition's probably going to require some sort of javaFX bullshit but
		// we'll see.
		for (int i = 0; i < phonemes.size(); i++) {
			String phoneme = phonemes.get(i);
			AudioStatus stat = toStatus(phoneme);

			// short the LeadLag for the H_L_SOUNDS viseme
			if (stat == AudioStatus.H_L_SOUNDS) {
				timeLeadLag = -(1 / 24.0 / 128);
			}

			double myStart = Math.max(wordStart + phonemeLength * ((double) i) + timeLeadLag, 0);
			double myEnd = wordStart + phonemeLength * ((double) (i + 1)) + timeLeadLag;
			double segLen = myEnd - myStart;
			TimeCodedViseme tc = new TimeCodedViseme(stat, myStart, myEnd, secLen);

			// adds a transitional silent viseme when a silence is detected
			if (timeCodedVisemes.size() > 0) {
				TimeCodedViseme tcLast = timeCodedVisemes.get(timeCodedVisemes.size() - 1);
				if (myStart - tcLast.end > 0.03) {

					// for longer pauses, transition through partially open mouth to close
					double siLength = myStart - tcLast.end;
					double hLength = siLength / 3.0;
					double mouthClosedTime = myStart - hLength;

					TimeCodedViseme tcSilentH = new TimeCodedViseme(AudioStatus.H_L_SOUNDS, tcLast.end, mouthClosedTime,
							secLen);
					TimeCodedViseme tcSilentX = new TimeCodedViseme(AudioStatus.X_NO_SOUND, mouthClosedTime, myStart,
							secLen);

					// println "ln 297";
					add(tcSilentH);
					add(tcSilentX);
				} else if (myStart - tcLast.end > 0) {
					// short transition to partially open mouth
					TimeCodedViseme tcSilent = new TimeCodedViseme(AudioStatus.H_L_SOUNDS, tcLast.end, myStart, secLen);
					add(tcSilent);
				}
			}

			// looks for transition situations between visemes within a word (i.e. it bails
			// at the last syllable)
			if (i < phonemes.size() - 1) {
				String next_phoneme = phonemes.get(i + 1);
				AudioStatus stat_next = toStatus(next_phoneme);
				// identifies transition sitautions
				// ⒶⒸⒹ and ⒷⒸⒹ
				// ⒸⒺⒻ and ⒹⒺⒻ
				if (
				// A or B preceeding D
				(stat_next == AudioStatus.D_AA_SOUNDS
						&& (stat == AudioStatus.A_PBM_SOUNDS || stat == AudioStatus.B_KST_SOUNDS)) ||
				// D preceeding A or B
						((stat_next == AudioStatus.A_PBM_SOUNDS || stat_next == AudioStatus.B_KST_SOUNDS)
								&& stat == AudioStatus.D_AA_SOUNDS)
						||
						// C or D preceeding an F
						(stat_next == AudioStatus.F_UW_OW_W_SOUNDS
								&& (stat == AudioStatus.C_EH_AE_SOUNDS || stat == AudioStatus.D_AA_SOUNDS))
						||
						// F preceeding a C or D
						((stat_next == AudioStatus.C_EH_AE_SOUNDS || stat_next == AudioStatus.D_AA_SOUNDS)
								&& stat == AudioStatus.F_UW_OW_W_SOUNDS)) {
					// println "transition situation detected";

					// determine the current length of the viseme, and the length and start point of
					// the transition to be applied
					double visLength = tc.end - tc.start;
					double transLength = visLength / 3.0;
					double transStart = tc.end - transLength;

					AudioStatus transViseme = tc.status;

					// based on the situation, set the appropriate transition viseme
					if (stat_next == AudioStatus.F_UW_OW_W_SOUNDS || stat == AudioStatus.F_UW_OW_W_SOUNDS) {
						// C or D found preceeding an F, or
						// F found preceeding a C or D
						// println "E_AO_ER inserted"
						transViseme = AudioStatus.E_AO_ER_SOUNDS;
					} else if (stat_next == AudioStatus.D_AA_SOUNDS || stat == AudioStatus.D_AA_SOUNDS) {
						// A or B found preceeding a D, or
						// D found preceeding an A or B
						// println "C_EH_AE inserted"
						transViseme = AudioStatus.C_EH_AE_SOUNDS;
					} else {
						// println "ERR_TRANSITION"
					}

					// create the transition viseme
					TimeCodedViseme tc_transition = new TimeCodedViseme(transViseme, transStart, tc.end, secLen);

					// push back the end point of the main viseme to the start point of the
					// transition viseme
					tc.end = transStart;

					// add the modified original viseme, and then the transition viseme
					add(tc);
					add(tc_transition);
				} else {
					// handles situations within words where the following viseme does not require a
					// transition
					add(tc);
				}
			} else {
				// handles situations at the end of words
				add(tc);
			}
		}

		// println "Word "+w+" starts at "+wordStart+" ends at "+wordEnd+" each phoneme
		// length "+phonemeLength+" "+phonemes+" "+timeCodedVisemes

	}

	private void add(TimeCodedViseme v) {
		// println "Adding "+ v
		timeCodedVisemes.add(v);
		timeCodedVisemesCache.add(v);

	}

	private void processWords(List<VoskResultWord> wordList, long len) {
		if (wordList == null)
			return;

		for (; words < wordList.size(); words++) {
			VoskResultWord word = wordList.get(words);
			addWord(word, len);
		}

	}

	public void processRaw(File f, String ttsLocation) throws UnsupportedAudioFileException, IOException {

		words = 0;
		positionInTrack = 0;
		AudioInputStream getAudioInputStream = AudioSystem.getAudioInputStream(f);
		long durationInMillis = (long) (1000 * getAudioInputStream.getFrameLength()
				/ getAudioInputStream.getFormat().getFrameRate());
		long start = System.currentTimeMillis();
		timeCodedVisemesCache.clear();
		Thread t = new Thread(() -> {
			try {

				double secLen = ((double) durationInMillis) / 1000.0;
				AudioInputStream ais = AudioSystem.getAudioInputStream(format, getAudioInputStream);
				Recognizer recognizer = new Recognizer(model, 120000);
				recognizer.setWords(true);
				recognizer.setPartialWords(true);
				numBytesRead = 0;
				long total = 0;
				while ((numBytesRead != -1) && (!Thread.interrupted())) {
					numBytesRead = ais.read(abData, 0, abData.length);
					total += numBytesRead;
					double tmpTotal = total;
					double len = (ais.getFrameLength() * 2);
					positionInTrack = tmpTotal / len * 100.0;

					if (recognizer.acceptWaveForm(abData, numBytesRead)) {
						String result = recognizer.getResult();
						VoskResultl database = gson.fromJson(result, resultType);
						processWords(database.result, durationInMillis);
					} else {
						String result = recognizer.getPartialResult();
						VoskPartial database = gson.fromJson(result, partailType);
						processWords(database.partial_result, durationInMillis);
					}
				}
				VoskResultl database = gson.fromJson(recognizer.getFinalResult(), resultType);
				recognizer.close();
				processWords(database.result, durationInMillis);
				positionInTrack = 100;
				if (timeCodedVisemes.size() > 0) {
					TimeCodedViseme tcLast = timeCodedVisemes.get(timeCodedVisemes.size() - 1);
					// termination sound of nothing
					TimeCodedViseme tc = new TimeCodedViseme(AudioStatus.X_NO_SOUND, tcLast.end, secLen, secLen);
					add(tc);
				}
				File json = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/tmp-tts-visime.json");
				if (!json.exists()) {
					json.createNewFile();
				}
				String s = gson.toJson(timeCodedVisemesCache);
				BufferedWriter writer = new BufferedWriter(new FileWriter(json.getAbsolutePath()));
				writer.write(s);
				writer.close();
				System.out.println("Lip Sync data writen to " + json.getAbsolutePath());
				timeCodedVisemesCache.clear();
			} catch (Throwable tr) {
				tr.printStackTrace();
			}
		});
		t.start();

		while (t.isAlive() && positionInTrack < getPercentageTimeOfLipSyncReadahead()
				&& (System.currentTimeMillis() - start < durationInMillis)) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {
				break;
			}
		}
		if (t.isAlive()) {
			t.interrupt();
		}
		// println "Visemes added, start audio.. "
	}

	public AudioInputStream startProcessing(AudioInputStream ais, String TTSString) {
		timeCodedVisemes = new ArrayList<>();

		File audio = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/tmp-tts.wav");
		try {
			long start = System.currentTimeMillis();
			System.out.println("Vosk Lip Sync Begin writing..");
			AudioSystem.write(ais, AudioFileFormat.Type.WAVE, audio);
			ais = AudioSystem.getAudioInputStream(audio);
			File text = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/tmp-tts.txt");
			if (!text.exists())
				text.createNewFile();
			try {
				FileWriter myWriter = new FileWriter(text);
				myWriter.write(TTSString);
				myWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			// rhubarb!
			processRaw(audio, text.getAbsolutePath());
			System.out.println("Vosk Lip Sync Done writing! took " + (System.currentTimeMillis() - start));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return ais;
	}

	public AudioStatus update(AudioStatus current, double amplitudeUnitVector, double currentRollingAverage,
			double currentDerivitiveTerm, double percent) {
		// println timeCodedVisemes
		AudioStatus ret = null;
		if (timeCodedVisemes.size() > 0) {
			TimeCodedViseme map = timeCodedVisemes.get(0);
			AudioStatus key = map.status;
			double value = map.getEndPercentage();
			if (percent > value) {
				timeCodedVisemes.remove(0);
				if (timeCodedVisemes.size() > 0)
					ret = timeCodedVisemes.get(0).status;
				else {
					// println "\n\nERROR Audio got ahead of lip sync "+percent+"\n\n"
					ret = AudioStatus.X_NO_SOUND;
				}
			} else if (percent > map.getStartPercentage())
				ret = key;
		} else {
			// println "\n\nERROR Audio got ahead of lip sync "+percent+"\n\n"
		}
		if (ret == null)
			ret = current;
		if (current != ret) {
			// println ret.toString()+" staarting at "+percent
		}
		return ret;

	}

	public static String promptFromMicrophone() throws IOException, LineUnavailableException {
		if (model == null)
			throw new RuntimeException("Vosk Model failed to load, check "
					+ ScriptingEngine.getWorkspace().getAbsolutePath() + "/" + getModelName());
		Recognizer recognizer = new Recognizer(model, 120000);

		DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
		TargetDataLine microphone;
		microphone = (TargetDataLine) AudioSystem.getLine(info);
		microphone.open(format);
		microphone.start();

		int numBytesRead;
		int CHUNK_SIZE = 1024;
		int bytesRead = 0;

		byte[] b = new byte[4096];
		// println "Listening..."
		String result = null;
		long start = System.currentTimeMillis();
		Type STTType = new TypeToken<HashMap<String, String>>() {
		}.getType();
		try {
			while (((System.currentTimeMillis() - start) < 30000) && !Thread.interrupted()) {
				// Thread.sleep(0,100);
				numBytesRead = microphone.read(b, 0, CHUNK_SIZE);
				bytesRead += numBytesRead;

				if (recognizer.acceptWaveForm(b, numBytesRead)) {
					result = recognizer.getResult();
					HashMap<String, String> db = gson.fromJson(result, STTType);
					result = db.get("text");
					if (result.length() > 2)
						break;
					else {
						// println "Listening..."
					}
				} else {
					// System.out.println(recognizer.getPartialResult());
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
		recognizer.close();
		// System.out.println(result);
		microphone.close();
		return result;
	}

	/**
	 * @return the percentageTimeOfLipSyncReadahead
	 */
	public static double getPercentageTimeOfLipSyncReadahead() {
		return PercentageTimeOfLipSyncReadahead;
	}

	/**
	 * @param percentageTimeOfLipSyncReadahead the percentageTimeOfLipSyncReadahead
	 *                                         to set
	 */
	public static void setPercentageTimeOfLipSyncReadahead(double percentageTimeOfLipSyncReadahead) {
		PercentageTimeOfLipSyncReadahead = percentageTimeOfLipSyncReadahead;
	}

	/**
	 * @return the timeLeadLag
	 */
	public double getTimeLeadLag() {
		return timeLeadLag;
	}

	/**
	 * @param timeLeadLag the timeLeadLag to set
	 */
	public void setTimeLeadLag(double timeLeadLag) {
		this.timeLeadLag = timeLeadLag;
	}

}
