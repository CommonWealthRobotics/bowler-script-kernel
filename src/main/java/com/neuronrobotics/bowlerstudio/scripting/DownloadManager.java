package com.neuronrobotics.bowlerstudio.scripting;

import org.apache.commons.exec.*;
import org.apache.commons.exec.environment.EnvironmentUtils;


import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.video.OSUtil;

import eu.mihosoft.vrl.v3d.CSG;
//import javafx.scene.control.Alert;
import javafx.scene.control.Button;
//import javafx.scene.control.ButtonType;
//import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import net.sf.sevenzipjbinding.*;
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


public class DownloadManager {
	private static String STUDIO_INSTALL = "BowlerStudioInstall";
	private static String editorsURL = "https://github.com/CommonWealthRobotics/ExternalEditorsBowlerStudio.git";
	private static String bindir = System.getProperty("user.home") + delim() + "bin" + delim() + getSTUDIO_INSTALL()
			+ delim();
	private static int ev = 0;
	private static String cmd = "";
	private static HashSet<String> failedURLs = new HashSet<String>();
	private static IDownloadManagerEvents downloadEvents = new IDownloadManagerEvents() {

		@Override
		public void startDownload() {
			// Auto-generated method stub

		}

		@Override
		public void finishDownload() {
			// Auto-generated method stub

		}
	};


	/**
	 * Searches for an executable by name.
	 * <p>
	 * All platforms: splits PATH and checks every entry.
	 * macOS only:    also scans /Applications for a *.app whose name
	 *                matches (case-insensitive) and returns the binary
	 *                inside its Contents/MacOS directory.
	 *
	 * @param executableName bare name, e.g. "inkscape", "blender"
	 * @return resolved absolute Path, or empty if not found
	 */
	public static Optional<Path> findExecutable(String executableName) {
		if (executableName.toLowerCase().contains("java")) {
			Optional<Path> fromJavaHome = searchJavaHome(executableName);
			if (fromJavaHome.isPresent())
				return fromJavaHome;
		}
		// 1. Search PATH (works on all three platforms)
		Optional<Path> fromPath = searchPath(executableName);
		if (fromPath.isPresent())
			return fromPath;

		// 2. macOS fallback: scan /Applications for a matching .app bundle
		if (isMac()) {
			return searchApplicationsDir(executableName);
		}

		return Optional.empty();
	}

	// -------------------------------------------------------------------------
	// PATH search
	// -------------------------------------------------------------------------

	/// -------------------------------------------------------------------------

	/**
	 * Resolves JAVA_HOME from the environment and looks for the executable
	 * in its bin/ sub-directory.
	 *
	 * JAVA_HOME typically points to the JDK/JRE root, e.g.:
	 *   /usr/lib/jvm/java-21-openjdk-amd64   (Linux)
	 *   /Library/Java/JavaVirtualMachines/…/Contents/Home  (macOS)
	 *   C:\Program Files\Eclipse Adoptium\jdk-21…          (Windows)
	 *
	 * The executable lives one level deeper in bin/.
	 */
	private static Optional<Path> searchJavaHome(String executableName) {
		String javaHome = System.getenv("JAVA_HOME");
		if (javaHome == null || javaHome.isBlank())
			return Optional.empty();

		Path javaHomePath = Paths.get(javaHome.trim());
		if (!Files.isDirectory(javaHomePath))
			return Optional.empty();

		Path binDir = javaHomePath.resolve("bin");
		if (!Files.isDirectory(binDir))
			return Optional.empty();

		for (String candidate : candidateNames(executableName)) {
			Path resolved = binDir.resolve(candidate);
			if (isExecutable(resolved)) {
				return Optional.of(resolved.toAbsolutePath());
			}
		}

		return Optional.empty();
	}

	private static Optional<Path> searchPath(String executableName) {
		String pathEnv = System.getenv("PATH");
		if (pathEnv == null)
			return Optional.empty();

		List<String> candidates = candidateNames(executableName);

		for (String entry : pathEnv.split(File.pathSeparator)) {
			Path dir = Paths.get(entry.trim());
			if (!Files.isDirectory(dir))
				continue;
			for (String candidate : candidates) {
				Path resolved = dir.resolve(candidate);
				if (isExecutable(resolved)) {
					return Optional.of(resolved.toAbsolutePath());
				}
			}
		}

		return Optional.empty();
	}

	/**
	 * On Windows, also try .exe / .cmd / .bat suffixes.
	 * On Unix the bare name is the only candidate.
	 */
	private static List<String> candidateNames(String name) {
		if (isWindows()) {
			return Arrays.asList(name, name + ".exe", name + ".cmd", name + ".bat");
		}
		ArrayList<String> of = new ArrayList();
		of.add(name);
		if (name.contains("openscad")) {
			of.add(name + "-nightly");
		}
		if (name.contains("java"))
			of.add("java");

		return of;
	}

	// -------------------------------------------------------------------------
	// macOS /Applications scan
	// -------------------------------------------------------------------------

