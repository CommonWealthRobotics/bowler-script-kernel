package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Scanner;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.util.ThreadUtil;

public class JettyTest {
  private static int WEBSERVER_PORT = 8065;
  private static String HOME_Local_URL = "http://localhost:" + WEBSERVER_PORT
      + "/BowlerStudio/Welcome-To-BowlerStudio/";

  private Server server;

  @Before
  public void startJetty() throws Exception {
    try {
      ScriptingEngine.setupAnyonmous();
      // ScriptingEngine.setAutoupdate(true);
    } catch (Exception ex) {
      System.out.println("User not logged in, test can not run");
    }
    File indexOfTutorial = ScriptingEngine
        .fileFromGit("https://github.com/CommonWealthRobotics/CommonWealthRobotics.github.io.git",
            "master",// the default branch is source, so this needs to be specified
            "index.html");

    // HOME_Local_URL = indexOfTutorial.toURI().toString().replace("file:/",
    // "file:///");
    server = new Server();
    ServerConnector connector = new ServerConnector(server);
    server.setConnectors(new Connector[]{connector});


    ResourceHandler resource_handler = new ResourceHandler();
    resource_handler.setDirectoriesListed(true);
    resource_handler.setWelcomeFiles(new String[]{"index.html"});
    System.out.println("Serving " + indexOfTutorial.getParent());
    resource_handler.setResourceBase(indexOfTutorial.getParent());

    HandlerList handlers = new HandlerList();
    handlers.setHandlers(new Handler[]{resource_handler, new DefaultHandler()});
    server.setHandler(handlers);

    // Start Server
    server.start();
    WEBSERVER_PORT = connector.getLocalPort();
    HOME_Local_URL = "http://localhost:" + WEBSERVER_PORT
        + "/BowlerStudio/Welcome-To-BowlerStudio/";
  }

  @Test
  public void test() throws InvalidRemoteException, TransportException, GitAPIException, IOException {

    try {
      java.io.InputStream url = new URL(HOME_Local_URL).openStream();
      try {
        System.out.println(org.apache.commons.io.IOUtils.toString(url));
      } finally {
        org.apache.commons.io.IOUtils.closeQuietly(url);
      }

      // read from your scanner
    } catch (IOException ex) {
      // there was some connection problem, or the file did not exist on
      // the server,
      // or your URL was not in the right format.
      // think about what to do now, and put it here.
      ex.printStackTrace(); // for now, simply output it.
      fail(ex.getMessage());
    }

  }

  @After
  public void stopJetty() {
    try {
      server.stop();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
