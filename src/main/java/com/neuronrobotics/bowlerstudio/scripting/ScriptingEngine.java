package com.neuronrobotics.bowlerstudio.scripting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHGist;
import org.kohsuke.github.GitHub;

import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.util.ThreadUtil;

import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;


public class ScriptingEngine {// this subclasses boarder pane for the widgets sake, because multiple inheritance is TOO hard for java...
	private static final int TIME_TO_WAIT_BETWEEN_GIT_PULL = 10000;
	/**
	 * 
	 */
	private static final Map<String,Long> fileLastLoaded = new HashMap<String,Long>();

	private static boolean hasnetwork=false;
	private static boolean autoupdate=false;
	
	private static final String[] imports = new String[] { //"haar",
			"java.awt",
			"eu.mihosoft.vrl.v3d",
			"eu.mihosoft.vrl.v3d.samples",
			"com.neuronrobotics.sdk.addons.kinematics.xml",
			"com.neuronrobotics.sdk.dyio.peripherals",
			"com.neuronrobotics.sdk.dyio",
			"com.neuronrobotics.sdk.common",
			"com.neuronrobotics.sdk.ui",
			"com.neuronrobotics.sdk.util",
			"com.neuronrobotics.sdk.serial",
			"javafx.scene.control",
			"com.neuronrobotics.bowlerstudio.scripting",
			"com.neuronrobotics.sdk.config",
			"com.neuronrobotics.bowlerstudio",
			"com.neuronrobotics.imageprovider",
			"com.neuronrobotics.bowlerstudio.tabs",
			"javafx.scene.text", "javafx.scene",
			"com.neuronrobotics.sdk.addons.kinematics",
			"com.neuronrobotics.sdk.addons.kinematics.math", "java.util",
			"com.neuronrobotics.sdk.addons.kinematics.gui",
			"javafx.scene.transform", "javafx.scene.shape",
			"java.awt.image.BufferedImage",
			"com.neuronrobotics.bowlerstudio.vitamins.Vitamins"};

	private static GitHub github;

	private static File creds=null;

	//private static GHGist gist;

	
	private static File workspace;
	private static File lastFile;
	private static String loginID=null;
	private static String pw =null;
	private static CredentialsProvider cp;// = new UsernamePasswordCredentialsProvider(name, password);
	private static ArrayList<IGithubLoginListener> loginListeners = new ArrayList<IGithubLoginListener>();

	private static ArrayList<IScriptingLanguage> langauges=new ArrayList<>();
	
	private static IGitHubLoginManager loginManager= new IGitHubLoginManager() {
		
		@Override
		public String[] prompt(String username) {
			if(username!=null){
				if(username.equals(""))
					username=null;
			}
			String []creds = new String[]{"",""};
			System.out.println("#Github Login Prompt#");
			System.out.println("For anynomous mode hit enter twice");
			System.out.print("Github Username: "+username!=null?"("+username+")":"");
			// create a scanner so we can read the command-line input
			BufferedReader buf = new BufferedReader (new InputStreamReader (System.in));

		   do{
			   try {
				creds[0] = buf.readLine ();
			} catch (IOException e) {
				return null;
			}
			   if(creds[0].equals("")&& (username==null)){
				   System.out.println("No username, using anynomous login");
				   return null;
			   }else
				   creds[0]=username;
		   }while(creds[0]==null);
		    
		    System.out.print("Github Password: ");
		    try {
				creds[1] = buf.readLine ();
				 if(creds[1].equals("")){
					   System.out.println("No password, using anynomous login");
					   return null;
				   }
			} catch (IOException e) {
				return null;
			}
			return creds;
		}
	};
 	static{
 		
		try {                                                                                                                                                                                                                                 
	        final URL url = new URL("http://github.com");                                                                                                                                                                                 
	        final URLConnection conn = url.openConnection();                                                                                                                                                                                  
	        conn.connect();    
	        conn.getInputStream();                                                                                                                                                                                                               
	        hasnetwork= true;                                                                                                                                                                                                                      
	    } catch (Exception e) {                                                                                                                                                                                                             
	        // we assuming we have no access to the server and run off of the chached gists.    
	    	hasnetwork= false;                                                                                                                                                                                                                              
	    }  
		workspace = new File(System.getProperty("user.home")+"/bowler-workspace/");
		if(!workspace.exists()){
			workspace.mkdir();
		}

		loadLoginData();
		addScriptingLanguage(new ClojureHelper());
		addScriptingLanguage(new GroovyHelper());
		addScriptingLanguage(new JythonHelper());

	}
 	
