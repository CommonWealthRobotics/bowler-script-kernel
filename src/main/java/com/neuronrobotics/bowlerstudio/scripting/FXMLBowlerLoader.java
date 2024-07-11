package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Tab;
import javafx.scene.layout.Pane;

public class FXMLBowlerLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File xml, ArrayList<Object> args) throws Exception {
		FXMLLoader loader = new FXMLLoader(xml.toURI().toURL());
		Pane newLoadedPane =  loader.load();
		// Create a tab
		Tab myTab = new Tab();
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
