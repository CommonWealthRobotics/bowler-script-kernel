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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.stage.Stage;
import javafx.scene.layout.*;

//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
//import org.springframework.context.annotation.ComponentScan;
//import org.springframework.context.annotation.Configuration;

import jline.ConsoleReader;
import jline.Terminal;

import com.neuronrobotics.bowlerstudio.assets.FontSizeManager;
import com.neuronrobotics.bowlerstudio.creature.CadFileExporter;
import com.neuronrobotics.bowlerstudio.creature.IMobileBaseUI;
import com.neuronrobotics.bowlerstudio.creature.IgenerateBed;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.bowlerstudio.printbed.PrintBedManager;
import com.neuronrobotics.bowlerstudio.scripting.CaDoodleLoader;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.CSGServer;
import eu.mihosoft.vrl.v3d.ICSGProgress;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.transform.Affine;
import marytts.signalproc.effects.LpcWhisperiserEffect;
import marytts.signalproc.effects.RobotiserEffect;
import marytts.signalproc.effects.ChorusEffectBase;
import marytts.signalproc.effects.HMMDurationScaleEffect;
import marytts.signalproc.effects.VolumeEffect;

public class BowlerKernel {

	// private static final String CSG = null;
	private static File historyFile = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/bowler.history");
	private static boolean kernelMode = true;
	public static void ensureUpdated(boolean check,String ... urls) {
		for(String s:urls) {
			
			if(s==null)
				continue;
			try {
				File wd = ScriptingEngine.getRepositoryCloneDirectory(s);
				if(check && wd.exists()) {
					Log.error("Skipping update, clone exists "+s);
					continue;
				}
				Log.debug("Pulling "+s);
				ScriptingEngine.cloneRepo(s, null);
				ScriptingEngine.pull(s);
			} catch (Throwable e) {
				// Auto-generated catch block
				e.printStackTrace();
				//ScriptingEngine.deleteRepo(s);
				//ScriptingEngine.cloneRepo(s, null);
			}
		}
	}
	private static void loadHistoryLocal() {
		historyFile = new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/bowler.history");
		ArrayList<String> history = new ArrayList<>();
		if (!historyFile.exists()) {
			try {
				historyFile.createNewFile();
			} catch (IOException e) {
				// Auto-generated catch block
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
		com.neuronrobotics.sdk.common.Log.error(
				"Usage: \r\njava -jar BowlerScriptKernel.jar -s <file 1> .. <file n> # This will load one script after the next ");
		com.neuronrobotics.sdk.common.Log.error(
				"java -jar BowlerScriptKernel.jar -p <file 1> .. <file n> # This will load one script then take the list of objects returned and pss them to the next script as its 'args' variable ");
		com.neuronrobotics.sdk.common.Log.error(
				"java -jar BowlerScriptKernel.jar -r <Groovy Jython or Clojure> (Optional)(-s or -p)<file 1> .. <file n> # This will start a shell in the requested langauge and run the files provided. ");
		com.neuronrobotics.sdk.common.Log
				.error("java -jar BowlerScriptKernel.jar -g <Git repo> <Git file> # this will run a file from git");
		if (isKernelMode())
			System.exit(1);
	}

	/**
	 * @param args the command line arguments
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
		long startTime = System.currentTimeMillis();
		loadHistoryLocal();
		startupProcedures(args);
		runArgumentsAfterStartup(args, startTime);
	}

	public static void runArgumentsAfterStartup(String[] args, long startTime)
			throws IOException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException,
			InvalidRemoteException, TransportException, GitAPIException, Exception {
		boolean gitRun = false;
		String gitRepo = null;
		String gitFile = null;
		boolean runCSGServer = false;
		File keys = null;
		int port = 3742;
		for (String s : args) {
			if (gitRun) {
				if (gitRepo == null) {
					gitRepo = s;
				} else if (gitFile == null) {
					gitFile = s;
				}
			}
			if (s.startsWith("-g")) {
				gitRun = true;
			}
			if (s.toLowerCase().contains("-csgserver")) {
				runCSGServer = true;
				continue;
			}
			if (runCSGServer) {
				if (keys == null) {
					keys = new File(s);
					continue;
				} else {
					try {
						port = Integer.parseInt(s);
					}catch(NumberFormatException ex) {
						Log.error(ex);
					}
				}
			}
		}
		if (runCSGServer) {
			CSGServer.setDirectory(ScriptingEngine.getWorkspace());
			CSGServer server = new CSGServer(port, keys);
			try {
				server.start();
			} catch (Exception e) {
				Log.error(e);
			}
			return;
		}

		Object ret = null;
		File baseWorkspaceFile = null;
		if (gitRun && gitRepo != null) {
			String url = null;

			ScriptingEngine.pull(gitRepo);
			ArrayList<String> files = ScriptingEngine.filesInGit(gitRepo);
			boolean fileExists = false;
			if (gitFile == null)
				gitFile = "launch";
			if (gitFile.endsWith("/"))
				gitFile += "launch";
			for (String f : files) {
				if (f.startsWith(gitFile)) {
					gitFile = f;
					fileExists = true;
				}
			}
			if (!fileExists) {
				com.neuronrobotics.sdk.common.Log.error("\n\nERROR file does not exist: " + gitFile);
				gitFile = null;
			}
			if (gitFile != null)
				try {
					ret = ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(),gitRepo, gitFile, null);
					url = gitRepo;
					baseWorkspaceFile = ScriptingEngine.getRepositoryCloneDirectory(url);

					processReturnedObjectsStart(ret, baseWorkspaceFile);
				} catch (Throwable e) {
					Log.error(e);
					fail();
				}
			else {
				com.neuronrobotics.sdk.common.Log.error("Files in git:");
				for (String f : files) {
					com.neuronrobotics.sdk.common.Log.error("\t" + f);
				}
			}
			finish(startTime);
		}
//		File servo = ScriptingEngine.fileFromGit("https://github.com/CommonWealthRobotics/BowlerStudioVitamins.git",
//							"BowlerStudioVitamins/stl/servo/smallservo.stl");
//		
//		ArrayList<CSG>  cad = (ArrayList<CSG> )ScriptingEngine.inlineGistScriptRun("4814b39ee72e9f590757", "javaCad.groovy" , null);
//		com.neuronrobotics.sdk.common.Log.error(servo.exists()+" exists: "+servo);

		boolean startLoadingScripts = false;
		for (String s : args) {
			if (startLoadingScripts) {
				try {

					File f = new File(s);
					String location;
					try {
						location = ScriptingEngine.locateGitTopLevelDirectory(f).getAbsolutePath();
					} catch (Exception ex) {
						location = new File(".").getAbsolutePath();
					}
					if (location.endsWith(".")) {
						location = location.substring(0, location.length() - 1);
					}
					if (!location.endsWith("/")) {
						location += "/";
					}
					baseWorkspaceFile = new File(location);

					com.neuronrobotics.sdk.common.Log.debug("Using working directory  " + baseWorkspaceFile.getAbsolutePath());
					f = new File(baseWorkspaceFile.getAbsolutePath() + "/" + s);
					com.neuronrobotics.sdk.common.Log.error("File   " + f.getName());
					ret = ScriptingEngine.inlineFileScriptRun(CSGDatabase.getInstance(),f, null);
				} catch (Throwable e) {
					Log.error(e);
					fail();
				}
			}
			if (s.startsWith("-f") || s.startsWith("-s")) {
				startLoadingScripts = true;
			}
		}
		if (startLoadingScripts) {
			processReturnedObjectsStart(ret, baseWorkspaceFile);
			startLoadingScripts = false;
			finish(startTime);
			return;
		}

		for (String s : args) {

			if (startLoadingScripts) {
				try {
					ret = ScriptingEngine.inlineFileScriptRun(CSGDatabase.getInstance(),new File(s), (ArrayList<Object>) ret);
				} catch (Throwable e) {
					Log.error(e);
					fail();
				}
			}
			if (s.startsWith("-p")) {
				startLoadingScripts = true;
			}
		}
		if (startLoadingScripts) {
			processReturnedObjectsStart(ret, new File("."));
			finish(startTime);
			return;
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
			if (s.startsWith("-r")) {
				runShell = true;
			}
		}

		if (!runShell) {
			finish(startTime);
		}
		com.neuronrobotics.sdk.common.Log.error("Starting Bowler REPL in langauge: " + shellTypeStorage);
		// sample from
		// http://jline.sourceforge.net/testapidocs/src-html/jline/example/Example.html

		if (!Terminal.getTerminal().isSupported()) {
			com.neuronrobotics.sdk.common.Log.error("Terminal not supported " + Terminal.getTerminal());
		}
		// Terminal.getTerminal().initializeTerminal();

		ConsoleReader reader = new ConsoleReader();
		reader.addTriggeredAction(Terminal.CTRL_C, e -> {
			finish(startTime);
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
			reader.getHistory().addToHistory(
					"ScriptingEngine.gitScriptRun(\\\"https://github.com/OperationSmallKat/greycat.git\\\", \\\"launch.groovy\\\" , null)");
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
						com.neuronrobotics.sdk.common.Log.error(s);
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
					ret = ScriptingEngine.inlineScriptStringRun(CSGDatabase.getInstance(),line, null, shellTypeStorage);
					if (ret != null) {
						com.neuronrobotics.sdk.common.Log.error(ret);
					}
					processReturnedObjectsStart(ret, null);
				} catch (Throwable e) {
					Log.error(e);
				} 
			}
		} catch (Exception e) {
			Log.error(e);
		}
	}

	private static void startupProcedures(String[] args)
			throws IOException, InvalidRemoteException, TransportException, GitAPIException, Exception {
		try {
			JavaFXInitializer.go();
		} catch (Throwable t) {
			t.printStackTrace();
			com.neuronrobotics.sdk.common.Log.error("ERROR No UI engine availible");
		}
		ScriptingEngine.waitForLogin();
		if (args.length == 0) {
			fail();
		}
		CSGDatabase.setInstance(new CSGDatabaseInstance(new File(ScriptingEngine.getWorkspace().getAbsoluteFile() + "/csgDatabase.json")));
		Log.setFile(new File(ScriptingEngine.getWorkspace().getAbsoluteFile() + "/kernelLog.txt"));
		Log.enableDebugPrint();
		ScriptingEngine.gitScriptRun(CSGDatabase.getInstance(),"https://github.com/CommonWealthRobotics/DeviceProviders.git", "loadAll.groovy",
				null);
		CSG.setPreventNonManifoldTriangles(true);
		CSG.setProgressMoniter(new ICSGProgress() {
			@Override
			public void progressUpdate(int currentIndex, int finalIndex, String type,
					eu.mihosoft.vrl.v3d.CSG intermediateShape) {

			}
		});
	}

	private static void finish(long startTime) {
		com.neuronrobotics.sdk.common.Log
				.error("Process took " + (((double) (System.currentTimeMillis() - startTime))) / 60000.0 + " minutes");
		System.exit(0);
	}

	public static void processReturnedObjectsStart(Object ret, File baseWorkspaceFile) {
		CSG.setPreventNonManifoldTriangles(true);
		processUIOpening(ret);
		if (baseWorkspaceFile != null)
			com.neuronrobotics.sdk.common.Log.debug("Processing file in directory " + baseWorkspaceFile.getAbsolutePath());

		if (baseWorkspaceFile != null) {

			File baseDirForFiles = new File(baseWorkspaceFile.getAbsolutePath() + "/manufacturing/");
			if (baseDirForFiles.exists()) {
				// baseDirForFiles.mkdir();
				File bomCSV = new File(
						baseWorkspaceFile.getAbsolutePath() + "/" + VitaminBomManager.getManufacturingBomCsv());
				if (bomCSV.exists()) {

					File file = new File(
							baseWorkspaceFile.getAbsolutePath() + "/" + VitaminBomManager.getManufacturingBomCsv());
//					if (file.exists())
//						file.delete();
					try {
						Files.copy(bomCSV.toPath(), file.toPath());
					} catch (IOException e) {
						// Auto-generated catch block
						Log.error(e);
					}
				}
				File bom = new File(
						baseWorkspaceFile.getAbsolutePath() + "/" + VitaminBomManager.getManufacturingBomJson());
				if (bom.exists()) {
					File file = new File(
							baseWorkspaceFile.getAbsolutePath() + "/" + VitaminBomManager.getManufacturingBomJson());
//					if (file.exists())
//						file.delete();
					try {
						Files.copy(bom.toPath(), file.toPath());
					} catch (IOException e) {
						// Auto-generated catch block
						Log.error(e);
					}
				}
			} else {
				baseDirForFiles.mkdirs();
			}
		}
		ArrayList<CSG> csgBits = new ArrayList<>();
		try {
			processReturnedObjects(ret, csgBits);
			String url = ScriptingEngine.locateGitUrl(baseWorkspaceFile);
			com.neuronrobotics.sdk.common.Log.error("Loading printbed URL  " + url);
			PrintBedManager printBedManager = new PrintBedManager(baseWorkspaceFile, csgBits);
			if (printBedManager.hasPrintBed())
				csgBits = printBedManager.makePrintBeds();
			else {
				com.neuronrobotics.sdk.common.Log.error("Exporting files without print bed");
			}
			new CadFileExporter().generateManufacturingParts(csgBits, baseWorkspaceFile);
		} catch (Throwable t) {
			Log.error(t);
			fail();
		}
	}

	private static void processUIOpening(Object ret) {
		if (Tab.class.isInstance(ret)) {
			com.neuronrobotics.sdk.common.Log.error("Launching User Defined UI");
			Tab t = (Tab) ret;
			CompletableFuture<Boolean> future = new CompletableFuture<>();

			BowlerKernel.runLater(() -> {
				// Get the content from the tab
				javafx.scene.Node content = t.getContent();
				// Create a new stage
				Stage newStage = new Stage();
				// Set the title of the new window to the tab's text
				newStage.setTitle(t.getText());
				AnchorPane root = new AnchorPane();
				root.getChildren().add(content);
				if (content != null) {
					AnchorPane.setTopAnchor(content, 10.0);
					AnchorPane.setRightAnchor(content, 10.0);
					AnchorPane.setBottomAnchor(content, 10.0);
					AnchorPane.setLeftAnchor(content, 10.0);
				}
				// Create a new scene with the parent container
				Scene scene = new Scene(root);
				// Set the scene on the new stage
				newStage.setScene(scene);
				scene.getRoot().setStyle("-fx-font-family: 'Arial';");
				scene.getRoot().applyCss();
				scene.getRoot().layout();
				// Add a close request handler
				newStage.setOnCloseRequest(event -> {
					// Exit the JVM when the window is closed
					future.complete(true);
					BowlerKernel.runLater(() -> Platform.exit());
				});
				FontSizeManager.addListener(fontNum -> {
					int tmp = fontNum - 10;
					if (tmp < 12)
						tmp = 12;
					root.setStyle("-fx-font-size: " + tmp + "pt");
					newStage.sizeToScene();
				});
				if (IStageReceiver.class.isInstance(ret)) {
					com.neuronrobotics.sdk.common.Log.error("UI is a IStageReceiver");
					IStageReceiver r = (IStageReceiver) ret;
					BowlerKernel.runLater(() -> {
						r.receiveStage(newStage, scene);
					});
				}

				// Show the new stage
				newStage.show();
				scene.getRoot().applyCss();
				scene.getRoot().layout();
			});
			try {
				future.get();
				System.exit(0);
			} catch (Exception e) {
				Log.error(e);
			}
		}

	}

	private static void processReturnedObjects(Object ret, ArrayList<CSG> csgBits) {
		if (List.class.isInstance(ret)) {
			List lst = (List) ret;
			for (int i = 0; i < lst.size(); i++)
				processReturnedObjects(lst.get(i), csgBits);
			return;
		}
		if (CSG.class.isInstance(ret)) {
			csgBits.add((CSG) ret);
		}
		if (CaDoodleFile.class.isInstance(ret)) {
			processReturnedObjects(CaDoodleLoader.process((CaDoodleFile) ret,false), csgBits);
			return;
		}
		if (MobileBase.class.isInstance(ret)) {
			MobileBase ret2 = (MobileBase) ret;
			CSGDatabaseInstance db = MobileBaseCadManager.getCSGDatabase(ret2);
			MobileBaseCadManager m = MobileBaseCadManager.get(db, ret2);
			m.setUi(new IMobileBaseUI() {

				@Override
				public void setSelectedCsg(Collection<CSG> selectedCsg) {
					// Auto-generated method stub

				}

				@Override
				public void setSelected(Affine rootListener) {
					// Auto-generated method stub

				}

				@Override
				public void setAllCSG(Collection<CSG> toAdd, File source) {
					// Auto-generated method stub

				}

				@Override
				public void highlightException(File fileEngineRunByName, Throwable ex) {
					Log.error(ex);
					fail();
				}

				@Override
				public Set<CSG> getVisibleCSGs() {
					// Auto-generated method stub
					return null;
				}

				@Override
				public void addCSG(Collection<CSG> toAdd, File source) {
					// Auto-generated method stub

				}
			});
			m.setConfigurationViewerMode(false);
			ret2.connect();
			m.generateBody(db);
			try {

				File baseDir = new File("./manufacturing/");
				File dir = new File(baseDir.getAbsolutePath() + "/" + ret2.getScriptingName());
				if (!dir.exists())
					dir.mkdirs();
				IgenerateBed bed = null;
				try {
					bed = m.getIgenerateBed();
				} catch (Throwable T) {
					throw new RuntimeException(T.getMessage());
				}
				String remoteURI = ret2.getGitSelfSource()[0];
				VitaminBomManager BoM = new VitaminBomManager(remoteURI);
				BoM.loadBaseVitamins(ret2);
				bed = m.getPrintBed(dir, bed, ScriptingEngine.getRepositoryCloneDirectory(remoteURI));
				if (bed == null) {
					m._generateStls(ret2, dir, false);
					return;
				}
				com.neuronrobotics.sdk.common.Log.error("Found arrangeBed API in CAD engine");
				List<CSG> totalAssembly = bed.arrangeBed(ret2);
				ret2.disconnect();
				Thread.sleep(1000);
				System.gc();
				// Get current size of heap in bytes
				long heapSize = Runtime.getRuntime().totalMemory();

				// Get maximum size of heap in bytes. The heap cannot grow beyond this size.//
				// Any attempt will result in an OutOfMemoryException.
				long heapMaxSize = Runtime.getRuntime().maxMemory();
				// com.neuronrobotics.sdk.common.Log.error("Heap remaining
				// "+(heapMaxSize-Runtime.getRuntime().totalMemory()));
				// com.neuronrobotics.sdk.common.Log.error("Of Heap "+(heapMaxSize));
				for (int i = 0; i < totalAssembly.size(); i++) {
					List<CSG> tmp = Arrays.asList(totalAssembly.get(i));
					totalAssembly.set(i, null);
					// com.neuronrobotics.sdk.common.Log.error("Before Heap remaining
					// "+(heapMaxSize-Runtime.getRuntime().totalMemory()));

					new CadFileExporter(m.getUi()).generateManufacturingParts(tmp, dir);
					tmp = null;
					System.gc();
					// com.neuronrobotics.sdk.common.Log.error("After Heap remaining
					// "+(heapMaxSize-Runtime.getRuntime().totalMemory()));

				}
			} catch (Exception e) {
				// Auto-generated catch block
				Log.error(e);
				fail();
			}
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
		com.neuronrobotics.sdk.common.Log.error("Saving history");
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(historyFile);
			BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
			for (String s : history) {
				bw.write(s);
				bw.newLine();
			}

			bw.close();
		} catch (Exception e) {
			// Auto-generated catch block
			Log.error(e);
		}

	}

	public static int speak(String msg, ISpeakingProgress progress) {
		return speak(msg, 200, 0, 100, 1.0, 1.0, progress);
	}

	public static int speak(String msg) {

		return speak(msg, 200, 0, 301, 1.0, 1.0, null);
	}

	@SuppressWarnings("unused")
	public static int speak(String msg, Number rate, Number pitch, Number voice, Number shift, Number volume) {
		return speak(msg, rate, pitch, voice, shift, volume, null);
	}

	@SuppressWarnings("unused")
	public static int speak(String msg, Number rate, Number pitch, Number voiceNumber, Number shift, Number volume,
			ISpeakingProgress progress) {
		if (rate.doubleValue() > 300)
			rate = 300;
		if (rate.doubleValue() < 10)
			rate = 10;
		try {
			if (voiceNumber.doubleValue() >= 800) {
				if (0 == CoquiDockerManager.get(voiceNumber.doubleValue()).speak(msg, 2.0f, false, true, progress)) {
					return 0;
				}
			}
		} catch (Throwable t) {
			Log.error(t);
		}
		TextToSpeech tts = new TextToSpeech();
		// cd ..
		tts.getAvailableVoices().stream().forEach(voice -> com.neuronrobotics.sdk.common.Log.error("Voice: " + voice));
		// Setting the Current Voice
		// voice =(tts.getAvailableVoices().toArray()[0].toString());
		String voice = "dfki-poppy-hsmm";
		if (voiceNumber.doubleValue() > 600)
			voice = ("dfki-prudence-hsmm");
		else if (voiceNumber.doubleValue() > 500)
			voice = ("cmu-rms-hsmm");
		else if (voiceNumber.doubleValue() > 400)
			voice = ("cmu-bdl-hsmm");
		else if (voiceNumber.doubleValue() > 300)
			voice = ("dfki-obadiah-hsmm");
		else if (voiceNumber.doubleValue() > 200)
			voice = ("cmu-slt-hsmm");
		else if (voiceNumber.doubleValue() > 100)
			voice = ("dfki-spike-hsmm");

		tts.setVoice(voice);

		com.neuronrobotics.sdk.common.Log.error("Using voice " + voice);

		RobotiserEffect vocalTractLSE = new RobotiserEffect(); // russian drunk effect
		vocalTractLSE.setParams("amount:" + pitch.intValue());

		// TTS say something that we actually are typing into the first variable
//	tts.getAudioEffects().stream().forEach(audioEffect -> {
//		if(audioEffect.getName().contains("Rate")) {
//		com.neuronrobotics.sdk.common.Log.error("-----Name-----");
//		com.neuronrobotics.sdk.common.Log.error(audioEffect.getName());
//		com.neuronrobotics.sdk.common.Log.error("-----Examples-----");
//		com.neuronrobotics.sdk.common.Log.error(audioEffect.getExampleParameters());
//		com.neuronrobotics.sdk.common.Log.error("-----Help Text------");
//		com.neuronrobotics.sdk.common.Log.error(audioEffect.getHelpText() + "\n\n");
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
		com.neuronrobotics.sdk.common.Log.error(msg + "-->" + effect);
		tts.getMarytts().setAudioEffects(effect);

		tts.speak(msg, 2.0f, false, true, progress);

		return 0;
	}

	public static void upenURL(String string) {

		com.neuronrobotics.sdk.common.Log.error("Opening " + string);
		try {
			upenURL(new URI(string));

		} catch (URISyntaxException e) {
			// Auto-generated catch block
			Log.error(e);
		} catch (Throwable e) {
			// Auto-generated catch block
			Log.error(e);
		}
	}

	public static void upenURL(URI htmlUrl) {
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
			try {
				Desktop.getDesktop().browse(htmlUrl);
			} catch (IOException e) {
				// Auto-generated catch block
				Log.error(e);
			}
	}

	@SuppressWarnings("restriction")
	public static void runLater(Runnable r) {
		if (Platform.isFxApplicationThread())
			try {
				r.run();
			} catch (Throwable t) {
				Log.error(t);
			}
		else
			runLater(r, new Exception("UI Thread Exception here!"));
	}

	@SuppressWarnings("restriction")
	public static void runLater(Runnable r, Throwable ex) {
		if (Platform.isFxApplicationThread())
			try {
				r.run();
			} catch (Throwable t) {
				Log.error(t);
			}
		else
			Platform.runLater(() -> {
				try {
					r.run();
				} catch (Throwable t) {
					t.printStackTrace();
					ex.printStackTrace();
				}

			});
	}

	public static boolean isKernelMode() {
		return kernelMode;
	}

	public static void setKernelMode(boolean kernelMode) {
		BowlerKernel.kernelMode = kernelMode;
	}

}
