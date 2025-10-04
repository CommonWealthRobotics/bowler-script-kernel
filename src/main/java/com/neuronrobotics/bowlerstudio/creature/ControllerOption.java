package com.neuronrobotics.bowlerstudio.creature;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.delim;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotController;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

public class ControllerOption {
	public static final String URL_OF_OPTIONS = "https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git";
	@Expose(serialize = true, deserialize = true)
	String type;
	@Expose(serialize = true, deserialize = true)
	String imageGit;
	@Expose(serialize = true, deserialize = true)
	String imageFile;
	@Expose(serialize = true, deserialize = true)
	List<String> vitaminType;
	@Expose(serialize = true, deserialize = true)
	List<String> vitaminSize;
	@Expose(serialize = true, deserialize = true)
	List<TransformNR> vitaminPose;
	@Expose(serialize = true, deserialize = true)
	String linkLoaderGit;
	@Expose(serialize = true, deserialize = true)
	String linkLoaderFile;
	@Expose(serialize = true, deserialize = true)
	String linkDeviceName;
	@Expose(serialize = true, deserialize = true)
	String linkDeviceType;
	@Expose(serialize = true, deserialize = true)
	String firmwareGit;
	@Expose(serialize = true, deserialize = true)
	String firmwareFile;
	@Expose(serialize = true, deserialize = true)
	ControllerFeatures provides;
	@Expose(serialize = true, deserialize = true)
	ControllerFeatures consumes;
	
	// Internal variables
	private boolean built=false;
	javafx.scene.image.Image image = null;
	CSG indicator = null;
	File stlFile = null;
	private ArrayList<VitaminLocation> back;
	private String baseName;
	
	public void build(CaDoodleFile f) {
		if(built)
			return;
		built=true;
		image =  new Image(getImageFile().toURI().toString());
		String absolutePath = ScriptingEngine.getWorkspace().getAbsolutePath() + delim() + "uicache";
		File dir = new File(absolutePath);
		if (!dir.exists())
			dir.mkdirs();
		stlFile = new File(absolutePath + delim() + type + ".stl");
		if ( stlFile.exists()) {
			indicator = Vitamins.get(stlFile);
			getIndicator().setColor(Color.WHITE);
			return;
		}else {
			AddRobotController arc = new AddRobotController().setController(this);
			arc.setCaDoodleFile(f);
			List<CSG> so = arc.process(new ArrayList<>());
			for (CSG c : so) {
				for (String s : c.getParameters(f.getCsgDBinstance())) {
					CSGDatabase.delete(s);
				}
			}
			indicator = so.get(0);
			if (so.size() > 1) {
				for(int i=1;i<so.size();i++) {
					indicator=getIndicator().dumbUnion(so.get(i));
				}
			}
			getIndicator().setColor(Color.WHITE);
		}

	}

	public File getImageFile() {
		try {
			return ScriptingEngine.fileFromGit(imageGit, imageFile);
		} catch (InvalidRemoteException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		throw new RuntimeException(imageGit+"/"+ imageFile);
	}

	public void runLinkLoader() throws FileNotFoundException {
		if (linkLoaderGit == null || linkLoaderFile == null) {
			com.neuronrobotics.sdk.common.Log.debug("Using built in link loaders");
			return;
		}
		try {
			ScriptingEngine.inlineGistScriptRun(linkLoaderGit, linkLoaderFile, null);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		throw new FileNotFoundException(linkLoaderGit + "/" + linkLoaderFile);
	}

	public File getFirmware() throws FileNotFoundException {
		try {
			return ScriptingEngine.fileFromGit(firmwareGit, firmwareFile);
		} catch (InvalidRemoteException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		throw new FileNotFoundException();
	}

	public String getType() {
		return type;
	}

	public static ArrayList<ControllerOption> getOptions()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		try {
			Type TT_CaDoodleFile = new TypeToken<ArrayList<ControllerOption>>() {
			}.getType();
			Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
					.excludeFieldsWithoutExposeAnnotation().create();

			File f = ScriptingEngine.fileFromGit(URL_OF_OPTIONS, "controllerOptions.json");
			String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
			return gson.fromJson(content, TT_CaDoodleFile);
		} catch (Exception ex) {
			com.neuronrobotics.sdk.common.Log.error(ex);;
			return new ArrayList<ControllerOption>();
		}
	}

	public String getLinkDeviceName() {
		return linkDeviceName;
	}
	public CSG getVitaminCSG(int index) {
		try {
			return Vitamins.get(vitaminType.get(index), vitaminSize.get(index));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	public List<String> getVitaminType() {
		return vitaminType;
	}
	public int getVitaminNumber() {
		return vitaminSize.size();
	}
	public List<String> getVitaminSize() {
		return vitaminSize;
	}

	public String getLinkDeviceType() {
		return linkDeviceType;
	}

	public javafx.scene.image.Image getImage() {
		return image;
	}
	public TransformNR getVitaminPose(int index) {
		return getVitaminPose().get(index);
	}
	public List<TransformNR> getVitaminPose() {
		if(vitaminPose==null)
			return new ArrayList<>(Arrays.asList(new TransformNR()));
		return vitaminPose;
	}

	public CSG getIndicator() {
		return indicator;
	}

	public ControllerFeatures getProvides() {
		return provides;
	}

	public ControllerFeatures getConsumes() {
		return consumes;
	}

	public ArrayList<VitaminLocation> getVitamins(TransformNR location, String baseName) {
		if(back==null ) {
			this.baseName = baseName;
			back = new ArrayList<VitaminLocation>();
			for (int i = 0; i < getVitaminNumber(); i++) {
				TransformNR offset = location.times(getVitaminPose(i));
				back.add(new VitaminLocation(false,baseName+"_"+i, vitaminType.get(i), vitaminSize.get(i), offset)
						);
			}
		}
		return back;
	}
}
