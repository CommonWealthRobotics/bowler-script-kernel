package com.neuronrobotics.bowlerstudio;

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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.context.annotation.Configuration;








import jline.ConsoleReader;
import jline.History;
import jline.Terminal;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.ShellType;
import com.neuronrobotics.imageprovider.OpenCVJNILoader;
import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.VoiceManager;
import com.sun.speech.freetts.en.us.FeatureProcessors.WordNumSyls;

public class BowlerKernel {

	private static void fail() {
		System.err
				.println("Usage: \r\njava -jar BowlerScriptKernel.jar -s <file 1> .. <file n> # This will load one script after the next ");
		System.err
				.println("java -jar BowlerScriptKernel.jar -p <file 1> .. <file n> # This will load one script then take the list of objects returned and pss them to the next script as its 'args' variable ");
		System.err
				.println("java -jar BowlerScriptKernel.jar -r <Groovy Jython or Clojure> (Optional)(-s or -p)<file 1> .. <file n> # This will start a shell in the requested langauge and run the files provided. ");
		
		System.exit(1);
	}

	/**
	 * @param args
	 *            the command line arguments
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {

		if (args.length == 0) {
			fail();
		}
		OpenCVJNILoader.load(); // Loads the OpenCV JNI (java native interface)
		boolean startLoadingScripts = false;
		Object ret = null;
		for (String s : args) {
			if (startLoadingScripts) {
				try {

					ret = ScriptingEngine
							.inlineFileScriptRun(new File(s), null);
				} catch (Error e) {
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
					ret = ScriptingEngine.inlineFileScriptRun(new File(s),
							(ArrayList<Object>) ret);
				} catch (Error e) {
					e.printStackTrace();
					fail();
				}
			}
			if (s.contains("pipe") || s.contains("-p")) {
				startLoadingScripts = true;
			}
		}
		boolean runShell = false;
		ShellType shellTypeStorage = ShellType.GROOVY;
		for (String s : args) {

			if (runShell) {
				try {
					shellTypeStorage = ShellType.getFromSlug(s);
				} catch (Exception e) {
					shellTypeStorage = ShellType.GROOVY;
				}
				break;
			}
			if (s.contains("repl") || s.contains("-r")) {
				runShell = true;
			}
		}
		
		System.out.println("Starting Bowler REPL in langauge: "
				+ shellTypeStorage.getNameOfShell());
		// sample from
		// http://jline.sourceforge.net/testapidocs/src-html/jline/example/Example.html

		if (!Terminal.getTerminal().isSupported()) {
			System.out.println("Terminal not supported "
					+ Terminal.getTerminal());
		}
		//Terminal.getTerminal().initializeTerminal();
		
		ConsoleReader reader = new ConsoleReader();
		reader.addTriggeredAction(Terminal.CTRL_C, e -> {
			System.exit(0);
		});
		
		File historyFile= new File(ScriptingEngine.getWorkspace().getAbsolutePath()+"/bowler.history");
		if(!historyFile.exists()){
			historyFile.createNewFile();
			reader.getHistory().addToHistory("println SDKBuildInfo.getVersion()");
			reader.getHistory().addToHistory("for(int i=0;i<1000000;i++) { println dyio.getValue(0) }");
			reader.getHistory().addToHistory("dyio.setValue(0,128)");
			reader.getHistory().addToHistory("println dyio.getValue(0)");
			reader.getHistory().addToHistory("ScriptingEngine.inlineGistScriptRun(\"d4312a0787456ec27a2a\", \"helloWorld.groovy\" , null)");
			reader.getHistory().addToHistory("DeviceManager.addConnection(new DyIO(ConnectionDialog.promptConnection()),\"dyio\")");
			reader.getHistory().addToHistory("DeviceManager.addConnection(new DyIO(new SerialConnection(\"/dev/DyIO0\")),\"dyio\")");
			reader.getHistory().addToHistory("shellType Clojure #Switches shell to Clojure");
			reader.getHistory().addToHistory("shellType Jython #Switches shell to Python");
			reader.getHistory().addToHistory("shellType Groovy #Switches shell to Groovy/Java");

			reader.getHistory().addToHistory("println \"Hello world!\"");
			
			
			writeHistory(historyFile,reader.getHistory());
		}else{
			// Construct BufferedReader from FileReader
			BufferedReader br = new BufferedReader(new FileReader(historyFile));
		 
			String line = null;
			while ((line = br.readLine()) != null) {
				reader.getHistory().addToHistory(line);
			}
		 
			br.close();
		}
		reader.setBellEnabled(false);
		reader.setDebug(new PrintWriter(new FileWriter("writer.debug", true)));
		
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				
				writeHistory(historyFile,reader.getHistory());
			}
		});
		
		//SpringApplication.run(SpringBowlerUI.class, new String[]{});
		
		String line;		
		try {
			while ((line = reader.readLine("Bowler " + shellTypeStorage.getNameOfShell()
					+ "> ")) != null) {
				if (line.equalsIgnoreCase("quit")
						|| line.equalsIgnoreCase("exit")) {
					break;
				}
				if (line.equalsIgnoreCase("history")
						|| line.equalsIgnoreCase("h")) {
					List<String> h = reader.getHistory().getHistoryList();
					for(String s:h){
						System.out.println(s);
					}
					continue;
				}
				if(line.startsWith("shellType")){
					try {
						shellTypeStorage = ShellType.getFromSlug(line.split(" ")[1]);
					} catch (Exception e) {
						shellTypeStorage = ShellType.GROOVY;
					}
					continue;
				}
				try {
					ret =ScriptingEngine.inlineScriptRun(line, null,
							shellTypeStorage);
					if(ret !=null)
						System.out.println(ret);
				}catch (Error e) {
					e.printStackTrace();
				}catch (Exception e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
	}
	
	public static void writeHistory(File historyFile,History history){
		System.out.println("Saving history");
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(historyFile);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
			
			List<String> h = history.getHistoryList();
			for(String s:h){
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

	public static int speak(String msg){
		System.setProperty("freetts.voices", "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");
		VoiceManager voiceManager = VoiceManager.getInstance();
		com.sun.speech.freetts.Voice voice = voiceManager
				.getVoice("kevin16");
		Thread t = new Thread() {
			public void run() {
				setName("Speaking Thread");

				
				if(voice !=null){
					voice.setRate(200f);
					voice.allocate();
					voice.speak(msg);
					voice.deallocate();
				}else{
					System.out.println("All voices available:");
					com.sun.speech.freetts.Voice[] voices=voiceManager.getVoices();
					for (int i=0; i < voices.length; i++) {
					  System.out.println("    " + voices[i].getName() + " ("+ voices[i].getDomain()+ " domain)");
					}
				}
			}
		};
		t.start();
		WordNumSyls feature = (WordNumSyls)voice.getFeatureProcessor("word_numsyls");
		if(feature!=null)
		try {
			
			System.out.println("Syllables# = "+feature.process(null));
		} catch (ProcessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return 0;
	}

}
