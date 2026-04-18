package com.neuronrobotics.bowlerstudio.creature;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.delim;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.RobotHelper;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

public class LimbOption {
	@Expose(serialize = true, deserialize = true)
	LimbType type;
	@Expose(serialize = true, deserialize = true)
	String name;
	@Expose(serialize = true, deserialize = true)
	String url;
	@Expose(serialize = true, deserialize = true)
	String file;
	@Expose(serialize = true, deserialize = true)
	boolean composite;
	@Expose(serialize = true, deserialize = true)
	ControllerFeatures consumes;
	@Expose(serialize = true, deserialize = true)
	ControllerFeatures provides;
	public static final TransformNR LimbRotationOffset = new TransformNR(new RotationNR(0, 90, -90));

	private CSG indicator;
	private Image image;

	public DHParameterKinematics getLimb(CSGDatabaseInstance db, String uniqueName) throws Exception {
		String xmlContent = ScriptingEngine.codeFromGit(getUrl(), getSourceFile())[0];
		if (!composite) {
			DHParameterKinematics newLimb = new DHParameterKinematics(null, IOUtils.toInputStream(xmlContent, "UTF-8"));
			newLimb.setScriptingName(uniqueName);
			MobileBaseLoader.setDefaultDhParameterKinematics(db, newLimb);
			return newLimb;
		} else {
			MobileBase base = RobotHelper.fileToRobot(db, getUrl(), getSourceFile());
			DHParameterKinematics newLimb = base.getAllDHChains().get(0);
			newLimb.setScriptingName(uniqueName);
			return newLimb;
		}
	}

	public static ArrayList<LimbOption> getOptions()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		try {
			Type TT_CaDoodleFile = new TypeToken<ArrayList<LimbOption>>() {
			}.getType();
			Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
					.excludeFieldsWithoutExposeAnnotation().create();
			File f = ScriptingEngine.fileFromGit(ControllerOption.URL_OF_OPTIONS, "limbOptions.json");
			String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
			return gson.fromJson(content, TT_CaDoodleFile);
		} catch (Exception ex) {
			com.neuronrobotics.sdk.common.Log.error(ex);;
			return new ArrayList<LimbOption>();
		}
	}

	@Override
	public String toString() {
		return getType() + " " + getName() + " " + getUrl() + "/" + getSourceFile() + "\n\tConsumes:" + getConsumes()
				+ "\n\tProvides:" + getProvides();
	}

	public ControllerFeatures getConsumes() {
		return consumes;
	}

	public ControllerFeatures getProvides() {
		return provides;
	}

	public LimbType getType() {
		return type;
	}

	public String getName() {
		return name;
	}

	public void build(CaDoodleFile f) throws IOException {
		String absolutePath = ScriptingEngine.getWorkspace().getAbsolutePath() + delim() + "uicache";
		File dir = new File(absolutePath);
		if (!dir.exists())
			dir.mkdirs();
		File imageFile = new File(absolutePath + delim() + type + name + ".png");
		File stlFile = new File(absolutePath + delim() + type + name + ".stl");
		if (imageFile.exists() && stlFile.exists()) {
			try {
				indicator = Vitamins.get(f.getCsgDBinstance(),false, stlFile);
				indicator = indicator.transformed(TransformFactory.nrToCSG(LimbRotationOffset));
				indicator.setColor(Color.WHITE);
				image = new Image(imageFile.toURI().toString());
				return;
			}catch(Exception e) {
				Log.error(e);
				stlFile.delete();
			}
		}
		AddRobotLimb add = new AddRobotLimb().setLimb(this).setLocation(new TransformNR());
		add.setCaDoodleFile(f);
		add.forceLoad = true;
		MobileBaseBuilder value = new MobileBaseBuilder(f.getCsgDBinstance(),
				Files.createTempDirectory(name + "-").toFile().getAbsolutePath(), "testfile");
		add.setBuilderName("tmp");
		add.getRobots().put("tmp", value);
		List<CSG> so = add.process(new ArrayList<>());
		if (so.size() == 0)
			throw new RuntimeException("Add limb produced no parts!");
		add.getRobots().remove("tmp");
		for (CSG c : so) {
			for (String s : c.getParameters(f.getCsgDBinstance())) {
				f.getCsgDBinstance().delete(s);
			}
		}
		if (f.getImageEngine() != null) {

			try {
				image = f.getImageEngine().get(f.getCsgDBinstance(), so);
				try {
					BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
					ImageIO.write(bufferedImage, "png", imageFile);
					System.err.println("Thumbnail saved successfully to " + imageFile.getAbsolutePath());
				} catch (Exception e) {
					// com.neuronrobotics.sdk.common.Log.error("Error saving image: " +
					// e.getMessage());
					com.neuronrobotics.sdk.common.Log.error(e);
				}
			} catch (NoImageException e) {
				Log.error(e);
				image = new WritableImage(100, 100);
			}

		}
		indicator = get(so.get(0));
		if (so.size() > 1) {
			for (int i = 1; i < so.size(); i++) {
				indicator = indicator.dumbUnion(get(so.get(i)));
			}
		}
		indicator.setColor(Color.WHITE);
		try {
			FileUtil.write(Paths.get(stlFile.getAbsolutePath()), indicator.toStlString());
			System.err.println("Indicator STL saved successfully to " + stlFile.getAbsolutePath());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		indicator = indicator.transformed(TransformFactory.nrToCSG(LimbRotationOffset));

	}

	CSG get(CSG in) {
		if (in.hasManipulator())
			try {
				return in.transformed(TransformFactory.nrToCSG(TransformFactory.affineToNr(in.getManipulator())));
			} catch (MissingManipulatorException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		return in;
	}

	public javafx.scene.image.Image getImage() {
		return image;
	}

	public CSG getIndicator() {
		return indicator;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getSourceFile() {
		return file;
	}

	public void setSourceFile(String file) {
		this.file = file;
	}
}
