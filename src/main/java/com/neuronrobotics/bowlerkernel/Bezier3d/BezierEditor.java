package com.neuronrobotics.bowlerkernel.Bezier3d;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
//import com.neuronrobotics.bowlerstudio.BowlerStudio
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;
import javafx.geometry.Point3D;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import eu.mihosoft.vrl.v3d.*;
import javafx.scene.shape.Line;

public class BezierEditor {
	Type TT_mapStringString = new TypeToken<HashMap<String, HashMap<String, List<Double>>>>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	File cachejson;
	TransformNR end = new TransformNR();
	TransformNR cp1 = new TransformNR();
	TransformNR cp2 = new TransformNR();
	TransformNR strt = new TransformNR();
	private ArrayList<CSG> partsInternal = null;
	CSG displayPart = new Cylinder(5, 0, 20, 10).toCSG().toZMax().roty(-90);

	private CartesianManipulator endManip;
	CartesianManipulator cp1Manip;
	CartesianManipulator cp2Manip;
	private CartesianManipulator start;
	HashMap<String, HashMap<String, List<Double>>> database;
	boolean updating = false;
	private String url;
	private String gitfile;
	private boolean saving;
	private ArrayList<BezierEditor> nextBez = new ArrayList<>();
	private Line cp1Line = new Line();
	private Line cp2Line = new Line();
	private Affine cp1LinePose = new Affine();
	private Affine cp2LinePose = new Affine();

