package com.neuronrobotics.bowlerstudio.scripting;

import com.neuronrobotics.bowlerstudio.util.FileChangeWatcher;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.util.ThreadUtil;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.DetachedHeadException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.kohsuke.github.GHGist;
import org.kohsuke.github.GitHub;
import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javafx.scene.web.WebEngine;

public class ScriptingEngine {// this subclasses boarder pane for the widgets
  // sake, because multiple inheritance is TOO
  // hard for java...
  private static final int TIME_TO_WAIT_BETWEEN_GIT_PULL = 100000;
  /**
   *
   */
  private static final Map<String, Long> fileLastLoaded = new HashMap<String, Long>();

  private static boolean autoupdate = false;

  private static final String[] imports = new String[] { // "haar",
      "java.nio.file", "java.util", "java.awt.image", "javafx.scene.text", "javafx.scene",
      "javafx.scene.control", "eu.mihosoft.vrl.v3d", "eu.mihosoft.vrl.v3d.svg",
      "eu.mihosoft.vrl.v3d.samples", "eu.mihosoft.vrl.v3d.parametrics",
      "com.neuronrobotics.imageprovider", "com.neuronrobotics.sdk.addons.kinematics.xml",
      "com.neuronrobotics.sdk.addons.kinematics", "com.neuronrobotics.sdk.dyio.peripherals",
      "com.neuronrobotics.sdk.dyio", "com.neuronrobotics.sdk.common", "com.neuronrobotics.sdk.ui",
      "com.neuronrobotics.sdk.util", "com.neuronrobotics.sdk.serial",
      "com.neuronrobotics.sdk.addons.kinematics", "com.neuronrobotics.sdk.addons.kinematics.math",
      "com.neuronrobotics.sdk.addons.kinematics.gui", "com.neuronrobotics.sdk.config",
      "com.neuronrobotics.bowlerkernel", "com.neuronrobotics.bowlerstudio",
      "com.neuronrobotics.bowlerstudio.scripting", "com.neuronrobotics.bowlerstudio.tabs",
      "com.neuronrobotics.bowlerstudio.physics", "com.neuronrobotics.bowlerstudio.physics",
      "com.neuronrobotics.bowlerstudio.vitamins", "com.neuronrobotics.bowlerstudio.creature",
      "com.neuronrobotics.bowlerstudio.threed"};
  
 
  private static HashMap<String, File> filesRun = new HashMap<>();

  // private static GHGist gist;

  private static File workspace;
  private static File lastFile;
  private static TransportConfigCallback transportConfigCallback = new SshTransportConfigCallback();

  // UsernamePasswordCredentialsProvider(name,
  // password);
  private static ArrayList<IGithubLoginListener> loginListeners =
      new ArrayList<IGithubLoginListener>();

  private static HashMap<String, IScriptingLanguage> langauges = new HashMap<>();



