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
	int words = 0;
	private double positionInTrack;

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
		if (phonemes == null) {
			// println "\n\n unknown word "+w+"\n\n"
			return;
		}

		double phonemeLength = wordLen / phonemes.size();
		for (int i = 0; i < phonemes.size(); i++) {
			String phoneme = phonemes.get(i);
			AudioStatus stat = toStatus(phoneme);
			double myStart = wordStart + phonemeLength * ((double) i);
			double myEnd = wordStart + phonemeLength * ((double) (i + 1));
			TimeCodedViseme tc = new TimeCodedViseme(stat, myStart, myEnd, secLen);
			if (timeCodedVisemes.size() > 0) {
				TimeCodedViseme tcLast = timeCodedVisemes.get(timeCodedVisemes.size() - 1);
				if (tcLast.end < myStart) {
					// termination sound of nothing
					TimeCodedViseme tcSilent = new TimeCodedViseme(AudioStatus.X_NO_SOUND, tcLast.end, myStart, secLen);
					add(tcSilent);
				}
			}
			add(tc);
		}

		// println "Word "+w+" starts at "+wordStart+" ends at "+wordEnd+" each phoneme
		// length "+phonemeLength+" "+phonemes+" "+timeCodedVisemes

	}

	private void add(TimeCodedViseme v) {
		// println "Adding "+ v
		timeCodedVisemes.add(v);
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
			} catch (Throwable tr) {
				// BowlerStudio.printStackTrace(t);
			}
		});
		t.start();

		while (t.isAlive() && positionInTrack < 1 && (System.currentTimeMillis() - start < durationInMillis)) {
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
		if(model==null)
			throw new RuntimeException("Vosk Model failed to load, check "+ScriptingEngine.getWorkspace().getAbsolutePath() + "/" + getModelName());
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
		//println "Listening..."
		String result=null;
		long start = System.currentTimeMillis();
		Type STTType = new TypeToken<HashMap<String, String>>() {}.getType();
		try{
			while (((System.currentTimeMillis()-start)<30000) && !Thread.interrupted()) {
				//Thread.sleep(0,100);
				numBytesRead = microphone.read(b, 0, CHUNK_SIZE);
				bytesRead += numBytesRead;

				if (recognizer.acceptWaveForm(b, numBytesRead)) {
					result=recognizer.getResult();
					HashMap<String, String> db = gson.fromJson(result, STTType);
					result = db.get("text");
					if(result.length()>2)
						break;
					else {
						//println "Listening..."
					}
				} else {
					//System.out.println(recognizer.getPartialResult());
				}
			}
		}catch(Throwable t){
			t.printStackTrace();
		}
		System.out.println(result);
		microphone.close();
		return result;
	}

}
