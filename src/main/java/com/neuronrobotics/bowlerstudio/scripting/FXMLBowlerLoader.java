package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class FXMLBowlerLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File xml, ArrayList<Object> args) throws Exception {
		javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(xml.toURI().toURL());
		javafx.scene.layout.Pane newLoadedPane =  loader.load();
		// Create a tab
		javafx.scene.control.Tab myTab = new javafx.scene.control.Tab();
		//set the title of the new tab
		myTab.setContent(newLoadedPane);
		return myTab;
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		throw new RuntimeException("This engine only supports files");
	}

	@Override
	public String getShellType() {
		// TODO Auto-generated method stub
		return "fxml";
	}
	@Override
	public boolean getIsTextFile() {
		// TODO Auto-generated method stub
		return true;
	}
	/**
	 * Get the contents of an empty file
	 * 
	 * @return
	 */
	public String getDefaultContents() {
		return "";
	}
	@Override
	public ArrayList<String> getFileExtenetion() {
		// TODO Auto-generated method stub
		return new ArrayList<>(Arrays.asList("fxml","FXML","FxML"));
	}

}