  static {

    PasswordManager.hasNetwork();
    workspace = new File(System.getProperty("user.home") + "/bowler-workspace/");
    if (!workspace.exists()) {
      workspace.mkdir();
    }
    File oldpass = new File(System.getProperty("user.home") + "/.github");
    if(oldpass.exists())
    	oldpass.delete();
    try {
    	PasswordManager.loadLoginData(workspace);
      // runLogin();
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
    addScriptingLanguage(new ClojureHelper());
    addScriptingLanguage(new GroovyHelper());
    addScriptingLanguage(new JythonHelper());
    addScriptingLanguage(new RobotHelper());
    addScriptingLanguage(new JsonRunner());
    addScriptingLanguage(new ArduinoLoader());
  }

  /**
   * This interface is for adding additional language support.
   *
   * @param code file content of the code to be executed
   * @param args the incoming arguments as a list of objects
   * @return the objects returned form the code that ran
   */
  public static Object inlineScriptRun(File code, ArrayList<Object> args, String shellTypeStorage)
      throws Exception {
    if (filesRun.get(code.getName()) == null) {
      filesRun.put(code.getName(), code);
      // System.out.println("Loading "+code.getAbsolutePath());
    }

    if (langauges.get(shellTypeStorage) != null) {
      return langauges.get(shellTypeStorage).inlineScriptRun(code, args);
    }
    return null;
  }

  /**
   * This interface is for adding additional language support.
   *
   * @param line the text content of the code to be executed
   * @param args the incoming arguments as a list of objects
   * @return the objects returned form the code that ran
   */
  public static Object inlineScriptStringRun(String line, ArrayList<Object> args,
      String shellTypeStorage) throws Exception {

    if (langauges.get(shellTypeStorage) != null) {
      return langauges.get(shellTypeStorage).inlineScriptRun(line, args);
    }
    return null;
  }



  public static void addScriptingLanguage(IScriptingLanguage lang) {
    langauges.put(lang.getShellType(), lang);
  }

  public static void addIGithubLoginListener(IGithubLoginListener l) {
    if (!loginListeners.contains(l)) {
      loginListeners.add(l);
    }
  }

  public static void removeIGithubLoginListener(IGithubLoginListener l) {
    if (loginListeners.contains(l)) {
      loginListeners.remove(l);
    }
  }

  public static File getWorkspace() {
    // System.err.println("Workspace: "+workspace.getAbsolutePath());
    return workspace;
  }

  public static String getShellType(String name) {
    for (IScriptingLanguage l : langauges.values()) {
      if (l.isSupportedFileExtenetion(name))
        return l.getShellType();
    }

    return "Groovy";
  }

  public static void login() throws IOException {
    if (!PasswordManager.hasNetwork())
      return;
    PasswordManager.login();
    if(PasswordManager.loggedIn())
	    for (IGithubLoginListener l : loginListeners) {
			l.onLogin(PasswordManager.getUsername());
		}
  }

  public static void logout() throws IOException {
    
	for (IGithubLoginListener l : loginListeners) {
		l.onLogout(PasswordManager.getUsername());
	}
	PasswordManager.logout();
  }

  public static GitHub setupAnyonmous() throws IOException {
	ScriptingEngine.setAutoupdate(false);
    return PasswordManager.setupAnyonmous();
  }

  public static String urlToGist(String in) {

    if (in.endsWith(".git")) {
      in = in.substring(0, in.lastIndexOf('.'));
    }
    String domain = in.split("//")[1];
    String[] tokens = domain.split("/");
    if (tokens[0].toLowerCase().contains("gist.github.com") && tokens.length >= 2) {
      try {
        String id = tokens[2].split("#")[0];
        Log.debug("Gist URL Detected " + id);
        return id;
      } catch (ArrayIndexOutOfBoundsException e) {
        try {
          String id = tokens[1].split("#")[0];
          Log.debug("Gist URL Detected " + id);
          return id;
        } catch (ArrayIndexOutOfBoundsException ex) {
          Log.error("Parsing " + in + " failed to find gist");
          return "d4312a0787456ec27a2a";
        }
      }
    }

    return null;
  }

  private static List<String> returnFirstGist(String html) {
    // Log.debug(html);
    ArrayList<String> ret = new ArrayList<>();
    Document doc = Jsoup.parse(html);
    Elements links = doc.select("script");
    for (int i = 0; i < links.size(); i++) {
      Element e = links.get(i);
      /// System.out.println("Found gist embed: "+e);
      Attributes n = e.attributes();
      String jSSource = n.get("src");
      if (jSSource.contains("https://gist.github.com/")) {
        // System.out.println("Source = "+jSSource);
        String slug = jSSource;
        String js = slug.split(".js")[0];
        String[] id = js.split("/");
        ret.add(id[id.length - 1]);
      }
    }
    return ret;
  }

  public static List<String> getCurrentGist(String addr, WebEngine engine) {
    String gist = urlToGist(addr);

    if (gist == null) {
      try {
        Log.debug("Non Gist URL Detected");
        String html;
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer t = tf.newTransformer();
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(engine.getDocument()), new StreamResult(sw));
        html = sw.getBuffer().toString();
        return returnFirstGist(html);
      } catch (TransformerConfigurationException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (TransformerException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

    }
    ArrayList<String> ret = new ArrayList<>();
    ret.add(gist);
    return ret;
  }

  /**
   * The GistID we are waiting to see
   */
  public static void waitForLogin()
      throws IOException, InvalidRemoteException, TransportException, GitAPIException {
    if (!PasswordManager.hasNetwork())
      return;
    try {
		PasswordManager.waitForLogin();
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}

  }

  public static void deleteRepo(String remoteURI) {

    File gitRepoFile = uriToFile(remoteURI);
    deleteFolder(gitRepoFile.getParentFile());
  }

  public static void deleteCache() {
    deleteFolder(new File(getWorkspace().getAbsolutePath() + "/gitcache/"));
  }

  public static void deleteFolder(File folder) {
		if (!folder.exists() || !folder.isDirectory())
			throw new RuntimeException("Folder doesnt exist " + folder);
		File[] files = folder.listFiles();
		if (files != null) { // some JVMs return null for empty dirs
			for (File f : files) {
				if (f.isDirectory()) {
					deleteFolder(f);
				} else {
					try {
						FileChangeWatcher.close(f);
						f.delete();
					} catch (Throwable t) {
						t.printStackTrace();
					}
					// System.out.println("Deleting " + f.getAbsolutePath());
				}
			}
		}
		try {
			folder.delete();
		} catch (Throwable t) {
			t.printStackTrace();
		}
  }

  private static void loadFilesToList(ArrayList<String> f, File directory, String extnetion) {
	if(directory==null)
		return;
    for (final File fileEntry : directory.listFiles()) {

      if (fileEntry.getName().endsWith(".git") || fileEntry.getName().startsWith(".git"))
        continue;// ignore git files
      if (extnetion != null)
        if (extnetion.length() > 0)
          if (!fileEntry.getName().endsWith(extnetion))
            continue;// skip this file as it fails the filter
      // from the user
      if (fileEntry.isDirectory()) {
        loadFilesToList(f, fileEntry, extnetion);
      } else {

        for (IScriptingLanguage l : langauges.values()) {
          if (l.isSupportedFileExtenetion(fileEntry.getName())) {
            f.add(findLocalPath(fileEntry));
            break;
          }
        }

      }
    }
  }

  public static ArrayList<String> filesInGit(String remote, String branch, String extnetion)
      throws Exception {
    ArrayList<String> f = new ArrayList<>();

    //waitForLogin();
    File gistDir = cloneRepo(remote, branch);
    loadFilesToList(f, gistDir, extnetion);

    return f;

  }

  public static ArrayList<String> filesInGit(String remote) throws Exception {
    return filesInGit(remote, ScriptingEngine.getFullBranch(remote), null);
  }

  // private static ArrayList<String> filesInGist(String id) throws Exception{
  // return filesInGist(id, null);
  // }
  //
  public static String getUserIdOfGist(String id) throws Exception {

    waitForLogin();
    Log.debug("Loading Gist: " + id);
    GHGist gist;

    gist = PasswordManager.getGithub().getGist(id);
    return gist.getOwner().getLogin();

  }

  public static File createFile(String git, String fileName, String commitMessage)
      throws Exception {
    pushCodeToGit(git, ScriptingEngine.getFullBranch(git), fileName, null, commitMessage);
    return fileFromGit(git, fileName);
  }

  public static void pushCodeToGit(String id, String branch, String FileName, String content,
      String commitMessage) throws Exception {
	    if (PasswordManager.getUsername() == null)
	        login();
	      if (!hasNetwork())
	        return;// No login info means there is no way to publish
    File gistDir = cloneRepo(id, branch);
    File desired = new File(gistDir.getAbsoluteFile() + "/" + FileName);

    boolean flagNewFile = false;
    if (!desired.exists()) {
      desired.createNewFile();
      flagNewFile = true;
    }
    pushCodeToGit(id, branch, FileName, content, commitMessage, flagNewFile);
  }

  public static void commit(String id, String branch, String FileName, String content,
      String commitMessage, boolean flagNewFile) throws Exception {

	    if (PasswordManager.getUsername()  == null)
	        login();
	      if (!hasNetwork())
	        return;// No login info means there is no way to publish
    File gistDir = cloneRepo(id, branch);
    File desired = new File(gistDir.getAbsoluteFile() + "/" + FileName);

    String localPath = gistDir.getAbsolutePath();
    File gitRepoFile = new File(localPath + "/.git");

    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
    Git git = new Git(localRepo);
    try { // latest version
      if (flagNewFile) {
        git.add().addFilepattern(FileName).call();
      }
      if (content != null) {
        OutputStream out = null;
        try {
          out = FileUtils.openOutputStream(desired, false);
          IOUtils.write(content, out);
          out.close(); // don't swallow close Exception if copy
          // completes
          // normally
        } finally {
          IOUtils.closeQuietly(out);
        }
      }

      git.commit().setAll(true).setMessage(commitMessage).call();
    } catch (Exception ex) {
      git.close();

      throw ex;
    }
    git.close();
    try {
      if (!desired.getName().contentEquals("csgDatabase.json")) {
        String[] gitID = ScriptingEngine.findGitTagFromFile(desired);
        String remoteURI = gitID[0];
        ArrayList<String> f = ScriptingEngine.filesInGit(remoteURI);
        for (String s : f) {
          if (s.contentEquals("csgDatabase.json")) {

            File dbFile = ScriptingEngine.fileFromGit(gitID[0], s);
            if (!CSGDatabase.getDbFile().equals(dbFile))
              CSGDatabase.setDbFile(dbFile);
            CSGDatabase.saveDatabase();
            @SuppressWarnings("resource")
            String c = new Scanner(dbFile).useDelimiter("\\Z").next();
            ScriptingEngine.commit(remoteURI, branch, s, c, "saving CSG database", false);
          }
        }
      }
    } catch (Exception e) {
      // ignore CSG database
      e.printStackTrace();
    }
  }

  public static void pushCodeToGit(String id, String branch, String FileName, String content,
      String commitMessage, boolean flagNewFile) throws Exception {
    commit(id, branch, FileName, content, commitMessage, flagNewFile);
    if (PasswordManager.getUsername()  == null)
      login();
    if (!hasNetwork())
      return;// No login info means there is no way to publish
    File gistDir = cloneRepo(id, branch);
    File desired = new File(gistDir.getAbsoluteFile() + "/" + FileName);

    if (!PasswordManager.hasNetwork() && content != null) {
      OutputStream out = null;
      try {
        out = FileUtils.openOutputStream(desired, false);
        IOUtils.write(content, out);
        out.close(); // don't swallow close Exception if copy completes
        // normally
      } finally {
        IOUtils.closeQuietly(out);
      }
      return;
    }

    waitForLogin();
    String localPath = gistDir.getAbsolutePath();
    File gitRepoFile = new File(localPath + "/.git");

    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
    Git git = new Git(localRepo);
    try {
    	
      pull(id,branch);
    	
      // latest version
      if (flagNewFile) {
        git.add().addFilepattern(FileName).call();
      }
      if (content != null) {
        OutputStream out = null;
        try {
          out = FileUtils.openOutputStream(desired, false);
          IOUtils.write(content, out);
          out.close(); // don't swallow close Exception if copy
          // completes
          // normally
        } finally {
          IOUtils.closeQuietly(out);
        }
      }
      if(git.getRepository().getConfig().getString("remote", "origin", "url").startsWith("git@"))
          git.push().setTransportConfigCallback(transportConfigCallback).call();
      else
    	  git.push().setCredentialsProvider(PasswordManager.getCredentialProvider()).call();
      System.out.println("PUSH OK! file: " + desired);
    } catch (Exception ex) {
      String[] gitID = ScriptingEngine.findGitTagFromFile(desired);
      String remoteURI = gitID[0];
      deleteRepo(remoteURI);
      git.close();
      throw ex;
    }
    git.close();

  }

  public static String[] codeFromGit(String id, String FileName) throws Exception {

    File targetFile = fileFromGit(id, FileName);
    if (targetFile.exists()) {
      // System.err.println("Loading file:
      // "+targetFile.getAbsoluteFile());
      // Target file is ready to go
      String text = new String(Files.readAllBytes(Paths.get(targetFile.getAbsolutePath())),
          StandardCharsets.UTF_8);
      return new String[] {text, FileName, targetFile.getAbsolutePath()};
    }

    return null;
  }

  private static String[] codeFromGistID(String id, String FileName) throws Exception {
    String giturl = "https://gist.github.com/" + id + ".git";

    File targetFile = fileFromGit(giturl, FileName);
    if (targetFile.exists()) {
      System.err.println("Gist at GIT : " + giturl);
      // Target file is ready to go
      String text = new String(Files.readAllBytes(Paths.get(targetFile.getAbsolutePath())),
          StandardCharsets.UTF_8);
      return new String[] {text, FileName, targetFile.getAbsolutePath()};
    }

    return null;
  }

  public static Object inlineFileScriptRun(File f, ArrayList<Object> args) throws Exception {

    return inlineScriptRun(f, args, getShellType(f.getName()));
  }

  public static Object inlineGistScriptRun(String gistID, String Filename, ArrayList<Object> args)
      throws Exception {
    String[] gistData = codeFromGistID(gistID, Filename);
    return inlineScriptRun(new File(gistData[2]), args, getShellType(gistData[1]));
  }

  public static Object gitScriptRun(String gitURL, String Filename, ArrayList<Object> args)
      throws Exception {
    String[] gistData = codeFromGit(gitURL, Filename);
    return inlineScriptRun(new File(gistData[2]), args, getShellType(gistData[1]));
  }

  public static File fileFromGit(String remoteURI, String fileInRepo)
      throws InvalidRemoteException, TransportException, GitAPIException, IOException {
    return fileFromGit(remoteURI, ScriptingEngine.getFullBranch(remoteURI), fileInRepo);
  }

  // git@github.com:CommonWealthRobotics/BowlerStudioVitamins.git
  // or
  // https://github.com/CommonWealthRobotics/BowlerStudioVitamins.git
  public static File fileFromGit(String remoteURI, String branch, String fileInRepo)
      throws InvalidRemoteException, TransportException, GitAPIException, IOException {
    File gitRepoFile = cloneRepo(remoteURI, branch);
    String id = gitRepoFile.getAbsolutePath();
    if (fileLastLoaded.get(id) == null) {
      // forces the first time the files is accessed by the application
      // tou pull an update
      fileLastLoaded.put(id, System.currentTimeMillis() - TIME_TO_WAIT_BETWEEN_GIT_PULL * 2);
    }
    long lastTime = fileLastLoaded.get(id);
    if ((System.currentTimeMillis() - lastTime) > TIME_TO_WAIT_BETWEEN_GIT_PULL
        || !gitRepoFile.exists())// wait
    // 2
    // seconds
    // before
    // re-downloading
    // the
    // file
    {
      // System.out.println("Updating git repo, its been
      // "+(System.currentTimeMillis()-lastTime)+
      // " need to wait "+ TIME_TO_WAIT_BETWEEN_GIT_PULL);
      fileLastLoaded.put(id, System.currentTimeMillis());
      if (isAutoupdate()) {
        // System.out.println("Autoupdating " +id);
        try {
          
          Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile() + "/.git");
          // https://gist.github.com/0e6454891a3b3f7c8f28.git
          
          try {
            pull(remoteURI,branch);
          } catch (Exception ex) {
            try {
              // Files.delete(gitRepoFile.toPath());
              ex.printStackTrace();
              System.err.println("Error in gist, hosing: " + gitRepoFile);
              deleteFolder(gitRepoFile);
            } catch (Exception x) {
              x.printStackTrace();
            }
          }
        } catch (NullPointerException ex) {
          setAutoupdate(false);
        }

      }

    }

    return new File(gitRepoFile.getAbsolutePath() + "/" + fileInRepo);
  }

  public static File uriToFile(String remoteURI) {
    // new Exception().printStackTrace();
    String[] colinSplit = remoteURI.split(":");

    String gitSplit = colinSplit[1].substring(0, colinSplit[1].lastIndexOf('.'));

    File gistDir = new File(getWorkspace().getAbsolutePath() + "/gitcache/" + gitSplit + "/.git");
    return gistDir;
  }

  public static String getBranch(String remoteURI) throws IOException {

    File gitRepoFile = uriToFile(remoteURI);
    if (!gitRepoFile.exists()) {
      gitRepoFile = cloneRepo(remoteURI, null);
    }

    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
    String branch = localRepo.getBranch();
    localRepo.close();

    return branch;
  }

  public static String getFullBranch(String remoteURI) throws IOException {

    File gitRepoFile = uriToFile(remoteURI);
    if (!gitRepoFile.exists()) {
      gitRepoFile = cloneRepo(remoteURI, null);
    }

    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
    String branch = localRepo.getFullBranch();
    localRepo.close();

    return branch;
  }

  public static void deleteBranch(String remoteURI, String toDelete) throws Exception {
    boolean found = false;
    for (String s : listBranchNames(remoteURI)) {
      if (s.contains(toDelete)) {
        found = true;
      }
    }
    if (!found)
      throw new RuntimeException(toDelete + " can not be deleted because it does not exist");

    File gitRepoFile = uriToFile(remoteURI);
    if (!gitRepoFile.exists()) {
      gitRepoFile = cloneRepo(remoteURI, null);
    }

    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
    // CreateBranchCommand bcc = null;
    // CheckoutCommand checkout;
    // String source = getFullBranch(remoteURI);

    Git git;

    git = new Git(localRepo);
    if (!toDelete.contains("heads")) {
      toDelete = "heads/" + toDelete;
    }
    if (!toDelete.contains("refs")) {
      toDelete = "refs/" + toDelete;
    }
    Exception ex = null;
    try {
      // delete branch 'branchToDelete' locally
      git.branchDelete().setBranchNames(toDelete).call();

      // delete branch 'branchToDelete' on remote 'origin'
      RefSpec refSpec = new RefSpec().setSource(null).setDestination(toDelete);
      git.push().setRefSpecs(refSpec).setRemote("origin").setCredentialsProvider(PasswordManager.getCredentialProvider()).call();
    } catch (Exception e) {
      ex = e;
    }
    git.close();
    if (ex != null)
      throw ex;
  }

  public static void newBranch(String remoteURI, String newBranch) throws Exception {
    for (String s : listBranchNames(remoteURI)) {
      if (s.contains(newBranch)) {
        throw new RuntimeException(
            newBranch + " can not be created because " + s + " is too similar");
      }
    }

    File gitRepoFile = uriToFile(remoteURI);
    if (!gitRepoFile.exists()) {
      gitRepoFile = cloneRepo(remoteURI, null);
    }

    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
    String source = getFullBranch(remoteURI);

    Git git;

    git = new Git(localRepo);

    newBranchLocal(newBranch, source, git);

    git.close();

  }

	private static void newBranchLocal(String newBranch, String remoteURI, Git git)
			throws GitAPIException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException,
			CheckoutConflictException, InvalidRemoteException, TransportException, IOException {
		 String source = getFullBranch(remoteURI);

	    try {
	    	CreateBranchCommand setName = git.branchCreate().setName(newBranch);
			CreateBranchCommand setStartPoint = setName.setStartPoint(source);
			CreateBranchCommand setForce = setStartPoint.setForce(true);
			setForce.call();
	
	    }catch(org.eclipse.jgit.api.errors.RefNotFoundException ex){
	    	git.branchCreate().setName(newBranch).call();

	    }

	    PushCommand pushCommand = git.push();
	    PushCommand setRemote = pushCommand.setRemote("origin");
		PushCommand setRefSpecs = setRemote.setRefSpecs(new RefSpec(newBranch + ":" + newBranch));
		PushCommand setCredentialsProvider = setRefSpecs
	        .setCredentialsProvider(PasswordManager.getCredentialProvider());
		setCredentialsProvider.call();
		
		CheckoutCommand checkout;
	    checkout = git.checkout();
	    checkout.setName(newBranch);
	    checkout.call();
	}
  
  
  private static boolean hasAtLeastOneReference(Git git) throws Exception {
    Repository repo = git.getRepository();
    Config storedConfig = repo.getConfig();
    Set<String> uriList = repo.getConfig().getSubsections("remote");
    String remoteURI = null;
    for (String remoteName : uriList) {
      if (remoteURI == null)
        remoteURI = storedConfig.getString("remote", remoteName, "url");;
    }
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() < (startTime + 2000)) {
      for (Ref ref : repo.getAllRefs().values()) {
        if (ref.getObjectId() != null) {
          List<Ref> branchList = listBranches(remoteURI, git);
          if (branchList.size() > 0) {
            // System.out.println("Found "+branchList.size()+"
            // branches");
            return true;
          }
        }
      }
    }
    
    return true;
  }

