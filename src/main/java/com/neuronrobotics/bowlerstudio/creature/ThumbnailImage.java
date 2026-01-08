package com.neuronrobotics.bowlerstudio.creature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Transform;
import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Affine;

public class ThumbnailImage {
	private HashMap<String, CSG> csgs = new HashMap<String, CSG>();
	private HashMap<String, MeshView> views = new HashMap<String, MeshView>();
	// Create a group to hold all the meshes
	private Group root = new Group();
	private Scene scene;

	public Bounds getSellectedBounds() {
		Vector3d min = null;
		Vector3d max = null;
		for (CSG c : csgs.values()) {
			if (c.isHide())
				continue;
			if (c.isInGroup())
				continue;
			Vector3d min2 = c.getBounds().getMin().clone();
			Vector3d max2 = c.getBounds().getMax().clone();
			if (min == null)
				min = min2;
			if (max == null)
				max = max2;
			if (min2.x < min.x)
				min.x = min2.x;
			if (min2.y < min.y)
				min.y = min2.y;
			if (min2.z < min.z)
				min.z = min2.z;
			if (max.x < max2.x)
				max.x = max2.x;
			if (max.y < max2.y)
				max.y = max2.y;
			if (max.z < max2.z)
				max.z = max2.z;
		}
		if (max == null)
			max = new Vector3d(0, 0, 0);
		if (min == null)
			min = new Vector3d(0, 0, 0);
		return new Bounds(min, max);
	}

	public WritableImage get(CSGDatabaseInstance instance, List<CSG> c) {
		ArrayList<CSG> csgList = new ArrayList<CSG>();
		for (CSG cs : c) {
			if (csgs.containsKey(cs.getName()))
				continue;
			csgs.put(cs.getName(), cs);
			if (cs.hasManipulator()) {
				TransformNR nr;
				try {
					nr = TransformFactory.affineToNr(cs.getManipulator());
					csgList.add(cs.transformed(TransformFactory.nrToCSG(nr)).syncProperties(instance, cs));
				} catch (MissingManipulatorException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else
				csgList.add(cs);
		}
		ArrayList<String> toRemove = new ArrayList<String>();
		for (String s : csgs.keySet()) {
			boolean exists = false;
			for (CSG cs : c) {
				if (cs.getName().contentEquals(s))
					exists = true;
			}
			if (!exists) {
				toRemove.add(s);
			}
		}
		for (String s : toRemove) {
			csgs.remove(s);
			MeshView mv = views.remove(s);
			if (mv != null)
				root.getChildren().remove(mv);
			Log.debug("Removing from thumbnail " + s);
		}

		// Add all meshes to the group
		Bounds b = getSellectedBounds();

		double yOffset = (b.getMax().y - b.getMin().y) / 2;
		double xOffset = (b.getMax().x - b.getMin().x) / 2;
		double zCenter = (b.getMax().z - b.getMin().z) / 2;
		for (CSG csg : csgList) {
			if (csg.isHide())
				continue;
			if (csg.isInGroup())
				continue;
			try {
				MeshView meshView = csg.movez(-zCenter).getMesh();
				views.put(csg.getName(), meshView);
				PhongMaterial material = new PhongMaterial();
				if (csg.isHole()) {
					material.setDiffuseColor(new Color(0.25, 0.25, 0.25, 0.75));
					meshView.setMaterial(material);
					meshView.setOpacity(0.25);
				}
				material.setSpecularColor(javafx.scene.paint.Color.WHITE);
				meshView.setCullFace(CullFace.BACK);
				root.getChildren().add(meshView);
				Log.debug("Adding to thumbnail " + csg.getName());

			} catch (Throwable t) {
				com.neuronrobotics.sdk.common.Log.error(t);
			}
		}
		if (root.getChildren().size() == 0) {
			Log.error("Thumbnail is empty!");
		}

		// Calculate the bounds of all CSGs combined
		double totalz = b.getMax().z - b.getMin().z;
		double totaly = b.getMax().y - b.getMin().y;
		double totalx = b.getMax().x - b.getMin().x;

		// Create a perspective camera
		PerspectiveCamera camera = new PerspectiveCamera(true);

		// Calculate camera position to fit all objects in view
		double maxDimension = Math.max(totalx, Math.max(totaly, totalz));
		double cameraDistance = (maxDimension / Math.tan(Math.toRadians(camera.getFieldOfView() / 2))) * 0.8;

		TransformNR camoffset = new TransformNR(xOffset, yOffset, 0);
		TransformNR camDist = new TransformNR(0, 0, -cameraDistance);
		TransformNR rot = new TransformNR(new RotationNR(-150, 45, 0));

		Affine af = TransformFactory.nrToAffine(camoffset.times(rot.times(camDist)));
		camera.getTransforms().add(af);
		int i = 100;
		if (scene == null) {
			scene = new Scene(root, i, i, true, SceneAntialiasing.BALANCED);
			scene.setFill(Color.TRANSPARENT);
			scene.setCamera(camera);
		}
		// Set up snapshot parameters
		SnapshotParameters params = new SnapshotParameters();
		params.setFill(Color.TRANSPARENT);
		params.setCamera(camera);
		params.setDepthBuffer(true);
		params.setTransform(Transform.scale(1, 1));
		// Set the near and far clip
		camera.setNearClip(0.1); // Set the near clip plane
		camera.setFarClip(9000.0); // Set the far clip plane

		// Create the WritableImage first
		WritableImage snapshot = new WritableImage(i, i);

		root.snapshot(params, snapshot);

		return snapshot;
	}
}
