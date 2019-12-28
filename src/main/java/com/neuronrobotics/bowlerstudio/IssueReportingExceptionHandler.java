package com.neuronrobotics.bowlerstudio;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;

import javafx.application.Platform;

public class IssueReportingExceptionHandler implements UncaughtExceptionHandler {

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		reportIssue( e) ;
		StackTraceElement[] element = e.getStackTrace();
		
		if(element[0].getClassName().contains("com.sun.scenario.animation.AbstractMasterTimer" )) {
			System.exit(-5);
		}
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
			for(GHIssue i:issues) {
				System.err.println("Issues are :"+i.getTitle());
				String source = getTitle(element);
				if(i.getTitle().contains(source)) {
					stackTraceReported=true;
				}
				
				if(stackTraceReported)
					break;
			}
			if(!stackTraceReported) {
				String stacktrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(t);
				repo.createIssue(getTitle(element))
				.body("Auto Reported Issue \r\n"+stacktrace)
				.label("BUG")
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