  public static List<Ref> listBranches(String remoteURI) throws Exception {

    File gitRepoFile = uriToFile(remoteURI);
    if (!gitRepoFile.exists()) {
      gitRepoFile = cloneRepo(remoteURI, null);
      return listBranches(remoteURI);
    }

    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
    // https://gist.github.com/0e6454891a3b3f7c8f28.git
    List<Ref> Ret = new ArrayList<>();
    Git git = new Git(localRepo);
    Ret = listBranches(remoteURI, git);
    git.close();
    return Ret;
  }

  public static List<Ref> listBranches(String remoteURI, Git git) throws Exception {

    // https://gist.github.com/0e6454891a3b3f7c8f28.git
    // System.out.println("Listing references from: "+remoteURI);
    // System.out.println(" branch: "+getFullBranch(remoteURI));
    List<Ref> list = git.branchList().setListMode(ListMode.ALL).call();
    // System.out.println(" size : "+list.size());
    return list;
  }

  public static List<Ref> listLocalBranches(String remoteURI) throws IOException {

    File gitRepoFile = uriToFile(remoteURI);
    if (!gitRepoFile.exists()) {
      gitRepoFile = cloneRepo(remoteURI, null);
    }

    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
    // https://gist.github.com/0e6454891a3b3f7c8f28.git
    Git git = new Git(localRepo);
    try {
      List<Ref> list = git.branchList().call();
      git.close();
      return list;
    } catch (Exception ex) {

    }
    git.close();
    return new ArrayList<>();
  }

