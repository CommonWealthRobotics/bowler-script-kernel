package com.neuronrobotics.bowlerstudio;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueBuilder;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import com.neuronrobotics.bowlerkernel.BowlerKernelBuildInfo;
import com.neuronrobotics.bowlerstudio.assets.StudioBuildInfo;
import com.neuronrobotics.bowlerstudio.scripting.PasswordManager;
import com.neuronrobotics.javacad.JavaCadBuildInfo;
import com.neuronrobotics.sdk.config.SDKBuildInfo;
import com.neuronrobotics.video.OSUtil;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

public class IssueReportingExceptionHandler implements UncaughtExceptionHandler {
	private static int timerErrorCount = 0;
	String stacktraceFromHandlerInstantiation;
	private int fxExceptionCount;
	private static boolean processing = false;
	private static HashMap<Throwable, String> exceptionQueue = new HashMap<Throwable, String>();
	private static boolean reportIssues = false;

	private static HashMap<String, Integer> exceptionCounter = new HashMap<String, Integer>();

	public IssueReportingExceptionHandler() {
		stacktraceFromHandlerInstantiation = org.apache.commons.lang.exception.ExceptionUtils
				.getStackTrace(new Exception());
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		if (e == null) {
			except(new Exception("A null exception was thrown"));
		}

		except(e);

	}

	public static void runLater(Runnable r) {
		runLater(r, new Exception("UI Thread Exception here!"));
	}

	public static void runLater(Runnable r, Throwable ex) {
		Platform.runLater(() -> {
			try {
				r.run();
			} catch (Throwable t) {
				t.printStackTrace();
				ex.printStackTrace();
			}

		});
	}