 	private static void loadLoginData(){
 		if(loginID == null && getCreds().exists() && hasnetwork){
			try {
				String line;
			
			    InputStream fis = new FileInputStream(getCreds().getAbsolutePath());
			    InputStreamReader isr = new InputStreamReader(fis, Charset.forName("UTF-8"));
			    @SuppressWarnings("resource")
				BufferedReader br = new BufferedReader(isr);
			
			    while ((line = br.readLine()) != null) {
			        if(line.contains("login")){
			        	loginID = line.split("=")[1];
			        }
			        if(line.contains("password")){
			        	pw = line.split("=")[1];
			        	// password loaded, we can now autoupdate
			        	ScriptingEngine.setAutoupdate(true);
			        }
			    }
				
				
			} catch (Exception e) {
				logout();
				//e.printStackTrace();
			}
		}
 		
 	}
 	
 	
 	public static void addScriptingLanguage(IScriptingLanguage lang){
 		langauges.add(lang);
 	}
 	
 	public static void addIGithubLoginListener(IGithubLoginListener l){
 		if(!loginListeners.contains(l)){
 			loginListeners.add(l);
 		}
 	}
 	public static void removeIGithubLoginListener(IGithubLoginListener l){
 		if(loginListeners.contains(l)){
 			loginListeners.remove(l);
 		}
 	}
 	
	public static File getWorkspace() {
		//System.err.println("Workspace: "+workspace.getAbsolutePath());
		return workspace;
	}

	public static ShellType setFilename(String name) {
		for (IScriptingLanguage l:langauges){
			if(l.isSupportedFileExtenetion(name))
				return l.getShellType();
		}

		return ShellType.GROOVY;
	}
	
	public static String getLoginID(){
		

		return loginID;
	}
	
	public static void login() throws IOException{
		if(! hasnetwork)
			return;
		loginID=null;

		gitHubLogin();
        
	}

