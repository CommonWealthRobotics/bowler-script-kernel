package com.neuronrobotics.bowlerstudio.scripting;

import org.eclipse.jgit.api.Git;

public class GitTimeouThread extends Thread {
	Git git;
	String ref;
	private RuntimeException ex;
	public GitTimeouThread(Git g) {
		git=g;
		ref = git.getRepository().getConfig().getString("remote", "origin", "url");
		setException(new RuntimeException(
				"Git opened here, timeout on close!!\nWhen Done with the git object, Call:\n 	ScriptingEngine.closeGit(git);\n"
						+ ref + "\n"));
	}
	public void run() {
		try {
			Thread.sleep(60 * 1000 * 20);
			ScriptingEngine.exp.uncaughtException(Thread.currentThread(), getException());
			git.close();
			ScriptingEngine.gitOpenTimeout.remove(git);
		} catch (InterruptedException e) {
			// exited clean
		}
	}
	public RuntimeException getException() {
		return ex;
	}
	private void setException(RuntimeException ex) {
		this.ex = ex;
	}
}
