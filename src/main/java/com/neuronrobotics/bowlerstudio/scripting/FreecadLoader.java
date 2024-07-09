/**
 * 
 */
package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.legacySystemRun;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

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
		File freecad = DownloadManager.getRunExecutable("freecad", null);
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
		File tmp = BlenderLoader.getTmpSTL(toSlice);
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
				File svg = new File("/tmp/temp.svg");//File.createTempFile(svgName, ".svg");
				SVGExporter.export(polygons, svg, false);
				addSVGToFreeCAD(freecadModel,svg,pose,svgName,name+"_body");
				planes++;
			}
		addSTLToFreecad(freecadModel,tmp,name);
	}
	public static File simplifySVG(File incoming, double threshhold) {
		try {
			File inkscape = DownloadManager.getRunExecutable("inkscape", null);
			File svg = File.createTempFile(incoming.getName(), ".svg");
			List <String >args = Arrays.asList(
					inkscape.getAbsolutePath(),
		            "--actions",
		            "\"select-all:all;path-simplify:threshold=" + threshhold+ ";;export-overwrite;export-do;quit-inkscape\"",
		            incoming.getAbsolutePath()
					);
			legacySystemRun(null, inkscape.getAbsoluteFile().getParentFile(), System.out, args);
			args = Arrays.asList(
					inkscape.getAbsolutePath(),
					"--export-plain-svg",
		            "--export-type=svg",
		            "--vacuum-defs",
		            "--export-filename="+svg.getAbsolutePath(),
		            incoming.getAbsolutePath()
					);
			legacySystemRun(null, inkscape.getAbsoluteFile().getParentFile(), System.out, args);
			return svg;
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return incoming;
	}
	public static void addSVGToFreeCAD(File freecadModel,File SVG, Transform pose, String name, String bodyName) {
		TransformNR nr=TransformFactory.csgToNR(pose);
		RotationNR r=nr.getRotation();
		File freecad = DownloadManager.getRunExecutable("freecad", null);
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
		File freecad = DownloadManager.getRunExecutable("freecad", null);
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
		File freecad = DownloadManager.getRunExecutable("freecad", null);
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

			args.add(freecad.getAbsolutePath());
			args.add(freecadModel.getAbsolutePath());

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
		toSlice.addSlicePlane(new Transform().movez(toSlice.getMaxZ()));
		toSlice.addSlicePlane(new Transform().movez(toSlice.getMaxZ()-0.51));
		toSlice.addSlicePlane(new Transform());
		FreecadLoader.addCSGToFreeCAD(test, toSlice);


		FreecadLoader.open(test);
		System.exit(0);
	}

}
