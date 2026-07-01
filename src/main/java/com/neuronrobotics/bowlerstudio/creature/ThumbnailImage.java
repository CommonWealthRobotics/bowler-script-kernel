package com.neuronrobotics.bowlerstudio.creature;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Transform;
import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Affine;

public class ThumbnailImage implements ImagePorviderInterface {
	private HashMap<String, CSG> csgs = new HashMap<String, CSG>();
	private HashMap<String, MeshView> views = new HashMap<String, MeshView>();

	private int imageSize = 300;

	public Bounds getSellectedBounds(List<CSG> incomingToDisplay) {
		Vector3d min = null;
		Vector3d max = null;
		for (CSG c : incomingToDisplay) {
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

	public boolean same(CSG a, CSG b) {

		Bounds bounds = a.getBounds();
		Bounds bounds2 = b.getBounds();
		if (!bounds.getMin().epsilonEquals(bounds2.getMin(), 0.01)) {
			return false;
		}
		if (!bounds.getMax().epsilonEquals(bounds2.getMax(), 0.01)) {
			return false;
		}
		return true;
	}

	public WritableImage get(CSGDatabaseInstance instance, List<CSG> incomingToDisplay, File image)
			throws NoImageException, IOException {
		if (image.exists()) {
			BufferedImage bufferedImage = ImageIO.read(image);
			if (bufferedImage != null) {
				return SwingFXUtils.toFXImage(bufferedImage, null);
			}
		}
		try {
			if (Platform.isFxApplicationThread()) {
				throw new RuntimeException("This should not be called from the UI thread!");

			}
		} catch (Exception ex) {
			// skipping no toolkit exceptions
		}
		ArrayList<CSG> csgList = new ArrayList<CSG>();
		Bounds b = getSellectedBounds(incomingToDisplay);
		for (CSG csg : incomingToDisplay) {
			if (csg.isHide())
				continue;
			if (csg.isInGroup())
				continue;
			if (csg.hasManipulator()) {
				TransformNR nr;
				if (csg.hasManipulator())
					try {
						nr = TransformFactory.affineToNr(csg.getManipulator());
						CSG syncProperties = csg.transformed(TransformFactory.nrToCSG(nr)).syncProperties(instance,
								csg);
						syncProperties.setName(csg.getName());
						csgList.add(syncProperties);
					} catch (MissingManipulatorException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			} else
				csgList.add(csg);
		}
		ArrayList<String> toRemove = new ArrayList<String>();
		for (String s : views.keySet()) {
			boolean exists = false;
			for (CSG cs : incomingToDisplay) {
				if (cs.getName().contentEquals(s)) {
					if (same(cs, csgs.get(s))) {
						exists = true;
						break;
					}
				}
			}
			if (!exists) {
				toRemove.add(s);
			}
		}
		for (String s : toRemove) {
			views.remove(s);
			csgs.remove(s);
			Log.debug("Removing from thumbnail " + s);
		}

		// Add all meshes to the group

		double yOffset = (b.getMax().y - b.getMin().y) / 2;
		double xOffset = (b.getMax().x - b.getMin().x) / 2;
		double zCenter = (b.getMax().z - b.getMin().z) / 2;
		// Create a group to hold all the meshes
		Group root = new Group();

		for (CSG csg : csgList) {
			if (csg.isHide())
				continue;
			if (csg.isInGroup())
				continue;
			try {
				if (!views.containsKey(csg.getName())) {
					PhongMaterial material = new PhongMaterial();
					MeshView newMesh = csg.movez(-zCenter).newMesh();
					if (csg.isHole()) {
						material.setDiffuseColor(new Color(0.25, 0.25, 0.25, 0.75));
						newMesh.setMaterial(material);
						newMesh.setOpacity(0.25);
					}
					if (csg.isWireFrame())
						newMesh.setDrawMode(DrawMode.LINE);
					else
						newMesh.setDrawMode(DrawMode.FILL);
					material.setSpecularColor(material.getDiffuseColor());
					newMesh.setCullFace(CullFace.BACK);
					views.put(csg.getName(), newMesh);
					csgs.put(csg.getName(), csg);
					Log.debug("Adding to thumbnail " + csg.getName());
				}
				root.getChildren().add(views.get(csg.getName()));

			} catch (Throwable t) {
				com.neuronrobotics.sdk.common.Log.error(t);
			}
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

		TransformNR times = camoffset.times(rot.times(camDist));

		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<WritableImage> imageRef = new AtomicReference<>();
		BowlerKernel.runLater(() -> {
			Affine af = TransformFactory.nrToAffine(times);
			try {
				camera.getTransforms().add(af);
				Scene scene = new Scene(root, imageSize, imageSize, true, SceneAntialiasing.BALANCED);
				scene.setFill(Color.TRANSPARENT);
				scene.setCamera(camera);

				// Set up snapshot parameters
				SnapshotParameters params = new SnapshotParameters();
				params.setFill(Color.TRANSPARENT);
				params.setCamera(camera);
				params.setDepthBuffer(true);
				params.setTransform(Transform.scale(1, 1));
				// Set the near and far clip
				camera.setNearClip(0.1); // Set the near clip plane
				camera.setFarClip(9000.0); // Set the far clip plane
				WritableImage snapshot = new WritableImage(imageSize, imageSize);
				imageRef.set(snapshot);
				root.snapshot(params, snapshot);
				root.getChildren().clear();
			} catch (Throwable t) {
				Log.error(t);
			} finally {
				latch.countDown(); // Signal completion
			}
		});
		boolean completed = false;
		try {
			completed = latch.await(2, TimeUnit.SECONDS);
		} catch (InterruptedException e) {

		}

		if (!completed) {
			throw new NoImageException("JavaFX thread did not complete within 2 seconds");
		}

		return imageRef.get();
	}
}
