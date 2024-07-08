/**
 * 
 */
package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.legacySystemRun;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
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
	public static void addCSGToFreeccad(File freecadModel,CSG incoming) throws IOException {
		addCSGToFreeccad(freecadModel,incoming,incoming.getSlicePlanes());
	}
	public static void addCSGToFreeccad(File freecadModel,CSG toSlice, List<Transform> slicePlanes) throws IOException {
		for(Transform pose:slicePlanes) {
			List<Polygon> polygons = Slice.slice(toSlice, pose, 0);
			String name = toSlice.getName();
			if(name.length()==0)
				name="SVG_EXPORT";
			File svg = new File("/tmp/temp.svg");//File.createTempFile(name, ".svg");
			SVGExporter.export(polygons, svg, false);
			addSVGToFreeccad(freecadModel,svg,pose);
		}
		File tmp = BlenderLoader.getTmpSTL(toSlice);
		addSTLToFreecad(freecadModel,tmp,toSlice.getName());
	}
	public static void addSVGToFreeccad(File freecadModel,File SVG, Transform pose) {
		TransformNR nr=TransformFactory.csgToNR(pose);
		RotationNR r=nr.getRotation();
		File freecad = DownloadManager.getRunExecutable("freecad", null);
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
			args.add("\""+	r.getRotationMatrix2QuaturnionW()+","+
							r.getRotationMatrix2QuaturnionX()+","+
							r.getRotationMatrix2QuaturnionY()+","+
							r.getRotationMatrix2QuaturnionZ()+"\"");

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
		FreecadLoader l = new FreecadLoader();
		File test = new File("test.FCStd");
		if(!test.exists())
			l.getDefaultContents(test);
		File stlToImport =ScriptingEngine.fileFromGit(
				"https://github.com/NeuronRobotics/NASACurisoity.git"
				, "STL/upper-arm.STL");
		CSG toSlice = Vitamins.get(stlToImport,true);
		String name="upperArm";
		toSlice.setName(name);
		toSlice.addSlicePlane(new Transform());
		FreecadLoader.addCSGToFreeccad(test, toSlice);


		//FreecadLoader.addSTLToFreecad(test,stlToImport,name);
		//FreecadLoader.toSTLFile(test, new File("testFreecad.stl"));
		FreecadLoader.open(test);
		
	}

}
