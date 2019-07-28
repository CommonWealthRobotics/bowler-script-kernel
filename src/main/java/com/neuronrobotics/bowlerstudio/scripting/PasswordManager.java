package com.neuronrobotics.bowlerstudio.scripting;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.file.Files;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GitHub;

import com.neuronrobotics.sdk.util.ThreadUtil;


public class PasswordManager {
	private static IGitHubLoginManager loginManager = new IGitHubLoginManager() {

		  @Override
		  public String[] prompt(String username) {
		    //new RuntimeException("Login required").printStackTrace();

		    if (username != null) {
		      if (username.equals(""))
		        username = null;
		    }
		    String[] creds = new String[] {"", ""};
		    System.out.println("#Github Login Prompt#");
		    System.out.println("For anynomous mode hit enter twice");
		    System.out.print("Github Username: " + (username != null ? "(" + username + ")" : ""));
		    // create a scanner so we can read the command-line input
		    BufferedReader buf = new BufferedReader(new InputStreamReader(System.in));

		    do {
		      try {
		        creds[0] = buf.readLine();
		        System.out.println("GitHub Username: "+ creds[0]);
		        
		      } catch (IOException e) {
		      	e.printStackTrace();
		        return null;
		      }
		      if (creds[0].equals("") && (username == null)) {
		        System.out.println("No username, using anynomous login");
		        return null;
		      } 

		      try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		    } while (creds[0] == null);

		    //System.out.print("Github Password: ");
		    try {
		  	  Console cons;
		  	  char[] passwd;
		  	  if ((cons = System.console()) != null &&
		  	      (passwd = cons.readPassword("[%s]", "GitHub Password:")) != null) {
		  		  creds[1]=new String(passwd);
		  	      java.util.Arrays.fill(passwd, ' ');
		  	  }
		      //creds[1] = buf.readLine();
		      if (creds[1].equals("")) {
		        System.out.println("No password, using anynomous login");
		      }
		    } catch (Exception e) {
		      return null;
		    }
		    return creds;
		  }
		};
	private static String loginID = null;
	private static String pw = null;
	private static CredentialsProvider cp;// = new
	private static GitHub github;
	private static boolean hasnetwork;

	public static void save(String user, String passcleartext) {

	}

	static String getPassword() {
		return "";
	}

	public static GitHub getGithub() {
		return github;
	}
	public static void setGithub(GitHub g) {
		 github =g;
	}
	public static String getUsername() {
		return null;
	}

	public static void login() throws IOException {
		if (!hasnetwork)
			return;
		loginID = null;
		String[] creds = loginManager.prompt(PasswordManager.getUsername());
		loginID=creds[0];
		pw=creds[1];
		try {
			waitForLogin();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * The GistID we are waiting to see
	 */
	public static void waitForLogin() throws Exception {
		try {
			final URL url = new URL("http://github.com");
			final URLConnection conn = url.openConnection();
			conn.connect();
			conn.getInputStream();
			hasnetwork = true;
		} catch (Exception e) {
			// we assuming we have no access to the server and run off of the
			// chached gists.
			hasnetwork = false;
		}
		if (!hasnetwork)
			return;
		if (getGithub() == null) {

			
			try {
				setGithub(GitHub.connectUsingPassword(loginID, pw));
			} catch (IOException ex) {
				logout();
			}
			
			if (getGithub() == null) {

				login();
			}

		}

		try {
			if (getGithub().getRateLimit().remaining < 2) {
				System.err.println("##Github Is Rate Limiting You## Disabling autoupdate");
				setAutoupdate(false);
			}
		} catch (IOException e) {
			logout();

		}

		loadLoginData();

	}

	public static void logout() throws IOException {

		setGithub(null);

		loginID = null;

	}

	public static GitHub setupAnyonmous() throws IOException {
		System.err.println("Using anynomous login, autoupdate disabled");
		ScriptingEngine.setAutoupdate(false);
		logout();
		setGithub(GitHub.connectAnonymously());
		return getGithub();
	}

	public static IGitHubLoginManager getLoginManager() {
		return loginManager;
	}

	public static void setLoginManager(IGitHubLoginManager lm) {
		loginManager = lm;
	}

	static void loadLoginData() throws IOException {
		if (loginID == null) {

			try {
				
				
				if (pw != null && loginID != null) {
					// password loaded, we can now autoupdate
					ScriptingEngine.setAutoupdate(true);
					if (hasnetwork)
						if (getCredentialProvider() == null) {
							setCredentialProvider(new UsernamePasswordCredentialsProvider(loginID, pw));
						}
				}
			} catch (Exception e) {
				logout();
				// e.printStackTrace();
			}
		}

	}

	public static void setAutoupdate(boolean autoupdate) throws IOException {
		if (autoupdate) {
			// calling loadLoginData
			PasswordManager.loadLoginData();
			if (pw == null || PasswordManager.getUsername() == null)
				login();

			if (pw == null || PasswordManager.getUsername() == null)
				return;
		}

	}

	public static CredentialsProvider getCredentialProvider() {
		return cp;
	}

	private static void setCredentialProvider(CredentialsProvider cp) {
		PasswordManager.cp = cp;
	}
}