	public void except(Throwable e, String stacktraceFromCatch) {
		System.out.println(stacktraceFromCatch);
		new Thread(() -> {
			String stacktrace = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(e);
			StackTraceElement[] element = e.getStackTrace();
			String source = getTitle(element);

			if (exceptionCounter.get(source) == null) {
				exceptionCounter.put(source, 0);
			}
			exceptionCounter.put(source, exceptionCounter.get(source) + 1);
			if (exceptionCounter.get(source) > 1) {
//				Alert alert = new Alert(AlertType.CONFIRMATION);
//				alert.setTitle("IRRECOVERABLE FAULT");
//				alert.setHeaderText("Its just gunna crash, sorry...");
//				alert.setContentText("I can wait till you hit yes, buts its basically done...");
//				Optional<ButtonType> result = alert.showAndWait();
//				System.exit(-1);
				return;// maybe just swallowing after 5 reports is good enough??
			}
			if (element != null)
				if (element.length > 0) {
					if (element[0].getClassName() != null)
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
							return;
						} else if (element[0].getClassName().contains("javafx.embed.swing.SwingNode")) {
							if (timerErrorCount++ > 5) {
								try {
									Thread.sleep(5000);
								} catch (InterruptedException e1) {
									// TODO Auto-generated catch block
									e1.printStackTrace();
								} // wait for the Issue to be reported
								System.exit(-5);
							}
							return;
						} else if (checkIgnoreExceptions(e)) {
							e.printStackTrace();
							return;
						} else if (java.lang.OutOfMemoryError.class.isInstance(e)
								|| stacktrace.contains("java.lang.OutOfMemoryError")) {
							e.printStackTrace();
							System.exit(-1);
						}
				}

			String javaVersion = System.getProperty("java.version");
			String javafxVersion = System.getProperty("javafx.version");
			String body = "Auto Reported Issue \r\n" + "BowlerStudio Build " + StudioBuildInfo.getVersion() + "\n"
					+ "BowlerKernel " + BowlerKernelBuildInfo.getVersion() + "\n" + "JavaCad Version: "
					+ JavaCadBuildInfo.getVersion() + "\n" + "Java-Bowler Version: " + SDKBuildInfo.getVersion() + "\n"
					+ "Java Version: " + javaVersion + "\n" + "JavaFX Version: " + javafxVersion + "\n" + "\nOS = "
					+ OSUtil.getOsName() + " " + OSUtil.getOsArch() + " " + (OSUtil.is64Bit() ? "x64" : "x86") + "\r\n"
					+ "```\n" + stacktrace + "\n```" + "\n\nCaught and reported at: \n" + "```\n" + stacktraceFromCatch
					+ "\n```\n" + "\nIssueReportingExceptionHandler Created at:\n" + "\n```\n"
					+ stacktraceFromHandlerInstantiation + "\n```\n";
			System.err.println(body);
			System.err.println("\r\n\r\nBug Reported!\r\n\r\n");
			System.out.println(body);
			System.out.println("\r\n\r\nBug Reported!\r\n\r\n");
			GitHub github = PasswordManager.getGithub();

			if (github == null || github.isAnonymous())
				return;
			processing = true;
			if (reportIssues) {
				runReport(element, body, github);
				return;
			}
			Platform.runLater(() -> {
				Alert alert = new Alert(AlertType.CONFIRMATION);
				alert.setTitle("An Error occoured");
				alert.setHeaderText("Would it be ok if I report this issue back to Kevin so he can fix it?");
				alert.setContentText("Are you ok with this?");
				Optional<ButtonType> result = alert.showAndWait();
				if (result.get() == ButtonType.OK) {
					reportIssues = true;
					runReport(element, body, github);
				} else {
					processing = false;
				}
			});

		}).start();
	}

	private void runReport(StackTraceElement[] element, String body, GitHub github) {
		new Thread(() -> {
			try {
				GHRepository repo = github.getOrganization("CommonWealthRobotics").getRepository("BowlerStudio");
				List<GHIssue> issues = repo.getIssues(GHIssueState.ALL);
				String source = getTitle(element);

				for (GHIssue i : issues) {
					System.err.println("Issues are :" + i.getTitle());
					if (i.getTitle().contains(source)) {
						List<GHIssueComment> comments = i.getComments();
						// Check to see if i created this issue
						boolean metoo = false;
						try {

							GHUser user = i.getUser();
							if (user != null) {
								String name = user.getName();
								if (name != null) {
									String username = PasswordManager.getUsername();
									if (username != null) {
										metoo = name.contentEquals(username);
										for (GHIssueComment comment : comments) {
											// check to see if i commented on this issue
											if (comment.getUser().getName().contentEquals(username)) {
												metoo = true;
											}
										}
									}
								}
							}
						} catch (Throwable t) {

						}
						// If i havent commented yet, comment that i had this issue too.
						if (!metoo)
							i.comment(body);
						if (i.getState() == GHIssueState.CLOSED) {
							try {
								i.reopen();
							} catch (Throwable t) {
							}
						}
						BowlerKernel.upenURL(i.getHtmlUrl().toURI());
						return;
					}

				}

				GHIssue i = repo.createIssue("Build " + StudioBuildInfo.getVersion() + " " + source).body(body)
						.label("BUG").label("AUTO_REPORTED").assignee("madhephaestus").create();
				BowlerKernel.upenURL(i.getHtmlUrl().toURI());

			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			processing = false;
			if (!exceptionQueue.isEmpty()) {
				Throwable exception = (Throwable) exceptionQueue.keySet().toArray()[0];
				String source = exceptionQueue.remove(exception);
				except(exception, source);
			}
		}).start();

	}

	public void except(Throwable t) {
		String stacktraceFromCatch = org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(new Exception());
		StackTraceElement[] element = t.getStackTrace();

		String className = element[0].getClassName();
		if (checkIgnoreExceptions(t)) {
			t.printStackTrace();
			return;
		}
		if (Platform.isFxApplicationThread()) {
			
			System.err.println("Exception in Javafx thread! \n"
					+ org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(t));
			fxExceptionCount++;
			if (fxExceptionCount > 10) {
				System.err.println(stacktraceFromCatch);
				t.printStackTrace();
				System.exit(-5);
			}
			throw new RuntimeException(t);
		}

		if (processing) {
			exceptionQueue.put(t, stacktraceFromCatch);
			return;
		}

		except(t, stacktraceFromCatch);
	}

	private boolean checkIgnoreExceptions(Throwable t) {
		try {
			return t.getStackTrace()[0].getClassName().contains("DropHandler") || t.getMessage().contains("Key already associated with a running event loop");
		}catch(Throwable tf) {
			return false;
		}
	}

	public static String getTitle(Throwable element) {
		return getTitle(element.getStackTrace());
	}

	public static String getTitle(StackTraceElement[] element) {
		if (element.length == 0)
			return "No exception trace found";
		return element[0].getClassName() + " at line " + element[0].getLineNumber();
	}
}
