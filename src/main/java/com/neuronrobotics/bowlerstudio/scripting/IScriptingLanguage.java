package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.util.io.AutoLFInputStream.IsBinaryException;

import javafx.scene.Group;

/**
 * Adding additional language support to bowler studio THis interface is for
 * adding new scripting languages Add the new langauge in the Static declaration
 * of ScriptingEngine or dynamically via:
 *
 * ScriptingEngine.addScriptingLanguage(new IScriptingLanguage());
 *
 * @author hephaestus
 */
public interface IScriptingLanguage {

	/**
	 * This interface is for adding additional language support.
	 *
	 * @param code file content of the code to be executed
	 * @param args the incoming arguments as a list of objects
	 * @return the objects returned form the code that ran
	 */
	public abstract Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception;

	/**
	 * This interface is for adding additional language support.
	 *
	 * @param code the text content of the code to be executed
	 * @param args the incoming arguments as a list of objects
	 * @return the objects returned form the code that ran
	 */
	public abstract Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception;

	/**
	 * Returns the HashMap key for this language
	 */
	public abstract String getShellType();

	/**
	 * Returns the list of supported file extentions Convention is to provide just
	 * the leters that make up the file extention
	 */
	public abstract ArrayList<String> getFileExtenetion();

	/**
	 * This function should return true is the filename provided is of a supported
	 * file extension. This function may never be called if this language is only
	 * used internally.
	 *
	 * @param filename the filename of the file to be executed
	 * @return true if the file extension is supported, false otherwise.
	 */
	default boolean isSupportedFileExtenetion(String filename) {
		for (String s : getFileExtenetion()) {
			if (filename.toLowerCase().endsWith(s.toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Get the contents of an empty file
	 * 
	 * @return
	 */
	default void getDefaultContents(File source) {
		if (getIsTextFile()) {
			String content = getDefaultContents();
			OutputStream out = null;
			try {

				out = FileUtils.openOutputStream(source, false);
				IOUtils.write(content, out, Charset.defaultCharset());
				out.close(); // don't swallow close Exception if copy completes
				// normally
			} catch (Throwable t) {
				t.printStackTrace();
			} finally {
				try {
					out.close();
				} catch (Exception e) {

				}
			}
		} else
			throw new RuntimeException("This langauge needs to save its own files");
	}

	/**
	 * Get the contents of an empty file
	 * 
	 * @return
	 */
	default String getDefaultContents() {
		// TODO Auto-generated method stub
		throw new RuntimeException("This shell " + getShellType() + " has binary files ");
	}

	/**
	 * Get the contents of an empty file
	 * 
	 * @param fileSlug
	 * @return
	 */
	default void getDefaultContents(String gitURL, String fileSlug) {
		try {
			getDefaultContents(ScriptingEngine.fileFromGit(gitURL, fileSlug));
		} catch (GitAPIException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * This function returns if this is a binary file or a text file
	 *
	 * @return true if the file is a text file.
	 */
	public boolean getIsTextFile();

}
