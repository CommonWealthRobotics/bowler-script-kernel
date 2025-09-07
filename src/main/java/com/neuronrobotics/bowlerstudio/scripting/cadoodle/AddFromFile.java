package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;

public class AddFromFile extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private TransformNR location = null;
	//private ArrayList<String> options = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	private Boolean preventBoM = false;

	public AddFromFile set(File source) {
		for (String s : ScriptingEngine.getAllExtentions()) {
			if (source.getName().toLowerCase().endsWith(s.toLowerCase())) {
				toLocal(source, getName(),getCaDoodleFile());
				try {
					getFile();
				} catch (NoSuchFileException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					break;
				}
				return this;
			}
		}
		throw new RuntimeException("File Extention not supported: " + source.getName());
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
			File file = getFile();
			if (!file.exists()) {
				throw new RuntimeException("Failed to find file");
			}

			ArrayList<Object> args = new ArrayList<>();
			args.addAll(Arrays.asList(name));
			HashMap<String, Object> configs = new HashMap<String, Object>();
			configs.put("name", name);
			configs.put("PreventBomAdd", preventBoM);
			args.add(configs);
			boolean isDoodle = file.getName().toLowerCase().endsWith(".doodle");
			CSGDatabaseInstance instance = CSGDatabase.getInstance();
			if(isDoodle) {
				Path tempFile = Files.createTempFile("CSGDatabase", ".tmp");
				CSGDatabase.setInstance(new CSGDatabaseInstance(tempFile.toFile()));
			}
			List<CSG> flattenedCSGs;
			try {
				flattenedCSGs = ScriptingEngine.flaten(file, CSG.class, args);
			}catch(Throwable t) {
				t.printStackTrace();
				flattenedCSGs=new ArrayList<CSG>();
				flattenedCSGs.add(new Cube(10).toCSG().setColor(javafx.scene.paint.Color.HOTPINK));
			}
			for (int i = 0; i < flattenedCSGs.size(); i++) {
				CSG csg = flattenedCSGs.get(i);
				if(isDoodle && csg.isInGroup())
					continue;
				try {
					CSG processedCSG = processGiven(csg, i, getOrderedName(),file,name,getLocation());
					collect.add(processedCSG);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			CSGDatabase.setInstance(instance);
			for(CSG csg1:collect)
				csg1.setParameter(getFileLocationparam(file,name));
			back.addAll(collect);
		} catch (Exception e) {
			throw new RuntimeException(e);
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

	public static File toLocal(File file, String name,CaDoodleFile cf ) {
		if(cf==null)
			return file;
		File parentFileIncoming = file.getParentFile();
		String strValue = cf.getSelf().getAbsolutePath();
		File parentFile = new File(strValue).getParentFile();
		String source = parentFile.getAbsolutePath();
		boolean isDoodle = file.getName().toLowerCase().endsWith(".doodle");
		if (parentFileIncoming != null) {
			String parentIncoming = parentFileIncoming.getAbsolutePath();
			String lowerCase = parentIncoming.toLowerCase();
			String lowerCase2 = source.toLowerCase();
			boolean namesNotEqual = !lowerCase.contentEquals(lowerCase2);
			boolean exists = file.exists();
			if (namesNotEqual && exists) {
				if (!isDoodle) {
					File copied;
					try {
						copied = copyFileToNewDirectory(file, parentFile, name);
						file = copied;
					} catch (IOException e) {
						// Auto-generated catch block
						e.printStackTrace();
					}
				} else {
					// doodle copy
					File doodleParent = file.getParentFile();
					File targetParent = new File(parentFile.getAbsoluteFile() + delim() + name);
					targetParent.mkdirs();
					recursiveCopy(doodleParent.getAbsolutePath(),targetParent.getAbsolutePath());
				}
			}
		}
		if (!isDoodle)
			file = new File(source + delim() + file.getName());
		return file;
	}

	/**
	 * Recursively copies all files and folders from the source directory to the
	 * target directory.
	 * 
	 * @param sourceDir The source directory path
	 * @param targetDir The target directory path
	 * @return true if the copy operation was successful, false otherwise
	 */
	public static boolean recursiveCopy(String sourceDir, String targetDir) {
		File source = new File(sourceDir);
		File target = new File(targetDir);

		// Validate inputs
		if (!source.exists()) {
			System.err.println("Error: Source directory '" + sourceDir + "' does not exist.");
			return false;
		}

		if (!source.isDirectory()) {
			System.err.println("Error: Source '" + sourceDir + "' is not a directory.");
			return false;
		}

		// Create target directory if it doesn't exist
		if (!target.exists()) {
			if (!target.mkdirs()) {
				System.err.println("Error: Could not create target directory '" + targetDir + "'.");
				return false;
			}
			System.out.println("Created target directory: " + targetDir);
		}

		try {
			return copyDirectory(source, target);
		} catch (IOException e) {
			System.err.println("Error during copy operation: " + e.getMessage());
			return false;
		}
	}

	/**
	 * Helper method to copy a directory recursively.
	 * 
	 * @param sourceDir The source directory
	 * @param targetDir The target directory
	 * @return true if the copy operation was successful
	 * @throws IOException If an I/O error occurs
	 */
	private static boolean copyDirectory(File sourceDir, File targetDir) throws IOException {
		// Get all files and directories in the source directory
		File[] files = sourceDir.listFiles();

		if (files == null) {
			System.err.println("Warning: Could not list contents of directory: " + sourceDir.getAbsolutePath());
			return false;
		}

		for (File file : files) {
			// Construct the target path
			File targetFile = new File(targetDir, file.getName());

			if (file.isDirectory()) {
				// Create the target directory if it doesn't exist
				if (!targetFile.exists() && !targetFile.mkdirs()) {
					System.err.println("Error: Could not create directory: " + targetFile.getAbsolutePath());
					return false;
				}

				// Recursively copy the subdirectory
				if (!copyDirectory(file, targetFile)) {
					return false;
				}
			} else {
				// Copy the file, preserving attributes
				try {
					Path sourcePath = file.toPath();
					Path targetPath = targetFile.toPath();

					Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING,
							StandardCopyOption.COPY_ATTRIBUTES);

					System.out.println("Copied: " + sourcePath + " -> " + targetPath);
				} catch (IOException e) {
					System.err.println("Error copying file " + file.getAbsolutePath() + ": " + e.getMessage());
					throw e;
				}
			}
		}

		return true;
	}

	public static File getFile(String name,CaDoodleFile cf) {
		String strValue = cf.getSelf().getAbsolutePath();
		File parentFile = new File(strValue).getParentFile();
		for (File f : parentFile.listFiles()) {
			if (f.getName().contains(name)) {
				if (f.isDirectory()) {
					// is a doodle file
					for (File d : f.listFiles()) {
						if (d.getName().toLowerCase().endsWith(".doodle")) {
							return d;
						}
					}
				} else {
					for (String s : ScriptingEngine.getAllExtentions()) {
						if (f.getName().toLowerCase().endsWith(s.toLowerCase())) {
							return f;
						}
					}
				}
			}

		}
		throw new RuntimeException("File not found! " + name);
	}
//
//	private String getStrValue() {
//		
//		return getParameter("UnKnown").getStrValue();
//	}

	private static CSG processGiven(CSG csg, int i, String name, File f,String task,TransformNR location) {
		Transform nrToCSG = TransformFactory.nrToCSG(location);
		boolean isDoodle = f.getName().toLowerCase().endsWith(".doodle");
		if(isDoodle) {
			csg.setStorage(new PropertyStorage());
		}

		CSG processedCSG = csg
				.transformed(nrToCSG).syncProperties(csg).setRegenerate(previous -> {
					try {
						File file =f;
						CSGDatabaseInstance instance = CSGDatabase.getInstance();
						if(isDoodle) {
							Path tempFile = Files.createTempFile("CSGDatabase", ".tmp");
							CSGDatabase.setInstance(new CSGDatabaseInstance(tempFile.toFile()));
						}
						String fileLocation = file.getAbsolutePath();
						com.neuronrobotics.sdk.common.Log.error("Regenerating " + fileLocation);
						List<CSG> flattenedCSGs = ScriptingEngine.flaten(file, CSG.class, null);
						CSG csg1 = flattenedCSGs.get(i);
						if(isDoodle) {
							csg1.getMapOfparametrics().clear();
							csg1.setStorage(new PropertyStorage());
						}
						CSGDatabase.setInstance(instance);
						csg1.setParameter(getFileLocationparam(f,task));
						return processGiven(csg1, i, name,f,task,location);
					} catch (Exception e) {
						e.printStackTrace();
					}
					return previous;
				}).setName(name);
		MoveCenter.set(task, processedCSG, nrToCSG);
		return processedCSG;
	}

	private static StringParameter getFileLocationparam( File pathname,String task) {
		StringParameter stringParameter = new StringParameter(task + "_CaDoodle_File", pathname.getAbsolutePath(), new ArrayList<String>());
		stringParameter.setStrValue(pathname.getAbsolutePath());
		return stringParameter;
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

	public Boolean getPreventBoM() {
		return preventBoM;
	}

	public AddFromFile setPreventBoM(Boolean preventBoM) {
		this.preventBoM = preventBoM;
		return this;
	}

	@Override
	public File getFile() throws NoSuchFileException {
		return getFile(name,getCaDoodleFile());
	}
}
