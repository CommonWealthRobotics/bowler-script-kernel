package com.neuronrobotics.bowlerstudio;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
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

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		new Thread(()-> {
			System.out.println("\r\n\r\nReporting Bug:\r\n\r\n");
			e.printStackTrace(System.out);
			System.out.println("\r\n\r\nBug Reported!\r\n\r\n");
			reportIssue( e) ;
			StackTraceElement[] element = e.getStackTrace();
			if(element[0].getClassName().contains("com.sun.scenario.animation.AbstractMasterTimer" )) {
				
				System.exit(-5);
			}

		}).start();
	}
	public static void reportIssue(Throwable t) {
		StackTraceElement[] element = t.getStackTrace();
		GitHub github = PasswordManager.getGithub();
		if(github==null || github.isAnonymous())
			return;
		try {
			GHRepository repo = github.getOrganization("CommonWealthRobotics").getRepository("BowlerStudio");
			List<GHIssue> issues = repo.getIssues(GHIssueState.OPEN);
			boolean stackTraceReported =false;
			String source = getTitle(element);
			for(GHIssue i:issues) {
				System.err.println("Issues are :"+i.getTitle());
				if(i.getTitle().contains(source)) {
					stackTraceReported=true;
				}
				
				if(stackTraceReported)
					break;
			}
			if(!stackTraceReported) {
				String stacktrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(t);
				String javaVersion = System.getProperty("java.version");
				String javafxVersion = System.getProperty("javafx.version");
				
				repo.createIssue(source)
				.body("Auto Reported Issue \r\n"
						+"BowlerStudio Build "+ StudioBuildInfo.getVersion()+"\n"
						+"BowlerKernel "+ BowlerKernelBuildInfo.getVersion()+"\n"
						+"JavaCad Version: " + JavaCadBuildInfo.getVersion()+"\n"
						+"Java-Bowler Version: " + SDKBuildInfo.getVersion()+"\n"
						+"Java Version: " + javaVersion+"\n"
						+"JavaFX Version: " + javafxVersion+"\n"
						+"\nOS = "+OSUtil.getOsName()+" "+OSUtil.getOsArch()+" "+(OSUtil.is64Bit()?"x64":"x86")+"\r\n"
						+"```\n"+stacktrace+"\n```")
				.label("BUG")
				.label("AUTO_REPORTED")
				.assignee("madhephaestus")
				.create();;
				
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	private static String getTitle(StackTraceElement[] element) {
		return element[0].getClassName()+" at line "+element[0].getLineNumber();
	}
}
