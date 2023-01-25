package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseLoader;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

public class RobotHelper implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) {
		byte[] bytes;
		try {
			bytes = Files.readAllBytes(code.toPath());
			String s = new String(bytes, "UTF-8");
			MobileBase mb;
			try {
				mb = new MobileBase(IOUtils.toInputStream(s, "UTF-8"));
				mb.setGitSelfSource(ScriptingEngine.findGitTagFromFile(code));
				return MobileBaseLoader.get(mb).getBase();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// System.out.println("Clojure returned of type="+ret.getClass()+" value="+ret);
		return null;
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) {

		MobileBase mb = null;
		try {
			mb = new MobileBase(IOUtils.toInputStream(code, "UTF-8"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}

		return mb;
	}

	@Override
	public String getShellType() {
		return "MobilBaseXML";
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

		return new MobileBase().getXml();
	}

	/**
	 * Get the contents of an empty file
	 * 
	 * @return
	 */
	public String getDefaultContents(String gitURL, String slug) {
		MobileBase back = new MobileBase();
		back.setScriptingName(slug);
		back.setGitSelfSource(Arrays.asList(gitURL, slug + ".xml").toArray(new String[0]));
		String[] cad = ScriptingEngine.copyGitFile(
				"https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git", gitURL, "exampleCad.groovy",slug+"Cad.groovy",true);
		String[] kin = ScriptingEngine.copyGitFile(
				"https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git", gitURL,
				"exampleKinematics.groovy",slug+"Kinematics.groovy",true);
		String[] walk = ScriptingEngine.copyGitFile(
				"https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git", gitURL,
				"exampleWalking.groovy",slug+"Walk.groovy",true);
		back.setGitCadEngine(cad);
		back.setGitDhEngine(kin);
		back.setGitWalkingEngine(walk);
		DHParameterKinematics limb = new DHParameterKinematics();
		limb.setScriptingName(slug+"-Limb-1");
		limb.setGitCadEngine(cad);
		limb.setGitDhEngine(kin);
		LinkConfiguration newLink = new LinkConfiguration();
		newLink.setName("link1");
		newLink.setDeviceScriptingName(newLink.getName());
		newLink.setDeviceScriptingName("exampleDevice");
		limb.addNewLink(newLink, new DHLink(0, 0, 100, 0));
		back.getAppendages().add(limb);

		return back.getXml();
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		// TODO Auto-generated method stub
		return new ArrayList<>(Arrays.asList("xml"));
	}

}
