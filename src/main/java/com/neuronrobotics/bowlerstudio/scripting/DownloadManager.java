package com.neuronrobotics.bowlerstudio.scripting;

import org.apache.commons.exec.*;
import org.apache.commons.exec.environment.EnvironmentUtils;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.run;

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
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarUtils;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FilenameUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.video.OSUtil;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;

public class DownloadManager {
	private static String editorsURL= "https://github.com/CommonWealthRobotics/ExternalEditorsBowlerStudio.git";
	private static String 		bindir = System.getProperty("user.home") + "/bin/BowlerStudioInstall/";
	private static int ev=0;
	private static String cmd="";
	private static ButtonType buttonType=null;
	public static Thread run(IExternalEditor editor,File dir, PrintStream out,List<String> finalCommand) {
		return run(new HashMap<String,String>(),editor,dir,out,finalCommand);
	}
	public static Thread run(Map<String,String> envincoming,IExternalEditor editor,File dir, PrintStream out,List<String> finalCommand) {


		Thread thread = new Thread(() -> {
			

			try {
				// creating the process
		        CommandLine cmdLine;
		        if(isMac()) {
		        	cmd="";
		        	for(String s:finalCommand) {
		        		cmd+=sanitize(s)+" ";
		        	}
					ProcessBuilder pb = new ProcessBuilder(finalCommand);
					Map<String, String> envir = pb.environment();
					// set environment variable u
					envir.putAll(envincoming);
					for (String s : envincoming.keySet()) {
						System.out.println("Environment var set: "+s+" to "+envir.get(s));
					}
					// setting the directory
					pb.directory(dir);
					// startinf the process
			        out.println("Running command:\n");
					out.println(cmd);

					out.println("\nIn "+dir.getAbsolutePath());
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
						System.out.println("ERROR PROCESS Process exited with " + ev);
					}
					while (process.isAlive()) {
						Thread.sleep(100);
					}
					out.println("");
		        }else {
			        cmdLine= new CommandLine(sanitize(finalCommand.get(0)));
			        cmd=cmdLine.getExecutable();
	
			        // Add arguments
			        for (int i=1;i<finalCommand.size();i++) {
			        	String san = sanitize(finalCommand.get(i));
			        	cmd+=" "+san;
			            cmdLine.addArgument(san,false);
			        }
			        out.println("Running command:\n");
					out.println(cmd);

					out.println("\nIn "+dir.getAbsolutePath());
					out.println("\n\n");

			        DefaultExecutor executor = new DefaultExecutor();
			        executor.setWorkingDirectory(dir);
		            Map<String, String> env = EnvironmentUtils.getProcEnvironment();
		            env.putAll(envincoming);
			        
		            PipedOutputStream outPipe = new PipedOutputStream();
		            PipedInputStream outPipeIn = new PipedInputStream(outPipe);
		            PipedOutputStream errPipe = new PipedOutputStream();
		            PipedInputStream errPipeIn = new PipedInputStream(errPipe);
		            
		            PumpStreamHandler streamHandler = new PumpStreamHandler(outPipe, errPipe);
		            executor.setStreamHandler(streamHandler);
		            startOutputReader(outPipeIn, "OUTPUT",out);
		            startOutputReader(errPipeIn, "ERROR",out);
		            ev = executor.execute(cmdLine, env);	            
					out.println("");
		        }

				if(editor!=null)editor.onProcessExit(ev);
					
			} catch (Throwable e) {
				e.printStackTrace(out);
				if(editor!=null)
					BowlerKernel.runLater(()->{
						Alert alert = new Alert(AlertType.ERROR);
						alert.setTitle(editor.nameOfEditor()+" is missing");
						alert.setHeaderText("failed to run "+cmd);
						alert.setContentText("Close to bring me to the install website");
						alert.showAndWait();
					});
				
			

			}
		});
		thread.start();
		return thread;
	}
    private static String sanitize(String s) {

    	String string =s;
		if(s.contains(" ")) {

				string ="\""+s+"\"";
		}
		return string;
	}
	private static void startOutputReader(final InputStream is, final String type, PrintStream out) {
        new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.println(type + "> " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
	public static Map<String,String> getEnvironment(String exeType) {
		String key = discoverKey();
		
		try {
			for(String f:ScriptingEngine.filesInGit(editorsURL)) {
				File file = ScriptingEngine.fileFromGit(editorsURL, f);
				if( file.getName().toLowerCase().startsWith(exeType.toLowerCase())&&
					file.getName().toLowerCase().endsWith(".json")) {
					String jsonText = new String(Files.readAllBytes(file.toPath()));
					Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
					}.getType();
					Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
					HashMap<String, Object> database = gson.fromJson(jsonText, TT_mapStringString);
					Map<String, Object> vm = (Map<String, Object>) database.get(key);
					if(vm!=null) {
						String baseURL = vm.get("url").toString();
						String type = vm.get("type").toString();
						String name = vm.get("name").toString();
						String exeInZip=vm.get("executable").toString();
						String jvmURL = baseURL + name + "." + type;
						Map<String,String> environment;
						Object o = vm.get("environment");
						if(o!=null) {
							System.out.println("Environment found for "+exeType+" on "+key);

							return (Map<String, String>) o;
						}						
					}
				}
			}
		}catch(Throwable t) {
			t.printStackTrace();
			
		}
		return new HashMap<>();
	}
	public static File getRunExecutable(String exeType ,IExternalEditor editor) {
		return getExecutable(exeType,editor,"executable");
	}
	public static File getConfigExecutable(String exeType ,IExternalEditor editor) {
		return getExecutable(exeType,editor,"configExecutable");
	}
	private static File getExecutable(String exeType ,IExternalEditor editor,String executable) {
		String key = discoverKey();
		
		try {
			for(String f:ScriptingEngine.filesInGit(editorsURL)) {
				File file = ScriptingEngine.fileFromGit(editorsURL, f);
				if( file.getName().toLowerCase().startsWith(exeType.toLowerCase())&&
					file.getName().toLowerCase().endsWith(".json")) {
					String jsonText = new String(Files.readAllBytes(file.toPath()));
					Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
					}.getType();
					Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
					HashMap<String, Object> database = gson.fromJson(jsonText, TT_mapStringString);
					Map<String, Object> vm = (Map<String, Object>) database.get(key);
					if(vm!=null) {
						System.out.println("Configuration found for "+exeType+" on "+key);
						String baseURL = vm.get("url").toString();
						String type = vm.get("type").toString();
						String name = vm.get("name").toString();
						String exeInZip=vm.get(executable).toString();
						String configexe=vm.get("configExecutable").toString();
						String jvmURL = baseURL + name + "." + type;
						Map<String,String> environment;
						Object o = vm.get("environment");
						if(o!=null) {
							environment=(Map<String, String>) o;
						}else
							environment= new HashMap<>();
						String targetdir=exeType;
						File jvmArchive = download("", jvmURL, 400000000, bindir, name + "." + type,exeType);
						File dest = new File(bindir + targetdir);
						String cmd = bindir + targetdir + "/"+exeInZip;
						if (!dest.exists()) {
							if (type.toLowerCase().contains("zip")) {
								unzip(jvmArchive, bindir+targetdir);
							}
							if (type.toLowerCase().contains("tar.gz")) {
								untar(jvmArchive, bindir+targetdir);
							}
							if (type.toLowerCase().contains("dmg")) {
								dmgExtract(jvmArchive, bindir+targetdir,exeInZip);
							}
							
							Object configurations =database.get("Meta-Configuration");
							if(configurations!=null) {
								List<String> configs=(List<String>) configurations;
								System.out.println("Got Configurations "+configs.size());
								ev=-1;
								IExternalEditor errorcheckerEditor = new IExternalEditor() {
									
									

									@Override
									public void onProcessExit(int e) {
										ev = e;
										// TODO Auto-generated method stub
										
									}
									
									@Override
									public String nameOfEditor() {
										// TODO Auto-generated method stub
										return null;
									}
									
									@Override
									public void launch(File file, Button advanced) {
										// TODO Auto-generated method stub
										
									}
									
									@Override
									public Class getSupportedLangauge() {
										// TODO Auto-generated method stub
										return null;
									}
									
									@Override
									public URL getInstallURL() throws MalformedURLException {
										// TODO Auto-generated method stub
										return null;
									}
									
									@Override
									public Image getImage() {
										// TODO Auto-generated method stub
										return null;
									}
								};
								for(int i=0;i<configs.size();i++) {
									System.out.println("Running "+exeType+" Configuration "+(i+1)+" of "+configs.size());
									ArrayList<String> toRun=new ArrayList<>();
									toRun.add(bindir + targetdir + "/"+configexe);
									String[] conf = configs.get(i).split(" ");
									for(int j=0;j<conf.length;j++) {
										toRun.add(conf[j]);
									}
									//= +" "+configs.get(i);
									
									//System.out.println(toRun);
									
									Thread thread =run(errorcheckerEditor,new File(bindir), System.out,toRun);
									thread.join();
									if(ev!=0) {
										throw new RuntimeException("Configuration failed for OS: "+key+" has no entry for "+exeType);
									}
								}
							}
						} else {
							System.out.println("Not extraction, VM exists " + dest.getAbsolutePath());
						}
						
						return new File(cmd);
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		throw new RuntimeException("Executable for OS: "+key+" has no entry for "+exeType);
	}
	
	private static void dmgExtract(File jvmArchive, String string,String appDir) {
		// since DMG is Mac only, and Mac always has the command line extractors, we will use those
		File location = new File(string);

		File[] listFiles = new File("/Volumes/").listFiles();
		Set<String> before =Stream.of(listFiles)
	      .filter(file -> file.isDirectory())
	      .map(File::getName)
	      .collect(Collectors.toSet());
		Thread t=run(null,new File("."),System.err, Arrays.asList("hdiutil", "attach",jvmArchive.getAbsolutePath()));
		try {
			t.join();
			Thread.sleep(2000);// wait for mount to settle
			File[] listFilesAfter = new File("/Volumes/").listFiles();
			Set<String> after =Stream.of(listFilesAfter)
				      .filter(file -> file.isDirectory())
				      .map(File::getName)
				      .collect(Collectors.toSet());
			after.removeAll(before);
			Object[] array = after.toArray();
			String newMount = (String) array[0];
			System.out.println("Extracted "+jvmArchive.getAbsolutePath()+" is mounted at "+newMount);
			//asr restore --source "$MOUNT_POINT" --target "$DEST_PATH" --erase --noprompt
			if(!location.exists()) {
				location.mkdirs();
			}
			Thread tcopy=run(null,new File("."),System.out, Arrays.asList("rsync", "-avtP","/Volumes/"+newMount+"/"+appDir,string+"/"));
			tcopy.join();

			
			Thread tdetach=run(null,new File("."),System.err, Arrays.asList("hdiutil", "detach","/Volumes/"+newMount));
			tdetach.join();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}//wait for the mount to finish

		System.out.println("Extracted "+jvmArchive.getAbsolutePath());

		
	}
	public static boolean isExecutable(ZipArchiveEntry entry) {
		int unixMode = entry.getUnixMode();
		// Check if any of the executable bits are set for user, group, or others.
		// User executable: 0100 (0x40), Group executable: 0010 (0x10), Others
		// executable: 0001 (0x01)
		return (unixMode & 0x49) != 0;
	}
	public static void unzip(File path, String dir) throws Exception {
		System.out.println("Unzipping "+path.getName()+" into "+dir);
		String fileBaseName = FilenameUtils.getBaseName(path.getName().toString());
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
						try (InputStream in = zipFile.getInputStream(entry)) {
							try {
								//ar.setExternalAttributes(entry.extraAttributes);
								if (entry.isUnixSymlink()) {
									String text = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
											.lines().collect(Collectors.joining("\n"));
									Path target = Paths.get(".", text);
									System.out.println("Creating symlink "+entryPath+" with "+target);

									Files.createSymbolicLink(entryPath, target);
									continue;
								}
							} catch (Exception ex) {
								ex.printStackTrace();
							}
							try (OutputStream out = new FileOutputStream(entryPath.toFile())) {
								IOUtils.copy(in, out);
								System.out.println("Inflating "+entryPath);

							}
							if(isExecutable(entry)) {
								entryPath.toFile().setExecutable(true);
							}
						}
					}
				}
			}
		}
	}

	public static void untar(File tarFile, String dir) throws Exception {
		System.out.println("Untaring "+tarFile.getName()+" into "+dir);

		File dest = new File(dir);
		dest.mkdir();
		TarArchiveInputStream tarIn = null;
		try {
		tarIn = new TarArchiveInputStream(
				new GzipCompressorInputStream(new BufferedInputStream(new FileInputStream(tarFile))));
		}catch (java.io.IOException ex) {
			tarFile.delete();
			return;
		}
		TarArchiveEntry tarEntry = tarIn.getNextTarEntry();
		// tarIn is a TarArchiveInputStream
		while (tarEntry != null) {// create a file with the same name as the tarEntry
			File destPath = new File(dest.toString() + System.getProperty("file.separator") + tarEntry.getName());
			System.out.println("Inflating: " + destPath.getCanonicalPath());
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
		return String.format("%6s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
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
		return System.getProperty("os.arch").toLowerCase().contains("aarch64")||System.getProperty("os.arch").toLowerCase().contains("arm");
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
		if(key.contentEquals("UNKNOWN")) {
			throw new RuntimeException("Unsupported OS/Arch "+System.getProperty("os.name")+" "+System.getProperty("os.arch"));
		}
		return key;
	}


	public static File download(String version, String downloadJsonURL, long sizeOfJson,String bindir, String filename, String downloadName) throws MalformedURLException, IOException, FileNotFoundException, InterruptedException {
		
		
		URL url = new URL(downloadJsonURL);
		URLConnection connection = url.openConnection();
		InputStream is = connection.getInputStream();
		ProcessInputStream pis = new ProcessInputStream(is, (int) sizeOfJson);
		pis.addListener(new Listener() {
			long timeSinceePrint=System.currentTimeMillis();
			@Override
			public void process(double percent) {
				if(System.currentTimeMillis()-timeSinceePrint>100) {
					timeSinceePrint=System.currentTimeMillis();
					System.out.println("Download percent " + (int)(percent*100));
				}
//				if(progress!=null)
//					Platform.runLater(() -> {
//						progress.setProgress(percent);
//					});
			}
		});
		File folder = new File(bindir + version + "/");
		File exe = new File(bindir + version + "/" + filename);

		if (!folder.exists() || !exe.exists()) {
			buttonType=null;
			
			BowlerKernel.runLater(()->{
				Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
				alert.setTitle("Message");
				alert.setHeaderText("Would you like to download: "+downloadName+"\nfrom:\n"+downloadJsonURL);
				Optional<ButtonType> result = alert.showAndWait();
				buttonType = result.get();
				alert.close();
			});
			
			while(buttonType==null) {
				Thread.sleep(100);
				
			}
			
			if (buttonType.equals(ButtonType.OK)) { 
				System.out.println("Start Downloading " + filename);

			}else  {
				pis.close();
				throw new RuntimeException("No Eclipse insalled");
			}
			
			folder.mkdirs();
			exe.createNewFile();
			byte dataBuffer[] = new byte[1024];
			int bytesRead;
			FileOutputStream fileOutputStream = new FileOutputStream(exe.getAbsoluteFile());
			while ((bytesRead = pis.read(dataBuffer, 0, 1024)) != -1) {
				fileOutputStream.write(dataBuffer, 0, bytesRead);
			}
			fileOutputStream.close();
			pis.close();
			System.out.println("Finished downloading " + filename);
			System.out.println("Download percent " + (int)(1*100));
		} else {
			System.out.println("Not downloadeing, it existst " + filename);
		}
		return exe;
	}

	/**
	 * @return the editorsURL
	 */
	public static String getEditorsURL() {
		return editorsURL;
	}

	/**
	 * @param editorsURL the editorsURL to set
	 */
	public static void setEditorsURL(String editorsURL) {
		DownloadManager.editorsURL = editorsURL;
	}
	public static String delim() {
		if (OSUtil.isWindows())
			return "\\";
		return "/";
	}
//	public static void main(String[] args) {
//		try {
//			PasswordManager.login();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		File f = getRunExecutable("eclipse",null);
//		String ws = EclipseExternalEditor.getEclipseWorkspace();
//		if(f.exists()) {
//			System.out.println("Executable retrived:\n"+f.getAbsolutePath());
//			run(getEnvironment("eclipse"),null,f.getParentFile(), System.err,Arrays.asList(f.getAbsolutePath(),"-data", ws));
//		}
//		else
//			System.out.println("Failed to load file!\n"+f.getAbsolutePath());
//	}

}
