package com.neuronrobotics.bowlerstudio.creature;

import java.util.List;

import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Transform;
import javafx.scene.PerspectiveCamera;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Rotate;
import javafx.geometry.Rectangle2D;

public class ThumbnailImage {
	public static Bounds getSellectedBounds(List<CSG> incoming) {
		Vector3d min = null;
		Vector3d max = null;
		for (CSG c : incoming) {
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

		return new Bounds(min, max);
	}

	public static WritableImage get(List<CSG> csgList) {
		// Create a group to hold all the meshes
		Group root = new Group();

		// Add all meshes to the group
		Bounds b = getSellectedBounds(csgList);

		double yOffset = (b.getMax().y-b.getMin().y)/2;
		double xOffset =(b.getMax().x -b.getMin().x)/2;
		for (CSG csg : csgList) {
			MeshView meshView = csg.getMesh();
			if (csg.isHole()) {
				PhongMaterial material = new PhongMaterial();
				material.setDiffuseColor(new Color(0.25, 0.25, 0.25, 0.75));
				material.setSpecularColor(javafx.scene.paint.Color.WHITE);
				meshView.setMaterial(material);
				meshView.setOpacity(0.25);
			}
			root.getChildren().add(meshView);
		}

		// Calculate the bounds of all CSGs combined
		double totalz = b.getMax().z - b.getMin().z;
		double totaly = b.getMax().y - b.getMin().y;
		double totalx = b.getMax().x - b.getMin().x;

		// Create a perspective camera
		PerspectiveCamera camera = new PerspectiveCamera(true);

		// Calculate camera position to fit all objects in view
		double maxDimension = Math.max(totalx, Math.max(totaly, totalz));
		double cameraDistance = (maxDimension / Math.tan(Math.toRadians(camera.getFieldOfView() / 2)))*0.9 ;

		TransformNR camoffset = new TransformNR(xOffset, yOffset, 0);
		TransformNR camDist = new TransformNR(0, 0, -cameraDistance);
		TransformNR rot = new TransformNR(new RotationNR(-155, 45, 0));
		
		Affine af = TransformFactory.nrToAffine(camoffset.times(rot.times(camDist)));
		camera.getTransforms().add(af);
		// Position the camera
//	    camera.setTranslateX();
//	    camera.setTranslateY();
//		camera.setTranslateZ();
//		   // Apply rotations to the root group instead of the camera
//	    root.getTransforms().addAll(
//	            new Rotate(-5, Rotate.Y_AXIS),
//	            new Rotate(-45, Rotate.X_AXIS)
//	    );
		// Create a scene with the group and camera
		int i = 1000;
		Scene scene = new Scene(root, i, i, true, SceneAntialiasing.BALANCED);
		scene.setFill(Color.TRANSPARENT);
		scene.setCamera(camera);

		// Set up snapshot parameters
		SnapshotParameters params = new SnapshotParameters();
		params.setFill(Color.TRANSPARENT);
		params.setCamera(camera);
		params.setDepthBuffer(true);
		params.setTransform(Transform.scale(1, 1));
		// Set the near and far clip
		camera.setNearClip(0.1);  // Set the near clip plane
		camera.setFarClip(9000.0);  // Set the far clip plane


		// Create the WritableImage first
		WritableImage snapshot = new WritableImage(i, i);

		root.snapshot(params, snapshot);

		return snapshot;
	}
}