	public static void logout(){
		//new RuntimeException("Logout callsed").printStackTrace();
		if(getCreds()!= null)
		try {
			if(getCreds().exists())
				Files.delete(getCreds().toPath());
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		github=null;
        for(IGithubLoginListener l:loginListeners){
        	l.onLogout(loginID);
        }
        loginID=null;
	}
	
	private static GitHub setupAnyonmous(){
		System.err.println("Using anynomous login, autoupdate disabled");
		ScriptingEngine.setAutoupdate(false);
		logout();
		try {
			github=GitHub.connectAnonymously();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return github;
	}
	
	public static String urlToGist(String in) {
		String domain = in.split("//")[1];
		String[] tokens = domain.split("/");
		if (tokens[0].toLowerCase().contains("gist.github.com")
				&& tokens.length >= 2) {
			try{
				String id = tokens[2].split("#")[0];
				Log.debug("Gist URL Detected " + id);
				return id;
			}catch(ArrayIndexOutOfBoundsException e){
				return "d4312a0787456ec27a2a";
			}
		}

		return null;
	}

	private static String returnFirstGist(String html) {
		// Log.debug(html);
		String slug = html.split("//gist.github.com/")[1];
		String js = slug.split(".js")[0];
		String id = js.split("/")[1];

		return id;
	}

	public static String getCurrentGist(String addr, WebEngine engine) {
		String gist = urlToGist(addr);
		if (gist == null) {
			try {
				Log.debug("Non Gist URL Detected");
				String html;
				TransformerFactory tf = TransformerFactory.newInstance();
				Transformer t = tf.newTransformer();
				StringWriter sw = new StringWriter();
				t.transform(new DOMSource(engine.getDocument()),
						new StreamResult(sw));
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
		return gist;
	}
	
	public static GitHub gitHubLogin(){
		String[] creds = loginManager.prompt(loginID);
		   
		
		if(creds==null){
			return setupAnyonmous();
		}else{
			if(creds[0].contains("@")){
			   System.err.print("###ERROR Enter the Username not the Email Address### ");
			   return gitHubLogin();
		   }if(creds[0].equals("") || creds[1].equals("") ){
			   System.err.print("###No Username or password### ");
			   return setupAnyonmous();
		   }
		}
		
		loginID = creds[0];
		pw= creds[1];
        
        String content= "login="+loginID+"\n";
        content+= "password="+pw+"\n";
        PrintWriter out;
		try {
			out = new PrintWriter(getCreds().getAbsoluteFile());
	        out.println(content);
	        out.flush();
	        out.close();
	   	 	github = GitHub.connect();
	   	
	   	 	if(github.isCredentialValid()){
		        for(IGithubLoginListener l:loginListeners){
		        	l.onLogin(loginID);
		        }
		        ScriptingEngine.setAutoupdate(true);
		        System.out.println("Success Login as "+loginID+"");
	   	 	}else{
	   	 		System.err.println("Bad login credentials for "+loginID);
	   	 		github=null;
				pw= null;
	   	 	}
	   	 		
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Login failed");
			github=null;
		}
		if(github==null){
			ThreadUtil.wait(200);
			return gitHubLogin();
		}
		else
			return github;
	}
	
	

	private static void waitForLogin(String id) throws IOException, InvalidRemoteException, TransportException, GitAPIException{
		try {                                                                                                                                                                                                                                 
	        final URL url = new URL("http://github.com");                                                                                                                                                                                 
	        final URLConnection conn = url.openConnection();                                                                                                                                                                                  
	        conn.connect();   
	        conn.getInputStream();
	        hasnetwork= true;                                                                                                                                                                                                                      
	    } catch (Exception e) {                                                                                                                                                                                                             
	        // we assuming we have no access to the server and run off of the chached gists.    
	    	hasnetwork= false;                                                                                                                                                                                                                              
	    }  
		if(!hasnetwork)
			return;
		if(github == null){

			if (getCreds().exists()){
				try{
					github = GitHub.connect();
				}catch(IOException ex){
					logout();
				}
			}else{
				getCreds().createNewFile();
			}
			
			if(github==null){
				
				login();	
			}
			
		}
		
		try{
			if(github.getRateLimit().remaining<2){
				System.err.println("##Github Is Rate Limiting You## Disabling autoupdate");
				setAutoupdate(false);
			}
		}catch(IOException e){
			logout();
			
		}
			
		
		loadLoginData();


	    for(int i=0;i<2;i++){
			File gistDir=new File(getWorkspace().getAbsolutePath()+"/gistcache/"+id);
			if(!gistDir.exists()){
				gistDir.mkdir();
			}
			String localPath=gistDir.getAbsolutePath();
			File gitRepoFile = new File(localPath + "/.git");
			
			
			if(!gitRepoFile.exists()){
				if(cp == null){
					cp = new UsernamePasswordCredentialsProvider(loginID, pw);
				}
				GHGist gist;
				try{
					gist = github.getGist(id);
				}catch(IOException ex){
					ex.printStackTrace();
					return;
				}
				String remotePath = gist.getGitPullUrl();
				System.out.println("Cloning files to: "+localPath);
				System.out.println("Cloning files from: "+remotePath);
				 //Clone the repo
				try{
				    Git.cloneRepository()
				    .setURI(remotePath)
				    .setDirectory(new File(localPath))
				    .setCredentialsProvider(cp)
				    .call();
				}catch(Exception e){
					//e.printStackTrace();
					deleteFolder(new File(localPath ));
					remotePath="git@gist.github.com:"+id+".git";
					 Git.cloneRepository()
					    .setURI("git@gist.github.com:"+id+".git")
					    .setDirectory(new File(localPath))
					    .call();
				}
				
			}
			
		    if(!isAutoupdate())
		    	return;
		    //System.out.println("Autoupdating " +id);
			if(cp == null){
				cp = new UsernamePasswordCredentialsProvider(loginID, pw);
			}
		    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
		    //https://gist.github.com/0e6454891a3b3f7c8f28.git
		    Git git = new Git(localRepo);
		    try{
		    	PullResult ret = git.pull().setCredentialsProvider(cp).call();// updates to the latest version
		    	//System.out.println("Pull completed "+ret);
		    	//
		    	//git.push().setCredentialsProvider(cp).call();
		    	git.close();
		    	return;
		    }catch(Exception ex){
		    	try {
		    	    //Files.delete(gitRepoFile.toPath());
		    		ex.printStackTrace();
		    		System.err.println("Error in gist, hosing: "+gitRepoFile);
		    		deleteFolder(new File(localPath ));
		    	} catch (Exception x) {
		    		x.printStackTrace();
		    	} 
		    }
		    git.close();
	    }

	}
	
	private static void deleteFolder(File folder) {
	    File[] files = folder.listFiles();
	    if(files!=null) { //some JVMs return null for empty dirs
	        for(File f: files) {
	            if(f.isDirectory()) {
	                deleteFolder(f);
	            } else {
	                f.delete();
	            }
	        }
	    }
	    folder.delete();
	}

	public static ArrayList<String> filesInGist(String gistcode, String extnetion) {
		ArrayList<String> f=new ArrayList<>();
		try {
			
			waitForLogin(gistcode);
			File gistDir=new File(getWorkspace().getAbsolutePath()+"/gistcache/"+gistcode);
			for (final File fileEntry : gistDir.listFiles()) {
				if(!fileEntry.getName().endsWith(".git"))
					if(extnetion==null)
						f.add(fileEntry.getName());
					else if(fileEntry.getName().endsWith(extnetion))
						f.add(fileEntry.getName());
		    }
			return f;
		} catch (InterruptedIOException e) {
			System.out.println("Gist Rate limited, you realy should login to github");
		} catch (MalformedURLException ex) {
			// ex.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	
	public static ArrayList<String> filesInGist(String id){
		return filesInGist(id, null);
	}
	
	public static String getUserIdOfGist(String id){
		try {
			waitForLogin(id);
			Log.debug("Loading Gist: " + id);
			GHGist gist;
			try{
				gist = github.getGist(id);
				return gist.getOwner().getLogin();
			}catch(IOException ex){
				ex.printStackTrace();
			}
		} catch (IOException | GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
		return null;
		
	}
	
	public static void pushCodeToGistID(String id, String FileName, String content )  throws Exception{
		File gistDir=new File(getWorkspace().getAbsolutePath()+"/gistcache/"+id);
		File desired = new File(gistDir.getAbsoluteFile()+"/"+FileName);
		if(!gistDir.exists()){
			gistDir.mkdir();
		}
		FileUtils.writeStringToFile(desired, content);
		if(!hasnetwork){
			return;
		}
		try {	
			waitForLogin(id);	
			Log.debug("Loading Gist: " + id);
			GHGist gist;
			try{
				gist = github.getGist(id);
			}catch(IOException ex){
				//ex.printStackTrace();
				
				return;
			}

			String localPath=gistDir.getAbsolutePath();
			String remotePath = gist.getGitPullUrl();
			File gitRepoFile = new File(localPath + "/.git");
			if(!gitRepoFile.exists()){
				System.out.println("Cloning files to: "+localPath);
				 //Clone the repo
			    Git.cloneRepository().setURI(remotePath).setDirectory(new File(localPath)).call();
			}
		    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
		    Git git = new Git(localRepo);
		    try{
		    	git.pull().setCredentialsProvider(cp).call();// updates to the latest version
		    	if(!desired.exists()){
		    		desired.createNewFile();
		    		git.add().addFilepattern(FileName).call();
		    	}
		    	FileUtils.writeStringToFile(desired, content);
		    	git.commit().setAll(true).setMessage("Updates any changes").call();
		    	git.push().setCredentialsProvider(cp).call();
		    }catch(Exception ex){
		    	ex.printStackTrace();
		    }
		    git.close();
		} catch (InterruptedIOException e) {
			System.out.println("Gist Rate limited");
		} catch (MalformedURLException ex) {
			// ex.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ;
	}
	
	
	public static String[] codeFromGit(String id, String FileName)  throws Exception{
		try {	

		    File targetFile = fileFromGit(id,FileName);
			if(targetFile.exists()){
				//System.err.println("Loading file: "+targetFile.getAbsoluteFile());
				//Target file is ready to go
				 String text = new String(Files.readAllBytes(Paths.get(targetFile.getAbsolutePath())), StandardCharsets.UTF_8);
				 return new String[] { text, FileName , targetFile.getAbsolutePath()};
			}

		} catch (InterruptedIOException e) {
			System.out.println("Gist Rate limited");
		} catch (MalformedURLException ex) {
			// ex.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	public static String[] codeFromGistID(String id, String FileName)  throws Exception{
		try {	

		    File targetFile = fileFromGit("https://gist.github.com/"+id+".git",FileName);
			if(targetFile.exists()){
				//System.err.println("Loading file: "+targetFile.getAbsoluteFile());
				//Target file is ready to go
				 String text = new String(Files.readAllBytes(Paths.get(targetFile.getAbsolutePath())), StandardCharsets.UTF_8);
				 return new String[] { text, FileName , targetFile.getAbsolutePath()};
			}

		} catch (InterruptedIOException e) {
			System.out.println("Gist Rate limited");
		} catch (MalformedURLException ex) {
			// ex.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static Object inlineFileScriptRun(File f, ArrayList<Object> args) throws Exception{
		byte[] bytes;

		try {
			bytes = Files.readAllBytes(f.toPath());
			String s = new String(bytes, "UTF-8");
			return inlineScriptRun(s, args,setFilename(f.getName()) );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public static Object inlineGistScriptRun(String gistID, String Filename ,ArrayList<Object> args)  throws Exception{
		String[] gistData = codeFromGistID(gistID,Filename);
		return inlineScriptRun(gistData[0], args,setFilename(gistData[1]));
	}
	public static Object gitScriptRun(String gitURL, String Filename ,ArrayList<Object> args)  throws Exception{
		String[] gistData = codeFromGit(gitURL,Filename);
		return inlineScriptRun(gistData[0], args,setFilename(gistData[1]));
	}
	public static File fileFromGit(String remoteURI, String fileInRepo ) throws InvalidRemoteException, TransportException, GitAPIException, IOException{
		return fileFromGit(remoteURI,"master",fileInRepo);
	}
	//git@github.com:NeuronRobotics/BowlerStudioVitamins.git
	//or
	//https://github.com/NeuronRobotics/BowlerStudioVitamins.git
	public static File fileFromGit(String remoteURI,String branch, String fileInRepo ) throws InvalidRemoteException, TransportException, GitAPIException, IOException{
		String[] colinSplit =remoteURI.split(":");
		
		String gitSplit =colinSplit[1].substring(0, colinSplit[1].lastIndexOf('.'));
		
		File gistDir=new File(getWorkspace().getAbsolutePath()+"/gistcache/"+gitSplit);
		if(!gistDir.exists()){
			gistDir.mkdir();
		}
		String localPath=gistDir.getAbsolutePath();
		File gitRepoFile = new File(localPath + "/.git");
		
		
		if(!gitRepoFile.exists()){
			if(cp == null){
				cp = new UsernamePasswordCredentialsProvider(loginID, pw);
			}

			System.out.println("Cloning files to: "+localPath);
			System.out.println("Cloning files from: "+remoteURI);
			
			 //Clone the repo
		    Git.cloneRepository()
		    .setURI(remoteURI)
		    .setBranch(branch)
		    .setDirectory(new File(localPath))
		    .setCredentialsProvider(cp)
		    .call();
		}
		String id  = gitRepoFile.getAbsolutePath();
		if(fileLastLoaded.get(id) ==null ){
			// forces the first time the files is accessed by the application tou pull an update
			fileLastLoaded.put(id, System.currentTimeMillis()-TIME_TO_WAIT_BETWEEN_GIT_PULL*2);
		}
		long lastTime =fileLastLoaded.get(id);
		if((System.currentTimeMillis()-lastTime)>TIME_TO_WAIT_BETWEEN_GIT_PULL || !gistDir.exists())// wait 2 seconds before re-downloading the file
		{	
//			System.out.println("Updating git repo, its been "+(System.currentTimeMillis()-lastTime)+
//					" need to wait "+ TIME_TO_WAIT_BETWEEN_GIT_PULL);
			fileLastLoaded.put(id, System.currentTimeMillis());
		    if(isAutoupdate()){
			    //System.out.println("Autoupdating " +id);
				if(cp == null){
					cp = new UsernamePasswordCredentialsProvider(loginID, pw);
				}
			    Repository localRepo = new FileRepository(gitRepoFile.getAbsoluteFile());
			    //https://gist.github.com/0e6454891a3b3f7c8f28.git
			    Git git = new Git(localRepo);
			    try{
			    	PullResult ret = git.pull().setCredentialsProvider(cp).call();// updates to the latest version
			    	//System.out.println("Pull completed "+ret);
			    	//
			    	//git.push().setCredentialsProvider(cp).call();
			    	git.close();
			    }catch(Exception ex){
			    	try {
			    	    //Files.delete(gitRepoFile.toPath());
			    		ex.printStackTrace();
			    		System.err.println("Error in gist, hosing: "+gitRepoFile);
			    		deleteFolder(new File(localPath ));
			    	} catch (Exception x) {
			    		x.printStackTrace();
			    	} 
			    }
			    git.close();
		    }
			
		}
		
		return new File(gistDir+"/"+fileInRepo);
	}
	
	public static String getText(URL website) throws Exception {

        URLConnection connection = website.openConnection();
        BufferedReader in = new BufferedReader(
                                new InputStreamReader(
                                    connection.getInputStream()));

        StringBuilder response = new StringBuilder();
        String inputLine;

        while ((inputLine = in.readLine()) != null) 
            response.append(inputLine+"\n");

        in.close();

        return response.toString();
    }
	
	public static File getLastFile() {
		if(lastFile==null)
			return getWorkspace();
		return lastFile;
	}

	public static void setLastFile(File lastFile) {
		ScriptingEngine.lastFile = lastFile;
	}

	public static File getCreds() {
		if(creds == null)
			setCreds(new File(System.getProperty("user.home")+"/.github"));
		return creds;
	}

	public static void setCreds(File creds) {
		ScriptingEngine.creds = creds;
	}
	
	public static Object inlineScriptRun(String code, ArrayList<Object> args,ShellType activeType) {
		
		for (IScriptingLanguage l:langauges){
			if(l.getShellType() == activeType){
				return l.inlineScriptRun(code, args);
			}
		}
		return null;
	}

	public static String[] getImports() {
		return imports;
	}


	public static IGitHubLoginManager getLoginManager() {
		return loginManager;
	}


	public static void setLoginManager(IGitHubLoginManager loginManager) {
		ScriptingEngine.loginManager = loginManager;
	}


	public static boolean isAutoupdate() {
		return autoupdate;
	}


	public static boolean setAutoupdate(boolean autoupdate) {
		if(autoupdate){
			loadLoginData();
			if(pw==null||loginID==null)
				try {
					login();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			if(pw==null||loginID==null)
				return false;
		}
		ScriptingEngine.autoupdate = autoupdate;
		return ScriptingEngine.autoupdate;
	}



}
