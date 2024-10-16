package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

public class AddFromFile extends AbstractAddFrom implements ICaDoodleOpperation {

	@Expose(serialize = true, deserialize = true)
	private String name = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = null;
	private ArrayList<String> options = new ArrayList<String>();
	private StringParameter parameter = null;

	public AddFromFile set(File source) {
		getParameter().setStrValue(source.getAbsolutePath());
		return this;
	}

	@Override
	public String getType() {
		return "Add Object";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		nameIndex = 0;
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		if (getName() == null) {

		}
		try {
//			ArrayList<Object>args = new ArrayList<>();
//			args.addAll(Arrays.asList(getName() ));
			ArrayList<CSG> collect = new ArrayList<>();
			List<CSG> flattenedCSGs = ScriptingEngine.flaten(getFile(), CSG.class, null);
			System.out.println("Initial Loading " + getParameter().getStrValue());
			for (int i = 0; i < flattenedCSGs.size(); i++) {
				CSG csg = flattenedCSGs.get(i);
				CSG processedCSG = processGiven(csg, i, getParameter(), getOrderedName());

				collect.add(processedCSG);
			}
			back.addAll(collect);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return back;
	}

	public static File copyFileToNewDirectory(File sourceFile, File targetDirectory, String newBaseName)
			throws IOException {
		if (!sourceFile.exists()) {
			throw new IOException("Source file does not exist: " + sourceFile.getAbsolutePath());
		}

		if (!targetDirectory.exists()) {
			if (!targetDirectory.mkdirs()) {
				throw new IOException("Failed to create target directory: " + targetDirectory.getAbsolutePath());
			}
		}

		String fileName = sourceFile.getName();
		String fileExtension = "";
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
			fileExtension = fileName.substring(dotIndex);
		}

		String newFileName = newBaseName + fileExtension;
		File targetFile = new File(targetDirectory, newFileName);

		Path sourcePath = sourceFile.toPath();
		Path targetPath = targetFile.toPath();

		Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
		return targetFile;
	}

	public File getFile() {
		StringParameter loc = new StringParameter("CaDoodle_File_Location", "NotSet", new ArrayList<String>());
		File file = new File(getParameter().getStrValue());
		String parentIncoming = file.getParentFile().getAbsolutePath();
		File parentFile = new File(loc.getStrValue()).getParentFile();
		String source = parentFile.getAbsolutePath();
		if (!parentIncoming.toLowerCase().contentEquals(source.toLowerCase()) && file.exists()) {
			File copied;
			try {
				copied = copyFileToNewDirectory(file,parentFile,getName());
				file= copied;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		file = new File(source+DownloadManager.delim()+file.getName());
		getParameter().setStrValue(file.getAbsolutePath());
		return file;
	}

	private CSG processGiven(CSG csg, int i, StringParameter parameter, String name) {
		CSG processedCSG = csg
//		    .moveToCenterX()
//		    .moveToCenterY()
//		    .toZMin()
				.transformed(TransformFactory.nrToCSG(getLocation())).syncProperties(csg).setParameter(parameter)
				.setRegenerate(previous -> {
					try {
						File file = getFile();
						String fileLocation = file.getAbsolutePath();
						System.out.println("Regenerating " + fileLocation);
						List<CSG> flattenedCSGs = ScriptingEngine.flaten(file, CSG.class, null);
						CSG csg1 = flattenedCSGs.get(i);
						return processGiven(csg1, i, parameter, name);
					} catch (Exception e) {
						e.printStackTrace();
					}
					return previous;
				}).setName(name);
		return processedCSG;
	}

	public TransformNR getLocation() {
		if (location == null)
			location = new TransformNR();
		return location;
	}

	public AddFromFile setLocation(TransformNR location) {
		this.location = location;
		return this;
	}

	public String getName() {
		if (name == null) {
			setName(RandomStringFactory.generateRandomString());
		}
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public StringParameter getParameter() {
		if (parameter == null)
			setParameter(new StringParameter(getName() + "_CaDoodle_File", "UnKnown", options));
		return parameter;
	}

	public void setParameter(StringParameter parameter) {
		this.parameter = parameter;
	}

}
