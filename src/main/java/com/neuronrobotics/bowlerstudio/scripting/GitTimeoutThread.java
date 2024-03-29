package com.neuronrobotics.bowlerstudio.scripting;

import org.eclipse.jgit.api.Git;

import com.neuronrobotics.bowlerstudio.IssueReportingExceptionHandler;

public class GitTimeoutThread extends Thread {
	Git git;
	String ref;
	private RuntimeException ex;
	long startTime=0;
	public GitTimeoutThread(Git g) {
		git=g;
		ref = git.getRepository().getConfig().getString("remote", "origin", "url");
		setException(new RuntimeException(
				"Git opened here, timeout on close!!\nWhen Done with the git object, Call:\n 	ScriptingEngine.closeGit(git);\n"
						+ ref + "\n"));
	}
	public void run() {
		resetTimer();
		try {
			while((startTime+(1000*120))>System.currentTimeMillis())
				Thread.sleep(1000);
			git.close();
			ScriptingEngine.gitOpenTimeout.remove(git);
			new IssueReportingExceptionHandler().uncaughtException(Thread.currentThread(), getException());
		} catch (InterruptedException e) {
			// exited clean
		}
	}
	public void resetTimer() {
		startTime=System.currentTimeMillis();
	}
	public RuntimeException getException() {
		return ex;
	}
	private void setException(RuntimeException ex) {
		this.ex = ex;
	}
}
