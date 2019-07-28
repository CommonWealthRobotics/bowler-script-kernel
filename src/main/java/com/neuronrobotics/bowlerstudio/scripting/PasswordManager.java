package com.neuronrobotics.bowlerstudio.scripting;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GitHub;

public class PasswordManager {
	private static IGitHubLoginManager loginManager = new IGitHubLoginManager() {

		@Override
		public String[] prompt(String username) {
			// new RuntimeException("Login required").printStackTrace();

			if (username != null) {
				if (username.equals(""))
					username = null;
			}
			String[] creds = new String[] { "", "" };
			System.out.println("#Github Login Prompt#");
			System.out.println("For anynomous mode hit enter twice");
			System.out.print("Github Username: " + (username != null ? "(" + username + ")" : ""));
			// create a scanner so we can read the command-line input
			BufferedReader buf = new BufferedReader(new InputStreamReader(System.in));

			do {
				try {
					creds[0] = buf.readLine();
					System.out.println("GitHub Username: " + creds[0]);

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

			// System.out.print("Github Password: ");
			try {
				Console cons;
				char[] passwd;
				if ((cons = System.console()) != null
						&& (passwd = cons.readPassword("[%s]", "GitHub Password:")) != null) {
					creds[1] = new String(passwd);
					java.util.Arrays.fill(passwd, ' ');
				}
				// creds[1] = buf.readLine();
				if (creds[1].equals("")) {
					System.out.println("No password, using anynomous login");
				}
			} catch (Exception e) {
				return null;
			}
			return creds;
		}
	};
	static {

		checkInternet();
	}

	public static void checkInternet() {
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
	}
	private static File usernamefile=null;
	private static File passfile=null;
	private static File keyfile=null;
	private static File workspace=null;
	private static String loginID = null;
	private static String pw = null;
	private static CredentialsProvider cp;// = new
	private static GitHub github;
	private static boolean hasnetwork;
	private static boolean isLoggedIn = false;

	static String getPassword() {
		return pw;
	}

	public static GitHub getGithub() {
		return github;
	}

	public static void setGithub(GitHub g) {
		github = g;
	}

	public static String getUsername() {
		return getLoginID();
	}

	static synchronized void login() throws IOException {
		checkInternet();
		if (!hasnetwork)
			return;
		boolean b = !hasStoredCredentials();
		boolean c = !isLoggedIn;
		boolean c2 = c && b;
		if (c2) {
			String[] creds = loginManager.prompt(PasswordManager.getUsername());
			setLoginID(creds[0]);
			pw = creds[1];
			
			try {
				waitForLogin();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
	}

	/**
	 * The GistID we are waiting to see
	 */
	public static void waitForLogin() throws Exception {

		if (!hasnetwork)
			return;
		if(loggedIn())
			return;
		if (getLoginID() != null && pw != null) {
			
			performLogin( getLoginID(), pw);

			if (getGithub() == null) {
				System.out.println("\nERROR: Wrong Password!\n");
				login();
			}

		}
	}
	
	private static void performLogin(String u,String p) {
		try {
			github=null;
			GitHub gh = GitHub.connectUsingPassword(u, p);
			if (gh.getRateLimit().remaining < 2) {
				System.err.println("##Github Is Rate Limiting You## Disabling autoupdate");
			}
			setGithub(gh);
			setCredentialProvider(new UsernamePasswordCredentialsProvider(u, p));
			isLoggedIn = true;
			writeData(u,p);
		}catch(Exception e) {
			github=null;
		}

	}

	public static boolean loggedIn() {
		return isLoggedIn;
	}

	public static boolean hasStoredCredentials() {

		if( usernamefile!=null && passfile!=null) {
			return usernamefile.exists() && passfile.exists();
		}
		return false;
	}

	public static void logout() throws IOException {

		setGithub(null);
		isLoggedIn = false;
		if(passfile!=null)
			if(passfile.exists())
				passfile.delete();
		pw=null;
		cp=null;
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

	static void loadLoginData(File ws) throws IOException {
		workspace=ws;
		usernamefile = new File(workspace.getAbsoluteFile()+"/username.json");
		if(!usernamefile.exists())
			usernamefile=null;
		passfile = new File(workspace.getAbsoluteFile()+"/timestamp.json");
		if(!passfile.exists())
			passfile=null;
		keyfile = new File(workspace.getAbsoluteFile()+"/loadData.json");
		if(!keyfile.exists())
			keyfile=null;
		if(usernamefile.exists()) {
			List linesu = Files.readAllLines(Paths.get(usernamefile.toURI()),
	                StandardCharsets.UTF_8);
			setLoginID((String) linesu.get(0));
		}
		if(hasStoredCredentials()) {
			List linesp = Files.readAllLines(Paths.get(passfile.toURI()),
                    StandardCharsets.UTF_8);
			
			String p=(String) linesp.get(0);
			performLogin( getLoginID(), p);
		}
	}
	
	private static void writeData(String user,String pass) throws IOException {
		setLoginID(user);
		pw=pass;
		usernamefile = new File(workspace.getAbsoluteFile()+"/username.json");
		if(!usernamefile.exists())
			usernamefile.createNewFile();
		Files.write(Paths.get(usernamefile.toURI()), user.getBytes());
		passfile = new File(workspace.getAbsoluteFile()+"/timestamp.json");
		if(!passfile.exists())
			passfile.createNewFile();
		Files.write(Paths.get(passfile.toURI()), pass.getBytes());
//		keyfile = new File(workspace.getAbsoluteFile()+"/loadData.json");
//		if(!keyfile.exists())
//			keyfile.createNewFile();
		
	}

	public static CredentialsProvider getCredentialProvider() {
		return cp;
	}

	private static void setCredentialProvider(CredentialsProvider cp) {
		PasswordManager.cp = cp;
	}

	public static boolean hasNetwork() {
		// TODO Auto-generated method stub
		return hasnetwork;
	}

	public static String getLoginID() {
		return loginID;
	}

	private static void setLoginID(String loginID) {
		//new RuntimeException(loginID).printStackTrace();
		PasswordManager.loginID = loginID;
	}
}
