package com.neuronrobotics.bowlerstudio;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.context.annotation.Configuration;

import jline.ConsoleReader;
import jline.Terminal;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import marytts.signalproc.effects.LpcWhisperiserEffect;
import marytts.signalproc.effects.RobotiserEffect;
import marytts.signalproc.effects.ChorusEffectBase;
import marytts.signalproc.effects.HMMDurationScaleEffect;
import marytts.signalproc.effects.VolumeEffect;

public class BowlerKernel {

	private static final String CSG = null;
	private static File historyFile = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/bowler.history");

	static {
		historyFile = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/bowler.history");
		ArrayList<String> history = new ArrayList<>();
		if (!historyFile.exists()) {
			try {
				historyFile.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			history.add("println SDKBuildInfo.getVersion()");
			history.add("for(int i=0;i<1000000;i++) { println dyio.getValue(0) }");
			history.add("dyio.setValue(0,128)");
			history.add("println dyio.getValue(0)");
			history.add("ScriptingEngine.inlineGistScriptRun(\"d4312a0787456ec27a2a\", \"helloWorld.groovy\" , null)");
			history.add("DeviceManager.addConnection(new DyIO(ConnectionDialog.promptConnection()),\"dyio\")");
			history.add("DeviceManager.addConnection(new DyIO(new SerialConnection(\"/dev/DyIO0\")),\"dyio\")");
			history.add("shellType Clojure #Switches shell to Clojure");
			history.add("shellType Jython #Switches shell to Python");
			history.add("shellType Groovy #Switches shell to Groovy/Java");

			history.add("println \"Hello world!\"");

			writeHistory(history);
		}
	}

	private static void fail() {
		System.err.println(
				"Usage: \r\njava -jar BowlerScriptKernel.jar -s <file 1> .. <file n> # This will load one script after the next ");
		System.err.println(
				"java -jar BowlerScriptKernel.jar -p <file 1> .. <file n> # This will load one script then take the list of objects returned and pss them to the next script as its 'args' variable ");
		System.err.println(
				"java -jar BowlerScriptKernel.jar -r <Groovy Jython or Clojure> (Optional)(-s or -p)<file 1> .. <file n> # This will start a shell in the requested langauge and run the files provided. ");
		System.err.println("java -jar BowlerScriptKernel.jar -g <Git repo> <Git file> # this will run a file from git");

		System.exit(1);
	}

	/**
	 * @param args the command line arguments
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		JavaFXInitializer.go();
		ScriptingEngine.waitForLogin();
		if (args.length == 0) {
			fail();
		}
		ScriptingEngine.gitScriptRun("https://github.com/CommonWealthRobotics/DeviceProviders.git",
				"loadAll.groovy",null);
		boolean gitRun = false;
		String gitRepo = null;
		String gitFile = null;
		for (String s : args) {

			if (gitRun) {
				if (gitRepo == null) {
					gitRepo = s;
				} else if (gitFile == null) {
					gitFile = s;
				}
			}
			if (s.contains("git") || s.contains("-g")) {
				gitRun = true;
			}
		}
		if (gitRun && gitRepo != null ) {
			
				ScriptingEngine.pull(gitRepo);
				ArrayList<String> files = ScriptingEngine.filesInGit(gitRepo);
				boolean fileExists = false;
				if(gitFile==null)
					gitFile="launch";
				if(gitFile.endsWith("/"))
					gitFile+="launch";
				for(String f:files) {
					if(f.startsWith(gitFile)) {
						gitFile=f;
						fileExists=true;
					}
				}
				if(!fileExists) {
					System.err.println("\n\nERROR file does not exist: "+gitFile);
					gitFile=null;
				}
				if( gitFile != null)
					try {
						ScriptingEngine.gitScriptRun(gitRepo, gitFile, null);
					} catch (Throwable e) {
						e.printStackTrace();
						fail();
					}
				else {
					System.out.println("Files in git:");
					for(String f:files) {
						System.out.println("\t"+f);
					}
				}
		}
//		File servo = ScriptingEngine.fileFromGit("https://github.com/CommonWealthRobotics/BowlerStudioVitamins.git",
//							"BowlerStudioVitamins/stl/servo/smallservo.stl");
//		
//		ArrayList<CSG>  cad = (ArrayList<CSG> )ScriptingEngine.inlineGistScriptRun("4814b39ee72e9f590757", "javaCad.groovy" , null);
//		System.out.println(servo.exists()+" exists: "+servo);

		boolean startLoadingScripts = false;
		Object ret = null;
		for (String s : args) {
			if (startLoadingScripts) {
				try {

					ret = ScriptingEngine.inlineFileScriptRun(new File(s), null);
				} catch (Throwable e) {
					e.printStackTrace();
					fail();
				}
			}
			if (s.contains("script") || s.contains("-s")) {
				startLoadingScripts = true;
			}
		}
		startLoadingScripts = false;

		for (String s : args) {

			if (startLoadingScripts) {
				try {
					ret = ScriptingEngine.inlineFileScriptRun(new File(s), (ArrayList<Object>) ret);
				} catch (Throwable e) {
					e.printStackTrace();
					fail();
				}
			}
			if (s.contains("pipe") || s.contains("-p")) {
				startLoadingScripts = true;
			}
		}
		boolean runShell = false;
		String groovy = "Groovy";
		String shellTypeStorage = groovy;
		for (String s : args) {

			if (runShell) {
				try {
					shellTypeStorage = s;
				} catch (Throwable e) {
					shellTypeStorage = groovy;
				}
				break;
			}
			if (s.contains("repl") || s.contains("-r")) {
				runShell = true;
			}
		}

		if (!runShell)
			System.exit(0);

		System.out.println("Starting Bowler REPL in langauge: " + shellTypeStorage);
		// sample from
		// http://jline.sourceforge.net/testapidocs/src-html/jline/example/Example.html

		if (!Terminal.getTerminal().isSupported()) {
			System.out.println("Terminal not supported " + Terminal.getTerminal());
		}
		// Terminal.getTerminal().initializeTerminal();

		ConsoleReader reader = new ConsoleReader();
		reader.addTriggeredAction(Terminal.CTRL_C, e -> {
			System.exit(0);
		});

		if (!historyFile.exists()) {
			historyFile.createNewFile();
			reader.getHistory().addToHistory("println SDKBuildInfo.getVersion()");
			reader.getHistory().addToHistory("for(int i=0;i<100;i++) { println dyio.getValue(0) }");
			reader.getHistory().addToHistory("dyio.setValue(0,128)");
			reader.getHistory().addToHistory("println dyio.getValue(0)");
			reader.getHistory().addToHistory(
					"ScriptingEngine.inlineGistScriptRun(\"d4312a0787456ec27a2a\", \"helloWorld.groovy\" , null)");
			reader.getHistory().addToHistory(
					"DeviceManager.addConnection(new DyIO(ConnectionDialog.promptConnection()),\"dyio\")");
			reader.getHistory().addToHistory(
					"DeviceManager.addConnection(new DyIO(new SerialConnection(\"/dev/DyIO0\")),\"dyio\")");
			reader.getHistory().addToHistory("BowlerKernel.speak(\"Text to speech works like this\")");
			reader.getHistory().addToHistory("ScriptingEngine.gitScriptRun(\\\"https://github.com/OperationSmallKat/greycat.git\\\", \\\"launch.groovy\\\" , null)");
			reader.getHistory().addToHistory("println \"Hello world!\"");
			writeHistory(reader.getHistory().getHistoryList());
		} else {
			List<String> history = loadHistory();
			for (String h : history) {
				reader.getHistory().addToHistory(h);
			}
		}
		reader.setBellEnabled(false);
		reader.setDebug(new PrintWriter(new FileWriter("writer.debug", true)));

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				Thread.currentThread().setUncaughtExceptionHandler(new IssueReportingExceptionHandler());

				writeHistory(reader.getHistory().getHistoryList());
			}
		});

		// SpringApplication.run(SpringBowlerUI.class, new String[]{});

		String line;
		try {
			while ((line = reader.readLine("Bowler " + shellTypeStorage + "> ")) != null) {
				if (line.equalsIgnoreCase("quit") || line.equalsIgnoreCase("exit")) {
					break;
				}
				if (line.equalsIgnoreCase("history") || line.equalsIgnoreCase("h")) {
					List<String> h = reader.getHistory().getHistoryList();
					for (String s : h) {
						System.out.println(s);
					}
					continue;
				}
				if (line.startsWith("shellType")) {
					try {
						shellTypeStorage = line.split(" ")[1];
					} catch (Exception e) {
						shellTypeStorage = groovy;
					}
					continue;
				}
				try {
					ret = ScriptingEngine.inlineScriptStringRun(line, null, shellTypeStorage);
					if (ret != null) {
						System.out.println(ret);
					}
				} catch (Error e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	public static ArrayList<String> loadHistory() throws IOException {
		ArrayList<String> history = new ArrayList<>();
		// Construct BufferedReader from FileReader
		BufferedReader br = new BufferedReader(new FileReader(historyFile));

		String line = null;
		while ((line = br.readLine()) != null) {
			history.add(line);
		}
		br.close();
		return history;
	}

	public static void writeHistory(List<String> history) {
		System.out.println("Saving history");
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(historyFile);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
			for (String s : history) {
				bw.write(s);
				bw.newLine();
			}

			bw.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public static int speak(String msg) {

		return speak(msg, 200, 0, 100, 1.0, 1.0);
	}

	@SuppressWarnings("unused")
	public static int speak(String msg, Number rate, Number pitch, Number range, Number shift, Number volume) {
		if (rate.doubleValue() > 300)
			rate = 300;
		if (rate.doubleValue() < 10)
			rate = 10;
		TextToSpeech tts = new TextToSpeech();
		// cd ..
		tts.getAvailableVoices().stream().forEach(voice -> System.out.println("Voice: " + voice));
		// Setting the Current Voice
		// tts.setVoice(tts.getAvailableVoices().toArray()[0].toString());

		if (range.doubleValue() > 200)
			tts.setVoice("cmu-slt-hsmm");
		else if (range.doubleValue() > 100)
			tts.setVoice("dfki-spike-hsmm");
		else if (range.doubleValue() > 50)
			tts.setVoice("dfki-prudence-hsmm");
		else
			tts.setVoice("dfki-poppy-hsmm");

		RobotiserEffect vocalTractLSE = new RobotiserEffect(); // russian drunk effect
		vocalTractLSE.setParams("amount:" + pitch.intValue());

		// TTS say something that we actually are typing into the first variable
//	tts.getAudioEffects().stream().forEach(audioEffect -> {
//		if(audioEffect.getName().contains("Rate")) {
//		System.out.println("-----Name-----");
//		System.out.println(audioEffect.getName());
//		System.out.println("-----Examples-----");
//		System.out.println(audioEffect.getExampleParameters());
//		System.out.println("-----Help Text------");
//		System.out.println(audioEffect.getHelpText() + "\n\n");
//		}
//	});
		String effect = "";
		if (volume.doubleValue() < 0.5) {

			LpcWhisperiserEffect lpcWhisperiserEffect = new LpcWhisperiserEffect(); // creepy
			lpcWhisperiserEffect.setParams("amount:" + (50 + (50 * volume.doubleValue())));
			effect += "+" + lpcWhisperiserEffect.getFullEffectAsString();
			volume = 1;
		}
		if (shift.doubleValue() < 1) {
			ChorusEffectBase ce = new ChorusEffectBase();
			ce.setParams("delay1:" + (int) (366.0 * shift.doubleValue())
					+ ";amp1:0.54;delay2:600;amp2:-0.10;delay3:250;amp3:0.30");
			effect += "+" + ce.getFullEffectAsString();
		}
		// Apply the effects
		// ----You can add multiple effects by using the method
		// `getFullEffectAsString()` and + symbol to connect with the other effect that
		// you want
		// ----check the example below
		VolumeEffect volumeEffect = new VolumeEffect(); // be careful with this i almost got heart attack
		volumeEffect.setParams("amount:" + volume);

		HMMDurationScaleEffect ratEff = new HMMDurationScaleEffect();
		ratEff.setParams("durScale:" + rate.doubleValue() / 100.0);

		effect += "+" + ratEff.getFullEffectAsString();
		effect += "+" + volumeEffect.getFullEffectAsString();
		if (pitch.intValue() > 0)
			effect += "+" + vocalTractLSE.getFullEffectAsString();
		System.out.println(msg + "-->" + effect);
		tts.getMarytts().setAudioEffects(effect);

		tts.speak(msg, 2.0f, false, true);

		return 0;
	}

	public static void upenURL(String string) {
		

		System.err.println("Opening "+string);
	    try {
	    	upenURL(new URI(string));
	    	
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Throwable e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void upenURL(URI htmlUrl) {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
			try {
				Desktop.getDesktop().browse(htmlUrl);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

}
