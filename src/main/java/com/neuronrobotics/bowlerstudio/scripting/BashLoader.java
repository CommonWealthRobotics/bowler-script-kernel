package com.neuronrobotics.bowlerstudio.scripting;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.neuronrobotics.video.OSUtil;

public class BashLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		// List<String> asList = Arrays.asList("bash",code.getAbsolutePath());
		ArrayList<String> commands = new ArrayList<>();
		commands.add("bash");
		commands.add(code.getAbsolutePath());
		if (args != null) {
			for (Object o : args) {
				commands.add(o.toString());
			}
		}
		ProcessBuilder pb = new ProcessBuilder(commands);
		// setting the directory
		pb.directory(code.getParentFile());
		// startinf the process
		Process process = pb.start();

		// for reading the ouput from stream
		BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
		BufferedReader errInput = new BufferedReader(new InputStreamReader(process.getErrorStream()));

		String s = null;
		String e = null;
		Thread.sleep(100);
		ArrayList<String> back = new ArrayList<>();
		while ((s = stdInput.readLine()) != null || (e = errInput.readLine()) != null) {
			if (s != null) {
				back.add(s);
				System.out.println(s);
			}
			if (e != null)
				System.out.println(e);
			//
		}
		process.waitFor();
		process.exitValue();

		while (process.isAlive()) {
			Thread.sleep(100);
		}
		return back;
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		throw new RuntimeException("Bash scripts have to be sent as files");
	}

	@Override
	public String getShellType() {
		return "Bash";
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		if (OSUtil.isWindows())
			return new ArrayList<>();
		return new ArrayList<>(Arrays.asList(".sh", ".bash"));
	}

	/**
	 * Get the contents of an empty file
	 * 
	 * @return
	 */
	public String getDefaultContents() {
		return "echo Hello World";
	}

	@Override
	public boolean getIsTextFile() {
		// TODO Auto-generated method stub
		return true;
	}

}