	/**
	 * Walks /Applications looking for a directory whose name, stripped of the
	 * ".app" suffix and lower-cased, equals the lower-cased executable name.
	 *
	 * Matching .app found  →  returns Contents/MacOS/<executableName>
	 *                         (or the first executable in Contents/MacOS
	 *                          if the exact name isn't there).
	 */
	private static Optional<Path> searchApplicationsDir(String executableName) {
		Path appsDir = Paths.get("/Applications");
		if (!Files.isDirectory(appsDir))
			return Optional.empty();

		String needle = executableName.toLowerCase();

		File[] bundles = appsDir.toFile().listFiles(f -> f.isDirectory() && f.getName().endsWith(".app"));
		if (bundles == null)
			return Optional.empty();

		for (File bundle : bundles) {
			// "Inkscape.app" → "inkscape"
			String appBaseName = bundle.getName().substring(0, bundle.getName().length() - 4) // strip ".app"
					.toLowerCase();

			if (!appBaseName.equals(needle))
				continue;

			// Prefer an exact-name match inside Contents/MacOS
			Path macOS = bundle.toPath().resolve("Contents/MacOS");
			if (!Files.isDirectory(macOS))
				continue;

			Path exact = macOS.resolve(executableName);
			if (isExecutable(exact))
				return Optional.of(exact.toAbsolutePath());

			// Fall back to the first executable found in Contents/MacOS
			File[] execs = macOS.toFile().listFiles(f -> f.isFile() && f.canExecute());
			if (execs != null && execs.length > 0) {
				return Optional.of(execs[0].toPath().toAbsolutePath());
			}
		}

		return Optional.empty();
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private static boolean isExecutable(Path p) {
		return Files.isRegularFile(p) && Files.isExecutable(p);
	}

	private static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}


	// -------------------------------------------------------------------------
	// Quick smoke-test
	// -------------------------------------------------------------------------

	public static void main(String[] args) {
		for (String name : new String[]{"inkscape", "blender", "freecad", "openscad"}) {
			Optional<Path> result = findExecutable(name);
			System.out.printf("%-10s → %s%n", name, result.map(Path::toString).orElse("not found"));
		}
	}


	public static String sanitizeString(String s) {
		if (s.contains(" "))
			s = s.replace(' ', '_');
		return s;
	}

	public static File getTmpSTL(CSG stlIn) throws IOException {
		String name = stlIn.getName();
		if (name.length() == 0)
			name = "CSG_EXPORT";
		Path tempDir = Paths.get(ScriptingEngine.getWorkspace().getAbsolutePath(), "tmp");
		Files.createDirectories(tempDir);
		File stl = File.createTempFile(name, ".stl", tempDir.toFile());
		stl.deleteOnExit();
		stlIn.toStl(Paths.get(stl.getAbsolutePath()));
		return stl;
	}
	private static IApprovalForDownload approval = new IApprovalForDownload() {

		@Override
		public boolean get(String name, String url) {
			com.neuronrobotics.sdk.common.Log
					.debug("Command line mode, assuming yes to downloading \n" + name + " \nfrom \n" + url);
			return true;
		}

		@Override
		public void onInstallFail(String url) {
			com.neuronrobotics.sdk.common.Log.error("Plugin needs to be installed from " + url);
		}

		public void notifyOfFailure(String name) {
			com.neuronrobotics.sdk.common.Log.error("Plugin failed " + name);
		}
	};
	private static GitLogProgressMonitor psudoSplash = new GitLogProgressMonitor() {

		@Override
		public void onLogUpdate(String update, Exception e) {
			// Auto-generated method stub

		}

	};
	private static String jvmURL;

	public static Thread run(IExternalEditor editor, File dir, PrintStream out, List<String> finalCommand) {
		return run(new HashMap<String, String>(), editor, dir, out, finalCommand);
	}

	public static Thread run(Map<String, String> envincoming, IExternalEditor editor, File dir, PrintStream out,
			List<String> finalCommand) {
		if (dir == null) {
			throw new NullPointerException("Parent directory can not be mull");
		}
		Thread thread = new Thread(() -> {

			try {
				if (isMac()) {
					legacySystemRun(envincoming, dir, out, finalCommand);
				} else {
					advancedSystemRun(envincoming, dir, out, finalCommand);
				}

				if (editor != null)
					editor.onProcessExit(ev);

			} catch (Throwable e) {
				e.printStackTrace(out);
			}
		});
		thread.start();
		return thread;
	}

