package com.neuronrobotics.bowlerstudio;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import com.neuronrobotics.bowlerkernel.BowlerKernelBuildInfo;
import com.neuronrobotics.bowlerstudio.assets.StudioBuildInfo;
import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;
import com.neuronrobotics.javacad.JavaCadBuildInfo;
import com.neuronrobotics.sdk.config.SDKBuildInfo;
import com.neuronrobotics.video.OSUtil;

import javafx.application.Platform;

public class IssueReportingExceptionHandler implements UncaughtExceptionHandler {
	private static int timerErrorCount = 0;
	String stacktraceFromHandlerInstantiation;
	private static boolean processing=false;
	private static HashMap<Throwable,String> exceptionQueue =new HashMap<Throwable, String>();
	public IssueReportingExceptionHandler(){
		stacktraceFromHandlerInstantiation=org.apache.commons.lang.exception.ExceptionUtils
				.getStackTrace(new Exception());
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		StackTraceElement[] element = e.getStackTrace();
		if (element[0].getClassName().contains("com.sun.scenario.animation.AbstractMasterTimer")) {
			if (timerErrorCount++ > 5) {
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} // wait for the Issue to be reported
				System.exit(-5);
			}
		}
		except(e);
		
	}
	public void except(Throwable t, String stacktraceFromCatch) {
		new Thread(() -> {
			processing=true;
			System.out.println("\r\n\r\nReporting Bug:\r\n\r\n");
			t.printStackTrace(System.out);
			System.out.println("\r\n\r\nBug Reported!\r\n\r\n");

			StackTraceElement[] element = t.getStackTrace();
			GitHub github = PasswordManager.getGithub();
			if (github == null || github.isAnonymous())
				return;
			String stacktrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(t);
			
			String javaVersion = System.getProperty("java.version");
			String javafxVersion = System.getProperty("javafx.version");
			String body = "Auto Reported Issue \r\n" + "BowlerStudio Build " + StudioBuildInfo.getVersion() + "\n"
					+ "BowlerKernel " + BowlerKernelBuildInfo.getVersion() + "\n" + "JavaCad Version: "
					+ JavaCadBuildInfo.getVersion() + "\n" + "Java-Bowler Version: " + SDKBuildInfo.getVersion() + "\n"
					+ "Java Version: " + javaVersion + "\n" + "JavaFX Version: " + javafxVersion + "\n" + "\nOS = "
					+ OSUtil.getOsName() + " " + OSUtil.getOsArch() + " " + (OSUtil.is64Bit() ? "x64" : "x86") + "\r\n"
					+ "```\n" + stacktrace + "\n```" + "\n\nCaught and reported at: \n" + "```\n" + stacktraceFromCatch
					+ "\n```\n"
					+"\nIssueReportingExceptionHandler Created at:\n"
					+"\n```\n"
					+stacktraceFromHandlerInstantiation
					+"\n```\n"
					;
			try {
				GHRepository repo = github.getOrganization("CommonWealthRobotics").getRepository("BowlerStudio");
				List<GHIssue> issues = repo.getIssues(GHIssueState.OPEN);
				String source = getTitle(element);
				for (GHIssue i : issues) {
					System.err.println("Issues are :" + i.getTitle());
					if (i.getTitle().contains(source)) {
						BowlerKernel.upenURL(i.getHtmlUrl().toURI());
						return;
					}

				}

				GHIssue i = repo.createIssue(source).body(body).label("BUG").label("AUTO_REPORTED")
						.assignee("madhephaestus").create();
				BowlerKernel.upenURL(i.getHtmlUrl().toURI());

			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			processing=false;
			if(!exceptionQueue.isEmpty()) {
				Throwable exception = (Throwable) exceptionQueue.keySet().toArray()[0];
				String source = exceptionQueue.remove(exception);
				except(exception,  source) ;
			}
				
		}).start();
	}
	public void except(Throwable t) {
		String stacktraceFromCatch = org.apache.commons.lang.exception.ExceptionUtils
				.getStackTrace(new Exception());
		if(processing) {
			exceptionQueue.put(t, stacktraceFromCatch);
			return;
		}
		
		except( t, stacktraceFromCatch);
	}

	private static String getTitle(StackTraceElement[] element) {
		return element[0].getClassName() + " at line " + element[0].getLineNumber();
	}
}