	public BezierEditor(String URL, String file, int numPoints)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		this(ScriptingEngine.fileFromGit(URL, file), numPoints);
		url = URL;
		gitfile = file;
	}

	public BezierEditor(File data, int numPoints) {
		cachejson = data;
		String jsonString = null;
		boolean loaded = false;
		try {
			if (cachejson.exists()) {
				InputStream inPut = null;
				inPut = FileUtils.openInputStream(cachejson);
				jsonString = IOUtils.toString(inPut);
				database = gson.fromJson(jsonString, TT_mapStringString);

				List<Double> cp1in = (List<Double>) database.get("bezier").get("control one");
				List<Double> cp2in = (List<Double>) database.get("bezier").get("control two");
				List<Double> ep = (List<Double>) database.get("bezier").get("end point");
				List<Double> st = (List<Double>) database.get("bezier").get("start point");
				end.setX(ep.get(0));
				end.setY(ep.get(1));
				end.setZ(ep.get(2));
				cp1.setX(cp1in.get(0));
				cp1.setY(cp1in.get(1));
				cp1.setZ(cp1in.get(2));
				cp2.setX(cp2in.get(0));
				cp2.setY(cp2in.get(1));
				cp2.setZ(cp2in.get(2));

				strt.setX(st.get(0));
				strt.setY(st.get(1));
				strt.setZ(st.get(2));
				loaded = true;
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}

		if (!loaded) {
			end.setX(100);
			end.setY(100);
			;
			end.setZ(100);
			cp1.setX(50);
			cp1.setY(-50);
			cp1.setZ(50);
			cp2.setX(0);
			cp2.setY(50);
			cp2.setZ(-50);

			database = new HashMap<>();
		}

		endManip = new CartesianManipulator(end);
		cp1Manip = new CartesianManipulator(cp1);

		cp2Manip = new CartesianManipulator(cp2);
		endManip.addDependant(cp2Manip);
		setStartManip(new CartesianManipulator(strt));

		endManip.addSaveListener(() -> {
			save();
		});
		endManip.addEventListener(() -> {
			update();
		});

		cp1Manip.addSaveListener(() -> {
			save();
		});
		cp1Manip.addEventListener(() -> {
			update();
		});

		cp2Manip.addSaveListener(() -> {
			save();
		});
		cp2Manip.addEventListener(() -> {
			update();
		});
		ArrayList<CSG> parts = new ArrayList<>();
		for (int i = 0; i < numPoints; i++) {
			CSG part = displayPart.clone();
			parts.add(part);
		}
		setPartsInternal(parts);
		cp1Line.setStrokeWidth(5);
		cp2Line.setStrokeWidth(5);

		cp2Line.getTransforms().addAll(endManip.manipulationMatrix, cp2LinePose);
		update();
		save();
	}
	public ArrayList<CSG> getCSG() {

		ArrayList<CSG> back = new ArrayList<>();
		back.addAll(getEndManip().get());
		back.addAll(cp1Manip.get());
		back.addAll(cp2Manip.get());
		back.addAll(getStartManip().get());
		back.addAll(getPartsInternal());

		return back;
	}
	public ArrayList<Object> get() {

		ArrayList<Object> back = new ArrayList<>();
		back.addAll(getCSG());
		back.add(cp2Line);
		back.add(cp1Line);

		return back;
	}

	public void update() {
		if (updating) {
			return;
		}
		updating = true;
		ArrayList<Transform> transforms = transforms();
		for (int i = 0; i < getNumParts(); i++) {
			TransformNR nr = TransformFactory.csgToNR(transforms.get(i));
			Affine partsGetGetManipulator = getPartsInternal().get(i).getManipulator();
			BowlerKernel.runLater(() -> {
				TransformFactory.nrToAffine(nr, partsGetGetManipulator);
			});
		}

		updateLines(start, cp1Manip, cp1Line, cp1LinePose);

		TransformNR endVector = updateLines(endManip, cp2Manip, cp2Line, cp2LinePose);
		if (!endManip.isMoving())
			for (BezierEditor b : nextBez) {
				CartesianManipulator m = b.cp1Manip;
				CartesianManipulator p = endManip;

				double cp1XDiff = m.getX() - p.getX();
				double cp1Ydiff = m.getY() - p.getY();
				double cp1ZDiff = m.getZ() - p.getZ();
				double distCP1 = Math.sqrt(Math.pow(cp1XDiff, 2) + Math.pow(cp1Ydiff, 2) + Math.pow(cp1ZDiff, 2));
				TransformNR vect = endVector.times(new TransformNR(0, distCP1, 0, new RotationNR()));
				double newX = vect.getX() + p.getX();
				double newY = vect.getY() + p.getY();
				double newZ = vect.getZ() + p.getZ();
				m.set(newX, newY, newZ);

			}

		updating = false;
	}

	private TransformNR updateLines(CartesianManipulator m, CartesianManipulator p, Line l, Affine poseAF) {
		double cp1XDiff = m.getX() - p.getX();
		double cp1Ydiff = m.getY() - p.getY();
		double cp1ZDiff = m.getZ() - p.getZ();
		TransformNR vect = new TransformNR(cp1XDiff, cp1Ydiff, cp1ZDiff, new RotationNR());

		double distCP1 = Math.sqrt(Math.pow(cp1XDiff, 2) + Math.pow(cp1Ydiff, 2) + Math.pow(cp1ZDiff, 2));
		double xyRot = Math.toDegrees(Math.atan2(cp1Ydiff, cp1XDiff)) - 90;

		TransformNR az = new TransformNR(0, 0, 0, new RotationNR(0, -xyRot, 0));
		TransformNR reorented = az.times(vect);
		// System.out.println("CP1 "+reorented.getX()+" "+reorented.getY()+"
		// "+reorented.getZ());
		double xzRot = Math.toDegrees(Math.atan2(reorented.getZ(), reorented.getY()));

		TransformNR pose = new TransformNR(0, 0, 0, new RotationNR(xzRot, xyRot, 0));

		Platform.runLater(() -> {
			TransformFactory.nrToAffine(pose, poseAF);

			l.setStartX(0);
			l.setStartY(0);
			l.setEndY(-distCP1);
			l.setEndX(0);
		});
		return pose;
	}

	public void setEnd(double newX, double newY, double newZ) {
		endManip.set(newX, newY, newZ);
		save();
	}

	public void setStart(double newX, double newY, double newZ) {
		getStartManip().set(newX, newY, newZ);
		save();
		
	}

	public void setCP1(double newX, double newY, double newZ) {
		cp1Manip.set(newX, newY, newZ);
		save();
		
	}

	public void setCP2(double newX, double newY, double newZ) {
		cp2Manip.set(newX, newY, newZ);
		save();
	}

	public void setStart(Vector3d point) {
		this.setStart(point.x, point.y, point.z);
	}

	public void setCP1(Vector3d point) {
		this.setCP1(point.x, point.y, point.z);
	}

	public void setCP2(Vector3d point) {
		this.setCP2(point.x, point.y, point.z);
	}

	public void setEnd(Vector3d point) {
		this.setEnd(point.x, point.y, point.z);
	}

	public ArrayList<Transform> transforms() {
		ArrayList<Transform> tf = Extrude.bezierToTransforms(
				new Vector3d(cp1Manip.getX() - getStartManip().getX(), cp1Manip.getY() - getStartManip().getY(),
						cp1Manip.getZ() - getStartManip().getZ()), // Control point one
				new Vector3d(cp2Manip.getX() - getStartManip().getX(), cp2Manip.getY() - getStartManip().getY(),
						cp2Manip.getZ() - getStartManip().getZ()), // Control point two
				new Vector3d(getEndManip().getX() - getStartManip().getX(),
						getEndManip().getY() - getStartManip().getY(), getEndManip().getZ() - getStartManip().getZ()), // Endpoint
				getNumParts()// Iterations
		);

		for (int i = 0; i < tf.size(); i++) {
			tf.set(i, tf.get(i).movex(getStartManip().getX()).movey(getStartManip().getY())
					.movez(getStartManip().getZ()));
		}
		return tf;
	}

	private int getNumParts() {
		return getPartsInternal().size();
	}

	public void save() {
		if (saving)
			return;
		saving = true;
		database.clear();
		HashMap<String, List<Double>> bezData = new HashMap<>();

		bezData.put("control one", Arrays.asList(cp1Manip.getX(), cp1Manip.getY(), cp1Manip.getZ()));
		bezData.put("control two", Arrays.asList(cp2Manip.getX(), cp2Manip.getY(), cp2Manip.getZ()));
		bezData.put("end point", Arrays.asList(getEndManip().getX(), getEndManip().getY(), getEndManip().getZ()));
		bezData.put("start point",
				Arrays.asList(getStartManip().getX(), getStartManip().getY(), getStartManip().getZ()));
		bezData.put("number of points", Arrays.asList((double) getNumParts()));
		database.put("bezier", bezData);

		new Thread(() -> {
			System.out.println("Saving to file " + cachejson.getAbsolutePath());
			String writeOut = gson.toJson(database, TT_mapStringString);
			if (url != null) {
				try {
					ScriptingEngine.pushCodeToGit(url, ScriptingEngine.getFullBranch(url), gitfile, writeOut,
							"Saving Bezier");
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				if (!cachejson.exists())
					try {
						cachejson.createNewFile();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				OutputStream out = null;
				try {
					out = FileUtils.openOutputStream(cachejson, false);
					IOUtils.write(writeOut, out);
					out.close(); // don't swallow close Exception if copy
					// completes
					// normally
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} finally {
					IOUtils.closeQuietly(out);
				}
			}
			saving = false;
		}).start();
		for (BezierEditor b : nextBez)
			b.save();
	}

	public CartesianManipulator getEndManip() {
		return endManip;
	}

//	private void setEndManip(CartesianManipulator endManip) {
//		this.endManip = endManip;
//	}
	public CartesianManipulator getStartManip() {
		return start;
	}

	public void setStartManip(CartesianManipulator start) {
		if (this.start != null)
			this.start.clearListeners();
		this.start = start;
		start.addSaveListener(() -> {
			save();
		});
		start.addEventListener(() -> {
			update();
		});
		start.addDependant(cp1Manip);
		cp1Line.getTransforms().clear();
		cp1Line.getTransforms().addAll(start.manipulationMatrix, cp1LinePose);

	}

	public void addBezierToTheEnd(BezierEditor b) {
		nextBez.add(b);
		b.setStartManip(endManip);
	}

	public ArrayList<CSG> getPartsInternal() {
		return partsInternal;
	}

	public void setPartsInternal(ArrayList<CSG> partsInternal) {
		this.partsInternal = partsInternal;
		for (int i = 0; i < partsInternal.size(); i++) {
			partsInternal.get(i).setManipulator(new Affine());
			partsInternal.get(i).setMfg(incoming -> null);
			partsInternal.get(i).getStorage().set("skeleton", true);
		}
		update();
	}
}