  public static List<String> listLocalBranchNames(String remoteURI) throws Exception {
    ArrayList<String> branchNames = new ArrayList<>();

    List<Ref> list = listLocalBranches(remoteURI);
    for (Ref ref : list) {
      // System.out.println("Branch: " + ref + " " + ref.getName() + " " +
      // ref.getObjectId().getName());
      branchNames.add(ref.getName());
    }
    return branchNames;
  }

  public static List<String> listBranchNames(String remoteURI) throws Exception {
    ArrayList<String> branchNames = new ArrayList<>();

    List<Ref> list = listBranches(remoteURI);
    for (Ref ref : list) {
      // System.out.println("Branch: " + ref + " " + ref.getName() + " " +
      // ref.getObjectId().getName());
      branchNames.add(ref.getName());
    }
    return branchNames;
  }

  public static void pull(String remoteURI, String branch) throws IOException {
      //new Exception().printStackTrace();

	  if(!hasNetwork())
		  return;
		File gitRepoFile = uriToFile(remoteURI);
		if (!gitRepoFile.exists()) {
			gitRepoFile = cloneRepo(remoteURI, branch);
		}

		Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
		Git git = new Git(localRepo);
		String ref = git.getRepository().getConfig().getString("remote", "origin", "url");
		// config.setString("branch", "master", "merge", "refs/heads/master");

		try {
			System.out.print("Pulling " + ref+"  ");
			if (ref.startsWith("git@")) {
				git.pull().setTransportConfigCallback(transportConfigCallback).call();
			}else {
				git.pull().setCredentialsProvider(PasswordManager.getCredentialProvider()).call();
			}
			System.out.println(" ... Success!" );
		} catch (CheckoutConflictException ex) {
			for(String p: ex.getConflictingPaths()) {
				File conf = new File(gitRepoFile.getParent()+"/"+p);
				System.out.println("\r\n\r\n\tConflict: "+conf+"\r\n\r\n");
				System.out.println("Using upstream and deleting local changes");
			}
			git.close();
			deleteFolder(new File(gitRepoFile.getParent()));
			pull( remoteURI,  branch);
		} catch (WrongRepositoryStateException e) {
			deleteRepo(remoteURI);
			pull( remoteURI,  branch);
		} catch (InvalidConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (DetachedHeadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CanceledException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RefNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RefNotAdvertisedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoHeadException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransportException e) {
			PasswordManager.checkInternet();
			
			if (git.getRepository().getConfig().getString("remote", "origin", "url").startsWith("git@")) {
				try {
					git.pull().setTransportConfigCallback(transportConfigCallback).call();
				}catch(Exception ex) {
					ex.printStackTrace();
				}
			}else {
				e.printStackTrace();
			}
			
			
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		git.close();
  }
  public static void pull(String remoteURI) throws IOException {
	     if (!hasNetwork())
		        return;// No login info means there is no way to publish
    pull(remoteURI, getBranch(remoteURI));
    
  }
  public static void checkoutCommit(String remoteURI, String branch, String commitHash)
      throws IOException {
    File gitRepoFile = ScriptingEngine.uriToFile(remoteURI);
    if (!gitRepoFile.exists() || !gitRepoFile.getAbsolutePath().endsWith(".git")) {
      System.err.println("Invailid git file!" + gitRepoFile.getAbsolutePath());
      throw new RuntimeException("Invailid git file!" + gitRepoFile.getAbsolutePath());
    }
    Repository localRepo = new FileRepository(gitRepoFile);
    Git git = new Git(localRepo);
    try {
      git.checkout().setName(commitHash).call();
      git.checkout().setCreateBranch(true).setName(branch).setStartPoint(commitHash).call();

    } catch (Exception ex) {
      ex.printStackTrace();
    }

    git.close();

  }

  public static void checkout(String remoteURI, String branch) throws IOException {
    // cloneRepo(remoteURI, branch);
    File gitRepoFile = uriToFile(remoteURI);
    if (!gitRepoFile.exists() || !gitRepoFile.getAbsolutePath().endsWith(".git")) {
      System.err.println("Invailid git file!" + gitRepoFile.getAbsolutePath());
      throw new RuntimeException("Invailid git file!" + gitRepoFile.getAbsolutePath());
    }

    String currentBranch = getFullBranch(remoteURI);
    if (currentBranch != null) {
      // String currentBranch=getFullBranch(remoteURI);
      Repository localRepo = new FileRepository(gitRepoFile);
      // if (!branch.contains("heads")) {
      // branch = "heads/" + branch;
      // }
      // if (!branch.contains("refs")) {
      // branch = "refs/" + branch;
      // }
      // System.out.println("Checking out "+branch+" :
      // "+gitRepoFile.getAbsolutePath() );
     // Git git = new Git(localRepo);
      // StoredConfig config = git.getRepository().getConfig();
      // config.setString("branch", "master", "merge", "refs/heads/master");
      if (!currentBranch.contains(branch)) {
    	pull(remoteURI,branch);
    	Git git = new Git(localRepo);
        try {
          git.branchCreate().setForce(true).setName(branch).setStartPoint("origin/" + branch)
              .call();
          git.checkout().setName(branch).call();
        } catch (Exception ex) {
          ex.printStackTrace();
        }
        git.close();
      }
      
    }

  }

  // public static void checkout(String branch, File gitRepoFile) throws
  // Exception {
  // String currentBranch=getFullBranch(gitRepoFile);
  // Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile() +
  // "/.git");
  //
  // }

  /**
   * This function retrieves the local cached version of a given git repository. If it does not
   * exist, it clones it.
   *
   * @return The local directory containing the .git
   */
  public static File cloneRepo(String remoteURI, String branch) {


    // new Exception().printStackTrace();
    String[] colinSplit = remoteURI.split(":");

    String gitSplit = colinSplit[1].substring(0, colinSplit[1].lastIndexOf('.'));

    File gistDir = new File(getWorkspace().getAbsolutePath() + "/gitcache/" + gitSplit);
    if (!gistDir.exists()) {
      gistDir.mkdir();
    }
    String localPath = gistDir.getAbsolutePath();
    File gitRepoFile = new File(localPath + "/.git");
    File dir = new File(localPath);

    if (!gitRepoFile.exists()) {
	   if (!hasNetwork())
		        return null;// No login info means there is no way to publish
      System.out.println("Cloning files from: " + remoteURI);
      if (branch != null)
        System.out.println("            branch: " + branch);
      System.out.println("                to: " + localPath);

      for (int i = 0; i < 5; i++) {
        // Clone the repo
        try {
          if (branch == null) {
            Git git = Git.cloneRepository().setURI(remoteURI).setDirectory(dir)
                .setCredentialsProvider(PasswordManager.getCredentialProvider()).call();
            hasAtLeastOneReference(git);
            branch = getFullBranch(remoteURI);
            checkout(remoteURI, branch);
            hasAtLeastOneReference(git);
            git.close();

          } else {
            Git git = Git.cloneRepository().setURI(remoteURI).setBranch(branch).setDirectory(dir)
                .setCredentialsProvider(PasswordManager.getCredentialProvider()).call();
            hasAtLeastOneReference(git);
            checkout(remoteURI, branch);
            hasAtLeastOneReference(git);
            git.close();

          }

          break;
        } catch (Exception e) {
          Log.error("Failed to clone " + remoteURI + " " + e);
          e.printStackTrace();
          deleteFolder(new File(localPath));
        }
        ThreadUtil.wait(200 * i);
      }
    }
    if (branch != null) {
      try {
        checkout(remoteURI, branch);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return gistDir;

  }

  public static Git locateGit(File f) throws IOException {
    File gitRepoFile = f;
    while (gitRepoFile != null) {
      gitRepoFile = gitRepoFile.getParentFile();
      if (new File(gitRepoFile.getAbsolutePath() + "/.git").exists()) {
        // System.err.println("Fount git repo for file: "+gitRepoFile);
        Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile() + "/.git");
        return new Git(localRepo);

      }
    }

    return null;
  }

  public static String getText(URL website) throws Exception {

    URLConnection connection = website.openConnection();
    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

    StringBuilder response = new StringBuilder();
    String inputLine;

    while ((inputLine = in.readLine()) != null)
      response.append(inputLine + "\n");

    in.close();

    return response.toString();
  }

  public static File getLastFile() {
    if (lastFile == null)
      return getWorkspace();
    return lastFile;
  }

  public static void setLastFile(File lastFile) {
    ScriptingEngine.lastFile = lastFile;
  }


  public static File getFileEngineRunByName(String filename) {
    return filesRun.get(filename);
  }


  public static String[] getImports() {
    return imports;
  }

  public static IGitHubLoginManager getLoginManager() {
    return PasswordManager.getLoginManager();
  }

  public static void setLoginManager(IGitHubLoginManager lm) {
    PasswordManager.setLoginManager(lm);
  }

  public static boolean isAutoupdate() {
    return autoupdate;
  }

  public static boolean setAutoupdate(boolean autoupdate) throws IOException {
		if (autoupdate && !ScriptingEngine.autoupdate) {
			ScriptingEngine.autoupdate = true;// prevents recoursion loop from
			//PasswordManager.setAutoupdate(autoupdate);
		}
		ScriptingEngine.autoupdate = autoupdate;
		return ScriptingEngine.autoupdate;
  }

  private static File fileFromGistID(String string, String string2)
      throws InvalidRemoteException, TransportException, GitAPIException, IOException {
    // TODO Auto-generated method stub
    return fileFromGit("https://gist.github.com/" + string + ".git", string2);
  }

  public static String findLocalPath(File currentFile, Git git) {
    File dir = git.getRepository().getDirectory().getParentFile();

    return dir.toURI().relativize(currentFile.toURI()).getPath();
  }

  public static String findLocalPath(File currentFile) {
    Git git;
    try {
      git = locateGit(currentFile);
      return findLocalPath(currentFile, git);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    return currentFile.getName();

  }

  public static String[] findGitTagFromFile(File currentFile) throws IOException {

    Git git = locateGit(currentFile);

    return new String[] {git.getRepository().getConfig().getString("remote", "origin", "url"),
        findLocalPath(currentFile, git)};
  }

	public static boolean checkOwner(File currentFile) {
		Git git;
		try {
			git = locateGit(currentFile);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return false;
		}

		try {
			waitForLogin();
			git.pull().setCredentialsProvider(PasswordManager.getCredentialProvider()).call();// updates to the
			// latest version
			git.push().setCredentialsProvider(PasswordManager.getCredentialProvider()).call();
			git.close();
			return true;
		} catch (Exception e) {
			if (git.getRepository().getConfig().getString("remote", "origin", "url").startsWith("git@")) {
				try {
					git.pull().setTransportConfigCallback(transportConfigCallback).call();// updates to the
					// latest version
					git.push().setTransportConfigCallback(transportConfigCallback).call();
					git.close();
					return true;
				} catch (Exception ex) {
					// just return false, the exception is it failing to push
					ex.printStackTrace();
				}
			}
		}
		git.close();
		return false;
  }

  public static GHGist fork(String currentGist) throws Exception {

    if (PasswordManager.getGithub() != null) {

      waitForLogin();
      GHGist incoming = PasswordManager.getGithub().getGist(currentGist);
      for (IGithubLoginListener l : loginListeners) {
        l.onLogin(PasswordManager.getUsername());
      }
      return incoming.fork();

    }

    return null;
  }

  public static String[] forkGitFile(String[] incoming) throws Exception {
    GitHub github = PasswordManager.getGithub();

    String id = null;
    if (incoming[0].endsWith(".git"))
      id = urlToGist(incoming[0]);
    else {
      id = incoming[0];
      incoming[0] = "https://gist.github.com/" + id + ".git";
    }
    GHGist incomingGist = github.getGist(id);
    File incomingFile = ScriptingEngine.fileFromGistID(id, incoming[1]);
    if (!ScriptingEngine.checkOwner(incomingFile)) {
      incomingGist = incomingGist.fork();
      incoming[0] = "https://gist.github.com/"
          + ScriptingEngine.urlToGist(incomingGist.getHtmlUrl()) + ".git";
      // sync the new file to the disk
      incomingFile = ScriptingEngine.fileFromGistID(id, incoming[1]);
    }
    for (IGithubLoginListener l : loginListeners) {
      l.onLogin(PasswordManager.getUsername());
    }

    return incoming;
  }

  public static String urlToString(URL htmlUrl) {
		// TODO Auto-generated method stub
		return htmlUrl.toExternalForm();
	}


public static String urlToGist(URL htmlUrl) {
	// TODO Auto-generated method stub
	String externalForm = urlToString( htmlUrl) ;
	System.out.println(externalForm);
	return ScriptingEngine.urlToGist(externalForm);
}

//  public static void setGithub(GitHub github) {
//    ScriptingEngine.github = github;
//    if (github == null)
//      setLoginSuccess(false);
//  }

  public static List<String> getAllLangauges() {
    ArrayList<String> langs = new ArrayList<>();
    for (String L : getLangaugesMap().keySet()) {
      langs.add(L);
    }
    return langs;
  }

  // private static ArrayList<IScriptingLanguage> getLangauges() {
  // ArrayList<IScriptingLanguage> langs = new ArrayList<>();
  // for (String L : getLangaugesMap().keySet()) {
  // langs.add(getLangaugesMap().get(L));
  // }
  // return langs;
  // }

  public static HashMap<String, IScriptingLanguage> getLangaugesMap() {
    return langauges;
  }

  public static boolean hasNetwork() {

    return PasswordManager.hasNetwork();
  }

  public static boolean isLoginSuccess() {
    return PasswordManager.loggedIn();
  }


  public static String [] copyGitFile(String sourceGit, String targetGit, String filename){
    String targetFilename = filename;
    String[] WalkingEngine;
    if(targetGit.contains("gist.github.com")&& filename.contains("/")) {
    	String[] parts = filename.split("/");
    	targetFilename =parts[parts.length-1];
    }
    try {
        WalkingEngine = ScriptingEngine.codeFromGit(sourceGit, filename);
        try {
            if( null==ScriptingEngine.fileFromGit(targetGit, targetFilename)){
                
                ScriptingEngine.createFile(targetGit, targetFilename, "copy file");
                while (true) {
                    try {
                        ScriptingEngine.fileFromGit(targetGit, targetFilename);
                        break;
                    } catch (Exception e) {

                    }
                    ThreadUtil.wait(500);
                    //Log.warn(targetGit +"/"+filename+ " not built yet");
                }
                
            }
        } catch (InvalidRemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TransportException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (GitAPIException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String[] newFileCode;
        try {
            newFileCode = ScriptingEngine.codeFromGit(targetGit, targetFilename);
            if(newFileCode==null)
                newFileCode=new String[]{""};
            if(!WalkingEngine[0].contentEquals(newFileCode[0])){
                System.out.println("Copy Content to "+targetGit+"/"+targetFilename);
                ScriptingEngine.pushCodeToGit(targetGit, ScriptingEngine.getFullBranch(targetGit), targetFilename, WalkingEngine[0], "copy file content");
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    } catch (Exception e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
    }
    
    
    return new String[]{targetGit,targetFilename};
}

}