	public static void legacySystemRun(Map<String, String> envincoming, File dir, PrintStream out,
			List<String> finalCommand) throws IOException, InterruptedException {
		cmd = "";
		for (String s : finalCommand) {
			cmd += sanitize(s) + " ";
		}
		ProcessBuilder pb = new ProcessBuilder(finalCommand);
		Map<String, String> envir = pb.environment();
		// set environment variable u
		if (envincoming != null) {
			envir.putAll(envincoming);
			for (String s : envincoming.keySet()) {
				com.neuronrobotics.sdk.common.Log.debug("Environment var set: " + s + " to " + envir.get(s));
			}
		}
		// setting the directory
		pb.directory(dir);
		// startinf the process
		out.println("Running command:\n");
		out.println(cmd);

		out.println("\nIn " + dir.getAbsolutePath());
		out.println("\n\n");

		Process process = pb.start();

		// for reading the ouput from stream
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
		BufferedReader errInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));

		String s = null;
		String e = null;
		Thread.sleep(100);
		while ((s = stdInput.readLine()) != null || (e = errInput.readLine()) != null) {
			if (s != null)
				out.println(s);
			if (e != null)
				out.println(e);
			//
		}
		process.waitFor();
		int ev = process.exitValue();
		// out.println("Running "+commands);
		if (ev != 0) {
			com.neuronrobotics.sdk.common.Log.error("ERROR PROCESS Process exited with " + ev);
		}
		while (process.isAlive()) {
			Thread.sleep(100);
		}
		out.println("");
	}

	public static void advancedSystemRun(Map<String, String> envincoming, File dir, PrintStream out,
			List<String> finalCommand) throws IOException, ExecuteException {
		CommandLine cmdLine;
		cmdLine = new CommandLine(sanitize(finalCommand.get(0)));
		cmd = cmdLine.getExecutable();

		// Add arguments
		for (int i = 1; i < finalCommand.size(); i++) {
			String san = sanitize(finalCommand.get(i));
			cmd += " " + san;
			cmdLine.addArgument(san, false);
		}
		out.println("Running command:\n");
		out.println(cmd);

		out.println("\nIn " + dir.getAbsolutePath());
		out.println("\n\n");

		DefaultExecutor executor = new DefaultExecutor();
		executor.setWorkingDirectory(dir);
		Map<String, String> env = EnvironmentUtils.getProcEnvironment();
		if (envincoming != null)
			env.putAll(envincoming);

		PipedOutputStream outPipe = new PipedOutputStream();
		PipedInputStream outPipeIn = new PipedInputStream(outPipe);
		PipedOutputStream errPipe = new PipedOutputStream();
		PipedInputStream errPipeIn = new PipedInputStream(errPipe);

		PumpStreamHandler streamHandler = new PumpStreamHandler(outPipe, errPipe);
		executor.setStreamHandler(streamHandler);
		startOutputReader(outPipeIn, "OUTPUT", out);
		startOutputReader(errPipeIn, "ERROR", out);
		ev = executor.execute(cmdLine, env);
		out.println("");
	}

	private static String sanitize(String s) {

		String string = s;
		if (s.contains(" ")) {
			if (!s.contains("\""))
				string = "\"" + s + "\"";
		}
		return string;
	}

	private static void startOutputReader(final InputStream is, final String type, PrintStream out) {
		new Thread(() -> {
			try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
				String line;
				while ((line = br.readLine()) != null) {
					out.println(line);
				}
			} catch (IOException e) {
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		}).start();
	}

	@SuppressWarnings("unchecked")
	public static Map<String, String> getEnvironment(String exeType) {
		String key = discoverKey();

		try {
			for (String f : ScriptingEngine.filesInGit(editorsURL)) {
				File file = ScriptingEngine.fileFromGit(editorsURL, f);
				if (file.getName().toLowerCase().startsWith(exeType.toLowerCase())
						&& file.getName().toLowerCase().endsWith(".json")) {
					String jsonText = new String(Files.readAllBytes(file.toPath()));
					Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
					}.getType();
					Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
					HashMap<String, Object> database = gson.fromJson(jsonText, TT_mapStringString);
					Map<String, Object> vm = (Map<String, Object>) database.get(key);
					if (vm != null) {
						String baseURL = vm.get("url").toString();
						String type = vm.get("type").toString();
						String name = vm.get("name").toString();
						String exeInZip = vm.get("executable").toString();
						String jvmURL = baseURL + name + "." + type;
						Map<String, String> environment;
						Object o = vm.get("environment");
						if (o != null) {
							com.neuronrobotics.sdk.common.Log.debug("Environment found for " + exeType + " on " + key);

							return (Map<String, String>) o;
						}
					}
				}
			}
		} catch (Throwable t) {
			com.neuronrobotics.sdk.common.Log.error(t);

		}
		return new HashMap<>();
	}

	public static File getRunExecutable(String exeType, IExternalEditor editor) {
		return getRunExecutable(exeType, editor, false);
	}

	public static File getRunExecutable(String exeType, IExternalEditor editor, boolean justChecking) {
		String executable = "executable";
		retryLoop(exeType, editor, executable, justChecking);
		return getExecutable(exeType, editor, executable, justChecking);
	}

	public static File getConfigExecutable(String exeType, IExternalEditor editor) {
		String executable = "configExecutable";
		retryLoop(exeType, editor, executable, false);
		return getExecutable(exeType, editor, executable, false);
	}

	private static void retryLoop(String exeType, IExternalEditor editor, String executable, boolean justChecking) {
		if (justChecking)
			return;
		for (int i = 0; i < 3; i++) {
			if (getExecutable(exeType, editor, executable, justChecking).exists()) {
				return;
			}
			com.neuronrobotics.sdk.common.Log.error(new RuntimeException("Download or extraction failed, retrying"));
		}
		if (!failedURLs.contains(jvmURL)) {
			failedURLs.add(jvmURL);
			approval.notifyOfFailure(exeType);
			approval.onInstallFail(jvmURL);
		}
	}

	public static File getDestinationDir(String exeType) {
		return new File(bindir + exeType);
	}

	private static File getExecutable(String exeType, IExternalEditor editor, String executable, boolean justChecking) {
		String key = discoverKey();
		if (exeType.toLowerCase().contains("java")) {
			Optional<Path> fromJavaHome = searchJavaHome(exeType);
			if (fromJavaHome.isPresent())
				return fromJavaHome.get().toFile();
		}
		ArrayList<String> filesInGit = null;;
		try {
			filesInGit = ScriptingEngine.filesInGit(editorsURL);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		try {
			for (String f : filesInGit) {
				File file = ScriptingEngine.fileFromGit(editorsURL, f);
				//Log.debug("Looking at json file " + file.getAbsolutePath());
				if (file.getName().toLowerCase().startsWith(exeType.toLowerCase())
						&& file.getName().toLowerCase().endsWith(".json")) {
					String jsonText = new String(Files.readAllBytes(file.toPath()));
					Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
					}.getType();
					Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
					HashMap<String, Object> database = gson.fromJson(jsonText, TT_mapStringString);
					Map<String, Object> vm = (Map<String, Object>) database.get(key);
					if (vm != null) {
						String targetdir = exeType;
						com.neuronrobotics.sdk.common.Log.debug("Configuration found for " + exeType + " on " + key);
						String baseURL = vm.get("url").toString();
						String type = vm.get("type").toString();
						String name = vm.get("name").toString();
						String ospath = null;
						try {
							ospath = vm.get("ospath").toString();
						} catch (Throwable t) {
						}
						String exeInZip = vm.get(executable).toString();
						String configexe = vm.get("configExecutable").toString();
						jvmURL = baseURL + name + "." + type;
						Map<String, String> environment;
						Object o = vm.get("environment");
						if (o != null) {
							environment = (Map<String, String>) o;
						} else
							environment = new HashMap<>();
						File dest = new File(bindir + targetdir);
						String cmd = bindir + targetdir + delim() + exeInZip;
						if (ospath != null) {
							String string = ospath + delim() + exeInZip;
							if (new File(string).exists())
								cmd = string;
						}

						Object object = vm.get("version");
						String version = null;
						if (object != null)
							version = object.toString();
						boolean toDelete = false;
						File versionFile = new File(bindir + targetdir + delim() + "version-cadoodle.txt");
						if (version != null) {
							if (!versionFile.exists()) {
								toDelete = true;
							} else {
								String curVer = Files.readString(Paths.get(versionFile.getAbsolutePath()));
								if (!curVer.contentEquals(version)) {
									toDelete = true;
								}
							}
							if (toDelete) {
								Log.debug("Deleting cached toolchain for version");
								File directoryToBeDeleted = new File(bindir + targetdir + delim());
								deleteDirectory(directoryToBeDeleted);
								directoryToBeDeleted.mkdirs();
							}
						}
						boolean b = !new File(cmd).exists();
						Optional<Path> onDisk = findExecutable(exeType);
						if (onDisk.isPresent() && b)
							return onDisk.get().toFile();
						if (b && !justChecking) {


							if (exeType.toLowerCase().contentEquals("freecad")) {
								// FreecadLoader.update(vm);
								baseURL = vm.get("url").toString();
								name = vm.get("name").toString();
								exeInZip = vm.get(executable).toString();
								configexe = vm.get("configExecutable").toString();
								jvmURL = baseURL + name + "." + type;
								o = vm.get("environment");
								if (o != null) {
									environment = (Map<String, String>) o;
								} else
									environment = new HashMap<>();
								dest = new File(bindir + targetdir);
								cmd = bindir + targetdir + "/" + exeInZip;
								saveFile(file, gson.toJson(database));
							}

							File jvmArchive = download("", jvmURL, 800000000, bindir, name + "." + type, exeType);

							if (dest.exists()) {
								com.neuronrobotics.sdk.common.Log.error("Erasing stale dir " + dest.getAbsolutePath());
								deleteDirectory(dest);
							}
							if (type.toLowerCase().contains("zip")) {
								unzip(jvmArchive, bindir + targetdir);
							}
							if (type.toLowerCase().contains("tar.gz")) {
								untar(jvmArchive, bindir + targetdir);
							}
							// extractTarXz
							if (type.toLowerCase().contains("tar.xz")) {
								extractTarXz(jvmArchive.getAbsolutePath(), bindir + targetdir);
							}
							if (type.toLowerCase().contains("dmg")) {
								dmgExtract(jvmArchive, bindir + targetdir, exeInZip);
							}
							if (type.toLowerCase().contains("appimage") || type.toLowerCase().contains("exe")
									|| type.toLowerCase().contains("msi") || type.toLowerCase().contains("jar")) {
								standaloneEXE(type, name, targetdir, cmd);
							}
							// extract7zArchive
							if (type.toLowerCase().contains("7z")) {
								if (isWin() && !exeType.contentEquals("sevenzip")) {
									extract7zSystemCall(jvmArchive.getAbsolutePath(), bindir + targetdir);
								} else
									extract7zArchive(jvmArchive.getAbsolutePath(), bindir + targetdir);
							}
							Object installer = vm.get("installer");
							if (installer != null) {
								runInstaller((List<String>) installer);
							}
							Object setup = vm.get("setup");
							if (setup != null) {
								String setupScript = setup.toString();
								File setupEXE = new File(
										getDestinationDir(exeType).getAbsolutePath() + delim() + setupScript);
								runInstaller(setupEXE, exeType);
							}

							Object configurations = database.get("Meta-Configuration");
							if (configurations != null) {
								List<String> configs = (List<String>) configurations;
								com.neuronrobotics.sdk.common.Log.error("Got Configurations " + configs.size());
								ev = -1;
								IExternalEditor errorcheckerEditor = new IExternalEditor() {

									@Override
									public void onProcessExit(int e) {
										ev = e;
										// Auto-generated method stub

									}

									@Override
									public String nameOfEditor() {
										// Auto-generated method stub
										return null;
									}

									@Override
									public void launch(File file, Button advanced, Runnable r) {
										// Auto-generated method stub

									}

									@Override
									public Class getSupportedLangauge() {
										// Auto-generated method stub
										return null;
									}

									@Override
									public URL getInstallURL() throws MalformedURLException {
										// Auto-generated method stub
										return null;
									}

									@Override
									public Image getImage() {
										// Auto-generated method stub
										return null;
									}
								};
								for (int i = 0; i < configs.size(); i++) {
									com.neuronrobotics.sdk.common.Log.error("Running " + exeType + " Configuration "
											+ (i + 1) + " of " + configs.size());
									ArrayList<String> toRun = new ArrayList<>();
									toRun.add(bindir + targetdir + "/" + configexe);
									String[] conf = configs.get(i).split(" ");
									for (int j = 0; j < conf.length; j++) {
										toRun.add(conf[j]);
									}
									// = +" "+configs.get(i);

									// com.neuronrobotics.sdk.common.Log.error(toRun);

									Thread thread = run(errorcheckerEditor, new File(bindir), System.out, toRun);
									thread.join();
									if (ev != 0) {
										throw new RuntimeException(
												"Configuration failed for OS: " + key + " has no entry for " + exeType);
									}
								}

							}

						} else {
							com.neuronrobotics.sdk.common.Log.debug("Not extraction, Application exists " + cmd);
						}
						if (version != null)
							Files.writeString(Paths.get(versionFile.getAbsolutePath()), version);
						return new File(cmd);
					}
				}
			}
		} catch (Exception e) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}

		throw new RuntimeException("Executable for OS: " + key + " has no entry for " + exeType + " from " + filesInGit
				+ " in " + editorsURL);
	}

	private static void saveFile(File file, String json) {
		try {
			FileUtils.writeStringToFile(file, json, Charset.forName("UTF-8"));
		} catch (IOException e) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
	}

	private static void runInstaller(List<String> installerList) {
		for (String installer : installerList) {
			File installerFile = getRunExecutable(installer, null);
			runInstaller(installerFile, installer);
		}
	}

	private static void runInstaller(File installerFile, String installer) {

		if (installerFile.getAbsolutePath().toLowerCase().endsWith("msi")) {
			List<String> command = new ArrayList<>();
			command.add("msiexec.exe");
			command.add("/i"); // Install
			command.add(installerFile.getAbsolutePath());
			command.add("/qn"); // Quiet mode, no UI

			Thread tcopy = run(null, new File("."), System.out, command);
			try {
				tcopy.join();
			} catch (InterruptedException e) {
				// Auto-generated catch block
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		} else if (installerFile.getAbsolutePath().toLowerCase().endsWith("sh")) {
			Thread tcopy = run(null, getDestinationDir(installer), System.out,
					Arrays.asList("bash", installerFile.getAbsolutePath()));
			try {
				tcopy.join();
			} catch (InterruptedException e) {
				// Auto-generated catch block
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		} else {
			Thread tcopy = run(null, getDestinationDir(installer), System.out,
					Arrays.asList(installerFile.getAbsolutePath()));
			try {
				tcopy.join();
			} catch (InterruptedException e) {
				// Auto-generated catch block
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		}

	}

	private static boolean deleteDirectory(File directoryToBeDeleted) {
		File[] allContents = directoryToBeDeleted.listFiles();
		if (allContents != null) {
			for (File file : allContents) {
				deleteDirectory(file);
			}
		}
		return directoryToBeDeleted.delete();
	}

	private static void standaloneEXE(String type, String name, String targetdir, String cmd)
			throws InterruptedException {
		File dir = new File(bindir + targetdir);
		if (!dir.exists())
			dir.mkdirs();
		try {
			Files.move(Paths.get(bindir + name + "." + type), Paths.get(cmd), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		new File(cmd).setExecutable(true);
	}

	private static void dmgExtract(File jvmArchive, String string, String appDir) {
		// since DMG is Mac only, and Mac always has the command line extractors, we
		// will use those
		File location = new File(string);

		File[] listFiles = new File("/Volumes/").listFiles();
		Set<String> before = Stream.of(listFiles).filter(file -> file.isDirectory()).map(File::getName)
				.collect(Collectors.toSet());
		Thread t = run(null, new File("."), System.out,
				Arrays.asList("hdiutil", "attach", "-verbose", jvmArchive.getAbsolutePath()));
		try {
			t.join();
			Thread.sleep(2000);// wait for mount to settle
			File[] listFilesAfter = new File("/Volumes/").listFiles();
			Set<String> after = Stream.of(listFilesAfter).filter(file -> file.isDirectory()).map(File::getName)
					.collect(Collectors.toSet());
			after.removeAll(before);
			Object[] array = after.toArray();
			String newMount = (String) array[0];
			com.neuronrobotics.sdk.common.Log
					.debug("Extracted " + jvmArchive.getAbsolutePath() + " is mounted at " + newMount);
			// asr restore --source "$MOUNT_POINT" --target "$DEST_PATH" --erase --noprompt
			if (!location.exists()) {
				location.mkdirs();
			}
			Thread tcopy = run(null, new File("."), System.out,
					Arrays.asList("rsync", "-avtP", "/Volumes/" + newMount + "/" + appDir, string + "/"));
			tcopy.join();

			Thread tdetach = run(null, new File("."), System.out,
					Arrays.asList("hdiutil", "detach", "/Volumes/" + newMount));
			tdetach.join();
		} catch (Exception e) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
			return;
		} // wait for the mount to finish

		com.neuronrobotics.sdk.common.Log.error("Extracted " + jvmArchive.getAbsolutePath());

	}

	public static boolean isExecutable(ZipArchiveEntry entry) {
		int unixMode = entry.getUnixMode();
		// Check if any of the executable bits are set for user, group, or others.
		// User executable: 0100 (0x40), Group executable: 0010 (0x10), Others
		// executable: 0001 (0x01)
		return (unixMode & 0x49) != 0;
	}

	private static void extract7zSystemCall(String archivePath, String outputPath) {
		File outputDir = new File(outputPath);
		if (outputDir.exists()) {
			com.neuronrobotics.sdk.common.Log.error("Deleting partial extraction, using system 7z");
			deleteDirectory(outputDir);
		}
		outputDir.mkdirs();

		File EXE = getRunExecutable("sevenzip", null);
		List<String> args = Arrays.asList(EXE.getAbsolutePath(), "x", // Extract with full paths
				archivePath, // Path to the .7z file
				"-o" + outputPath, // Output directory
				"-y", // Assume Yes on all queries
				"-bsp1"

		);
		try {
			legacySystemRun(null, outputDir, System.out, args);
		} catch (IOException e) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		} catch (InterruptedException e) {
			// Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
	}

	public static void extract7zArchive(String archivePath, String outputPath) {
		try (RandomAccessFile randomAccessFile = new RandomAccessFile(archivePath, "r");
				IInArchive inArchive = SevenZip.openInArchive(null, new RandomAccessFileInStream(randomAccessFile))) {

			com.neuronrobotics.sdk.common.Log.debug("Archive size: " + randomAccessFile.length() + " bytes");
			com.neuronrobotics.sdk.common.Log.debug("Items in archive: " + inArchive.getNumberOfItems());

			for (int i = 0; i < inArchive.getNumberOfItems(); i++) {
				Boolean isFolder = (Boolean) inArchive.getProperty(i, PropID.IS_FOLDER);
				if (isFolder == null || !isFolder) {
					extractItem(inArchive, i, outputPath);
				}
			}

			com.neuronrobotics.sdk.common.Log.debug("Extraction completed successfully.");

		} catch (Exception e) {
			com.neuronrobotics.sdk.common.Log.error("Error extracting archive: " + e.getMessage());
			com.neuronrobotics.sdk.common.Log.error(e);
		}
	}

	private static void extractItem(IInArchive inArchive, int index, String outputPath)
			throws SevenZipException, IOException {
		String path = inArchive.getStringProperty(index, PropID.PATH);
		Long size = (Long) inArchive.getProperty(index, PropID.SIZE);

		File outputFile = new File(outputPath, path);
		File parentDir = outputFile.getParentFile();
		if (!parentDir.exists()) {
			parentDir.mkdirs();
		}

		ExtractOperationResult result;
		downloadEvents.startDownload();
		try (FileOutputStream fos = new FileOutputStream(outputFile)) {
			result = inArchive.extractSlow(index, new ISequentialOutStream() {
				public int write(byte[] data) throws SevenZipException {
					try {
						psudoSplash.onLogUpdate("Inflate 7z .. " + outputFile.getName(), null);
						fos.write(data);
					} catch (IOException e) {
						throw new SevenZipException("Error writing to file: " + e.getMessage());
					}
					return data.length;
				}
			});
		}
		downloadEvents.finishDownload();
		if (result == ExtractOperationResult.OK) {
			com.neuronrobotics.sdk.common.Log.debug("Extracted: " + path);
		} else {
			com.neuronrobotics.sdk.common.Log.error("Error extracting " + path + ": " + result);
		}
	}

	/*
	 * public static void extract7zArchive(String archivePath, String outputPath) {
	 *
	 *
	 * File archiveFile = new File(archivePath); File outputDir = new
	 * File(outputPath);
	 *
	 * if (!outputDir.exists()) { outputDir.mkdirs(); }
	 *
	 * try (SevenZFile sevenZFile = new
	 * SevenZFile.Builder().setFile(archiveFile).get()) { SevenZArchiveEntry entry;
	 * while ((entry = sevenZFile.getNextEntry()) != null) { if
	 * (entry.isDirectory()) { continue; } File outputFile = new File(outputDir,
	 * entry.getName()); File parent = outputFile.getParentFile(); if
	 * (!parent.exists()) { parent.mkdirs(); }
	 * com.neuronrobotics.sdk.common.Log.error("Inflating 7z "+outputFile.
	 * getAbsolutePath()); try (FileOutputStream out = new
	 * FileOutputStream(outputFile)) { byte[] content = new byte[(int)
	 * entry.getSize()]; sevenZFile.read(content, 0, content.length);
	 * out.write(content); } }
	 * com.neuronrobotics.sdk.common.Log.error("Extraction completed successfully."
	 * ); } catch (IOException e) { e.printStackTrace(System.out); } }
	 *
	 * }
	 */
	public static void unzip(File path, String dir) throws Exception {
		com.neuronrobotics.sdk.common.Log.debug("Unzipping " + path.getName() + " into " + dir);
		Path destFolderPath = new File(dir).toPath();

		try (ZipFile zipFile = ZipFile.builder().setFile(path).get()) {
			Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry entry = entries.nextElement();
				Path entryPath = destFolderPath.resolve(entry.getName());
				if (entryPath.normalize().startsWith(destFolderPath.normalize())) {
					if (entry.isDirectory()) {
						Files.createDirectories(entryPath);
					} else {
						Files.createDirectories(entryPath.getParent());

						// Check timestamps before extracting
						File file = entryPath.toFile();
						file.getParentFile().mkdirs();
						File targetFile = file;
						boolean shouldExtract = false;

						if (!targetFile.exists()) {
							// File doesn't exist, extract it
							shouldExtract = true;
							Log.info("Adding new file: " + entryPath);
						} else {
							// File exists, compare timestamps
							long zipTime = entry.getTime();
							long diskTime = targetFile.lastModified();

							if (zipTime > diskTime) {
								// Zip file is newer, extract it
								shouldExtract = true;
								// Log.debug("Updating file (zip is newer): " + entryPath);
							} else {
								// Disk file is newer or same, skip extraction
								// Log.debug("Skipping file (disk is newer or same): " + entryPath);
							}
						}

						if (shouldExtract) {
							try (InputStream in = zipFile.getInputStream(entry)) {
								try {
									// ar.setExternalAttributes(entry.extraAttributes);
									if (entry.isUnixSymlink()) {
										String text = new BufferedReader(
												new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
												.collect(Collectors.joining("\n"));
										Path target = Paths.get(".", text);
										com.neuronrobotics.sdk.common.Log
												.info("Creating symlink " + entryPath + " with " + target);

										Files.createSymbolicLink(entryPath, target);
										continue;
									}
								} catch (Exception ex) {
									com.neuronrobotics.sdk.common.Log.error(ex);
								}
								try (OutputStream out = new FileOutputStream(file)) {
									IOUtils.copy(in, out);
									// com.neuronrobotics.sdk.common.Log.debug("Inflating " + entryPath);
								} catch (Exception ex) {
									// Log.error(ex);
								}
								if (isExecutable(entry)) {
									file.setExecutable(true);
								}
							}
						}
					}
				}
			}
		}
	}

	private static boolean isPosixCompliantSystem() {
		return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
	}

	private static Set<PosixFilePermission> getPosixPermissions(int mode) {
		StringBuilder permissions = new StringBuilder("rwxrwxrwx");
		for (int i = 0; i < 9; i++) {
			if ((mode & (1 << (8 - i))) == 0) {
				permissions.setCharAt(i, '-');
			}
		}
		return java.nio.file.attribute.PosixFilePermissions.fromString(permissions.toString());
	}

	public static void extractTarXz(String inputFile, String outputDir) throws IOException {
		Path outDir = Paths.get(outputDir);
		if (!Files.exists(outDir)) {
			Files.createDirectories(outDir);
		}

		try {
			FileInputStream fis = new FileInputStream(inputFile);
			XZCompressorInputStream xzIn = new XZCompressorInputStream(fis);
			TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn);
			TarArchiveEntry entry;
			downloadEvents.startDownload();
			while ((entry = tarIn.getNextEntry()) != null) {
				Path outPath = outDir.resolve(entry.getName());

				if (entry.isSymbolicLink()) {
					Path target = Paths.get(entry.getLinkName());
					try {
						Files.createSymbolicLink(outPath, target);
					} catch (IOException | UnsupportedOperationException e) {
						com.neuronrobotics.sdk.common.Log
								.error("Failed to create symlink " + outPath + ". Copying target instead.");
						// Fallback: copy the target file instead
						Path resolvedTarget = outPath.getParent().resolve(target).normalize();
						if (Files.exists(resolvedTarget)) {
							Files.copy(resolvedTarget, outPath);
						} else {
							com.neuronrobotics.sdk.common.Log.error("Symlink target does not exist: " + resolvedTarget);
						}
					}
				} else if (entry.isDirectory()) {
					Files.createDirectories(outPath);
				} else {
					Files.createDirectories(outPath.getParent());
					try (OutputStream out = Files.newOutputStream(outPath)) {
						byte[] buffer = new byte[1024];
						int len;
						psudoSplash.onLogUpdate("Inflate Tar XZ " + outPath.getFileName(), null);
						while ((len = tarIn.read(buffer)) != -1) {
							out.write(buffer, 0, len);
						}
						if (isPosixCompliantSystem()) {
							Set<PosixFilePermission> permissions = getPosixPermissions(entry.getMode());
							Files.setPosixFilePermissions(outPath, permissions);
						} else {
							// For non-POSIX systems (e.g., Windows)
							outPath.toFile().setExecutable((entry.getMode() & 0100) != 0);
						}
					}
				}
			}
		} catch (Throwable ex) {
			downloadEvents.finishDownload();
			com.neuronrobotics.sdk.common.Log.error(ex);;
			new File(inputFile).delete();
			throw ex;
		}
		downloadEvents.finishDownload();
	}

	public static void untar(File tarFile, String dir) throws Exception {
		com.neuronrobotics.sdk.common.Log.debug("Untaring " + tarFile.getName() + " into " + dir);

		File dest = new File(dir);
		dest.mkdir();
		TarArchiveInputStream tarIn = null;
		try {
			tarIn = new TarArchiveInputStream(
					new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(tarFile))));
		} catch (java.io.IOException ex) {
			tarFile.delete();
			return;
		}
		TarArchiveEntry tarEntry = tarIn.getNextTarEntry();
		// tarIn is a TarArchiveInputStream
		while (tarEntry != null) {// create a file with the same name as the tarEntry
			File destPath = new File(dest.toString() + System.getProperty("file.separator") + tarEntry.getName());
			com.neuronrobotics.sdk.common.Log.debug("Inflating: " + destPath.getCanonicalPath());
			if (tarEntry.isDirectory()) {
				destPath.mkdirs();
			} else {
				destPath.createNewFile();
				FileOutputStream fout = new FileOutputStream(destPath);
				byte[] b = new byte[(int) tarEntry.getSize()];
				tarIn.read(b);
				fout.write(b);
				fout.close();
				int mode = tarEntry.getMode();
				b = new byte[5];
				TarUtils.formatUnsignedOctalString(mode, b, 0, 4);
				if (bits(b[1]).endsWith("1")) {
					destPath.setExecutable(true);
				}
			}
			tarEntry = tarIn.getNextTarEntry();
		}
		tarIn.close();
	}

	private static String bits(byte b) {
		return String.format(Locale.US, "%6s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
	}

	public static boolean isWin() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}

	public static boolean isLin() {
		return System.getProperty("os.name").toLowerCase().contains("linux");
	}

	public static boolean isMac() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
	}

	public static boolean isArm() {
		return System.getProperty("os.arch").toLowerCase().contains("aarch64")
				|| System.getProperty("os.arch").toLowerCase().contains("arm");
	}

	public static String discoverKey() {
		String key = "UNKNOWN";
		if (isLin()) {
			if (isArm()) {
				key = "Linux-aarch64";
			} else {
				key = "Linux-x64";
			}
		}

		if (isMac()) {
			if (isArm()) {
				key = "Mac-aarch64";
			} else {
				key = "Mac-x64";
			}
		}
		if (isWin()) {
			if (isArm()) {
				key = "UNKNOWN";
			} else {
				key = "Windows-x64";
			}
		}
		if (key.contentEquals("UNKNOWN")) {
			throw new RuntimeException(
					"Unsupported OS/Arch " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
		}
		return key;
	}

	/**
	 *
	 * @param version
	 *            A string indicating version, this will be the folder name
	 * @param URL
	 *            The direct URL of the download
	 * @param sizeOfFile
	 *            The number of bytes in the file
	 * @param directoryInWhichFileIsStored
	 *            The root directory into which this will all be downloaded
	 * @param filename
	 *            The resulting filename
	 * @param downloadName
	 *            User level name for asking about the download
	 * @return
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws InterruptedException
	 */
	public static File download(String version, String URL, long sizeOfFile, String directoryInWhichFileIsStored,
			String filename, String downloadName)
			throws MalformedURLException, IOException, FileNotFoundException, InterruptedException {

		URL url = new URL(URL);
		URLConnection connection = url.openConnection();
		InputStream is = connection.getInputStream();
		ProcessInputStream pis = new ProcessInputStream(is, (int) sizeOfFile);
		pis.addListener(new Listener() {
			long timeSinceePrint = System.currentTimeMillis();

			@Override
			public void process(double percent) {
				if (System.currentTimeMillis() - timeSinceePrint > 1000) {
					timeSinceePrint = System.currentTimeMillis();
					psudoSplash.onLogUpdate((int) (percent * 100) + " % " + filename, null);
				}
				// if(progress!=null)
				// Platform.runLater(() -> {
				// progress.setProgress(percent);
				// });
			}
		});
		File folder = new File(bindir + version + "/");
		File exe = new File(bindir + version + "/" + filename);

		if (!folder.exists() || !exe.exists()) {

			if (approval.get(downloadName, URL)) {
				com.neuronrobotics.sdk.common.Log.debug("Start Downloading " + filename);
				com.neuronrobotics.sdk.common.Log.debug("From " + URL);

			} else {
				pis.close();
				throw new RuntimeException("No Application insalled");
			}
			downloadEvents.startDownload();
			rawFileDownload(pis, folder, exe);
			com.neuronrobotics.sdk.common.Log.debug("Finished downloading " + filename);
			psudoSplash.onLogUpdate((int) (1 * 100) + " %  " + filename, null);
			downloadEvents.finishDownload();
		} else {
			com.neuronrobotics.sdk.common.Log.debug("Not downloading, it existst " + filename);
		}
		return exe;
	}

	private static void rawFileDownload(ProcessInputStream pis, File folder, File output)
			throws IOException, FileNotFoundException {
		folder.mkdirs();
		output.createNewFile();
		byte dataBuffer[] = new byte[1024 * 1000];
		int bytesRead;
		File exe = File.createTempFile("tmp", output.getName());
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(exe.getAbsoluteFile());

			while ((bytesRead = pis.read(dataBuffer, 0, dataBuffer.length)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}
			fileOutputStream.close();
			pis.close();
			FileOutputStream out = new FileOutputStream(output.getAbsoluteFile());
			Files.copy(exe.toPath(), out);
			out.flush();
			out.close();
		} catch (Exception ex) {
			com.neuronrobotics.sdk.common.Log.error(ex);;
			output.delete();
		}
		exe.delete();
	}

	/**
	 * @return the editorsURL
	 */
	public static String getEditorsURL() {
		return editorsURL;
	}

	/**
	 * @param editorsURL
	 *            the editorsURL to set
	 */
	public static void setEditorsURL(String editorsURL) {
		DownloadManager.editorsURL = editorsURL;
	}

	public static String delim() {
		if (OSUtil.isWindows())
			return "\\";
		return "/";
	}
	// public static void main(String[] args) {
	// try {
	// PasswordManager.login();
	// } catch (IOException e) {
	// // Auto-generated catch block
	// com.neuronrobotics.sdk.common.Log.error(e);
	// }
	// File f = getRunExecutable("eclipse",null);
	// String ws = EclipseExternalEditor.getEclipseWorkspace();
	// if(f.exists()) {
	// com.neuronrobotics.sdk.common.Log.error("Executable
	// retrived:\n"+f.getAbsolutePath());
	// run(getEnvironment("eclipse"),null,f.getParentFile(),
	// System.err,Arrays.asList(f.getAbsolutePath(),"-data", ws));
	// }
	// else
	// com.neuronrobotics.sdk.common.Log.error("Failed to load
	// file!\n"+f.getAbsolutePath());
	// }

	public static IApprovalForDownload getApproval() {
		return approval;
	}

	public static void setApproval(IApprovalForDownload approval) {
		DownloadManager.approval = approval;
	}

	public static void addLogListener(GitLogProgressMonitor psudoSplash) {
		DownloadManager.psudoSplash = psudoSplash;
	}

	public static IDownloadManagerEvents getDownloadEvents() {
		return downloadEvents;
	}

	public static void setDownloadEvents(IDownloadManagerEvents de) {
		if (downloadEvents != null)
			downloadEvents = de;
	}

	public static String getSTUDIO_INSTALL() {
		return STUDIO_INSTALL;
	}

	public static void setSTUDIO_INSTALL(String sTUDIO_INSTALL) {
		STUDIO_INSTALL = sTUDIO_INSTALL;
	}

	public static boolean isDownloadedAlready(String string) {
		File f = DownloadManager.getRunExecutable(string, null, true);
		return f.exists();
	}

}
