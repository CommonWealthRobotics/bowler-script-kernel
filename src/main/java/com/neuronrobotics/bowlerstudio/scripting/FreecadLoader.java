/**
 * 
 */
package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Slice;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.svg.SVGExporter;
import javafx.scene.paint.Color;

/**
 * 
 */
public class FreecadLoader implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		File stl = File.createTempFile(code.getName(), ".stl");
		stl.deleteOnExit();
		toSTLFile(code,stl);
		CSG back = Vitamins.get(stl,true);
		back.setColor(Color.BLUE);
		return back;
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		throw new RuntimeException("Freecad file can not be instantiated from a string");
	}

	@Override
	public String getShellType() {
		return "FreeCAD";
	}

	@Override
	public ArrayList<String> getFileExtenetion() {
		ArrayList<String> ext = new ArrayList<>();
		ext.add("FCStd");
		return ext;
	}

	@Override
	public boolean getIsTextFile() {
		return false;
	}
	
	@Override
	public void getDefaultContents(File freecadGenFile) {
		File freecad = DownloadManager.getConfigExecutable("freecad", null);
		try {
			File newFile = ScriptingEngine.fileFromGit(
					"https://github.com/CommonWealthRobotics/freecad-bowler-cli.git", 
					"newFile.py");
			ArrayList<String> args = new ArrayList<>();
	
			if(freecadGenFile.exists())
				freecadGenFile.delete();
			args.add(freecad.getAbsolutePath());
	
			args.add("-c");
			args.add(newFile.getAbsolutePath());
			args.add(freecadGenFile.getAbsolutePath());
			legacySystemRun(null, freecadGenFile.getAbsoluteFile().getParentFile(), System.out, args);
		}catch(Throwable t) {
			t.printStackTrace();
		}
		
	}
	public static void addCSGToFreeCAD(File freecadModel,CSG incoming) throws IOException {
		addCSGToFreeCAD(freecadModel,incoming,incoming.getSlicePlanes());
	}
	public static void addCSGToFreeCAD(File freecadModel,CSG toSlice, List<Transform> slicePlanes) throws IOException {
		File tmp =getTmpSTL(toSlice);
		String name = toSlice.getName();
		if(name.length()==0) {
			name="CSG_TO_FREECAD";
		}
		int planes=1;
		if(slicePlanes!=null)
			for(Transform pose:slicePlanes) {
				List<Polygon> polygons = Slice.slice(toSlice, pose, 0);
				String svgName = toSlice.getName();
				if(svgName.length()==0)
					svgName="SVG_EXPORT";
				svgName+="_"+planes;
				File svg = File.createTempFile(svgName, ".svg");
				SVGExporter.export(polygons, svg, false);
				addSVGToFreeCAD(freecadModel,svg,pose,svgName,name+"_body");
				planes++;
			}
		addSTLToFreecad(freecadModel,tmp,name);
	}

	public static void addSVGToFreeCAD(File freecadModel,File SVG, Transform pose, String name, String bodyName) {
		TransformNR nr=TransformFactory.csgToNR(pose);
		RotationNR r=nr.getRotation();
		File freecad = DownloadManager.getConfigExecutable("freecad", null);
		//SVG=simplifySVG(SVG,0.002);
		
		try {
			File export = ScriptingEngine.fileFromGit(
					"https://github.com/CommonWealthRobotics/freecad-bowler-cli.git", 
					"importSVGToPose.py");
			ArrayList<String> args = new ArrayList<>();

			args.add(freecad.getAbsolutePath());
	
			args.add("-c");
			args.add(export.getAbsolutePath());
			args.add(freecadModel.getAbsolutePath());
			args.add(SVG.getAbsolutePath());
			args.add("\""+nr.getX()+","+nr.getY()+","+nr.getZ()+"\"");
			args.add("\""+	Math.toDegrees(r.getRotationAzimuth())+","+
							Math.toDegrees(r.getRotationElevation())+","+
							Math.toDegrees(r.getRotationTilt())+"\"");
			args.add(name);
			args.add(bodyName);
			legacySystemRun(null, export.getAbsoluteFile().getParentFile(), System.out, args);
		}catch(Throwable t) {
			t.printStackTrace();
		}
	}
	public static void addSTLToFreecad(File freecadModel, File stlToAdd,String meshName) {
		File freecad = DownloadManager.getConfigExecutable("freecad", null);
		try {
			File export = ScriptingEngine.fileFromGit(
					"https://github.com/CommonWealthRobotics/freecad-bowler-cli.git", 
					"importSTL.py");
			ArrayList<String> args = new ArrayList<>();

			args.add(freecad.getAbsolutePath());
	
			args.add("-c");
			args.add(export.getAbsolutePath());
			args.add(freecadModel.getAbsolutePath());
			args.add(stlToAdd.getAbsolutePath());
			args.add(meshName);
			legacySystemRun(null, export.getAbsoluteFile().getParentFile(), System.out, args);
		}catch(Throwable t) {
			t.printStackTrace();
		}
	}
	public static void toSTLFile(File freecadModel,File stlout) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		File freecad = DownloadManager.getConfigExecutable("freecad", null);
		try {
			File export = ScriptingEngine.fileFromGit(
					"https://github.com/CommonWealthRobotics/freecad-bowler-cli.git", 
					"export.py");
			ArrayList<String> args = new ArrayList<>();

			args.add(freecad.getAbsolutePath());
	
			args.add("-c");
			args.add(export.getAbsolutePath());
			args.add(freecadModel.getAbsolutePath());
			args.add(stlout.getAbsolutePath());

			legacySystemRun(null, export.getAbsoluteFile().getParentFile(), System.out, args);
		}catch(Throwable t) {
			t.printStackTrace();
		}
	}
	public static void open(File freecadModel) {
		File freecad = DownloadManager.getRunExecutable("freecad", null);

		try {

			ArrayList<String> args = new ArrayList<>();
			if(isMac()) {
				args.add("open");
				args.add("-a");
			}

			args.add(freecad.getAbsolutePath());
			args.add(freecadModel.getAbsolutePath());
			if(isMac())
				advancedSystemRun(null, freecadModel.getAbsoluteFile().getParentFile(), System.out, args);
			else
				legacySystemRun(null, freecadModel.getAbsoluteFile().getParentFile(), System.out, args);
		}catch(Throwable t) {
			t.printStackTrace();
		}
	}
	/**
	 * @param args
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws GitAPIException 
	 * @throws TransportException 
	 * @throws InvalidRemoteException 
	 */
	public static void main(String[] args) throws InvalidRemoteException, TransportException, GitAPIException, IOException, InterruptedException {
		JavaFXInitializer.go();
		PasswordManager.login();
		FreecadLoader l = new FreecadLoader();
		File test = new File("test.FCStd");
		test.delete();
		if(!test.exists())
			l.getDefaultContents(test);
		File stlToImport =ScriptingEngine.fileFromGit(
				"https://github.com/NeuronRobotics/NASACurisoity.git"
				, "STL/upper-arm.STL");
		CSG toSlice = Vitamins.get(stlToImport,true);
//		toSlice=toSlice.union(
//					new Cube(20).toCSG()
//						.toXMin()
//						.toYMin()
//						.toZMin()
//					);
		String name="upperArm";
		toSlice.setName(name);
		toSlice.addSlicePlane(new Transform().rotY(90).movex(30));
		toSlice.addSlicePlane(new Transform().movez(toSlice.getMaxZ()-0.5));
		toSlice.addSlicePlane(new Transform().movez(toSlice.getMaxZ()-0.51));
		toSlice.addSlicePlane(new Transform());
		FreecadLoader.addCSGToFreeCAD(test, toSlice);


		FreecadLoader.open(test);
		System.exit(0);
	}
	private static String readAll(Reader rd) throws IOException {
		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}
	public static void update(Map<String, Object> vm) throws MalformedURLException, IOException {
		String url= "https://api.github.com/repos/FreeCAD/FreeCAD-Bundle/releases/tags/1.0rc2";
		InputStream is = new URL(url).openStream();
		String type = vm.get("type").toString();

		try {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
			String jsonText = readAll(rd);
			// Create the type, this tells GSON what datatypes to instantiate when parsing
			// and saving the json
			Type TT_mapStringString = new TypeToken<HashMap<String, Object>>() {
			}.getType();
			// chreat the gson object, this is the parsing factory
			Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
			HashMap<String, Object> database = gson.fromJson(jsonText, TT_mapStringString);
			@SuppressWarnings("unchecked")
			List<Map<String, Object>> assets = (List<Map<String, Object>>) database.get("assets");
			for (Map<String, Object> key : assets) {
				String assetName = key.get("name").toString();
				if(!assetName.endsWith(type))
					continue;
				if(isLin()) {
					if(!assetName.toLowerCase().contains("linux"))
						continue;
					if(isArm() && !assetName.toLowerCase().contains("aarch64"))
						continue;
					if(!isArm() && assetName.toLowerCase().contains("aarch64"))
						continue;
				}
				if(isWin()) {
					if(!assetName.toLowerCase().contains("windows"))
						continue;
				}
				if(isMac()) {
					if(!assetName.toLowerCase().contains("macos"))
						continue;
					
					if(isArm() && !assetName.toLowerCase().contains("arm64"))
						continue;
					if(!isArm() && assetName.toLowerCase().contains("arm64"))
						continue;
				}
				String name = assetName.replace("."+type, "");
				System.out.println("Updating Freecad assets to "+name);
				vm.put("name",name);
				if(isMac())
					continue;
				if(isWin()) {
					vm.put("executable",name+delim()+"bin"+delim()+"freecad.exe");
					vm.put("configExecutable",name+delim()+"bin"+delim()+"freecad.exe");
					continue;
				}
				vm.put("executable",name+"."+type);
				vm.put("configExecutable",name+"."+type);
			}
		} finally {
			is.close();
		}
	}

}
