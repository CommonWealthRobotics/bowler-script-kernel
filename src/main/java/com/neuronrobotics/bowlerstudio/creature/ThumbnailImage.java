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
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import javafx.application.Platform;
import javafx.geometry.Point3D;
import javafx.scene.AmbientLight;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.DrawMode;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Affine;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;

public class ThumbnailImage implements ImagePorviderInterface {
	public static final String CACHE_VERSION = "11";
	private static final int THUMBNAIL_MARGIN = 24;
	private static final double CAMERA_AZIMUTH = -40.0;
	private static final double CAMERA_ELEVATION = 31.0;

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
		if (a.isHole() != b.isHole())
			return false;
		if (a.isWireFrame() != b.isWireFrame())
			return false;
		double EPS = 1e-9;

		Color ca = a.getColor();
		Color cb = b.getColor();
		if (Math.abs(ca.getRed() - cb.getRed()) >= EPS || Math.abs(ca.getGreen() - cb.getGreen()) >= EPS
				|| Math.abs(ca.getBlue() - cb.getBlue()) >= EPS || Math.abs(ca.getOpacity() - cb.getOpacity()) >= EPS) {
			return false;
		}
		return true;
	}

	private static Affine thumbnailView(javafx.geometry.Bounds bounds, double distance) {
		double azimuth = Math.toRadians(CAMERA_AZIMUTH);
		double elevation = Math.toRadians(CAMERA_ELEVATION);

		Point3D cameraDirection = new Point3D(Math.cos(elevation) * Math.cos(azimuth),
				Math.cos(elevation) * Math.sin(azimuth), Math.sin(elevation));
		Point3D forward = cameraDirection.multiply(-1);
		Point3D right = forward.crossProduct(new Point3D(0, 0, 1)).normalize();
		Point3D up = right.crossProduct(forward).normalize();
		Point3D center = new Point3D((bounds.getMinX() + bounds.getMaxX()) / 2,
				(bounds.getMinY() + bounds.getMaxY()) / 2, (bounds.getMinZ() + bounds.getMaxZ()) / 2);

		Affine view = new Affine();
		view.setMxx(right.getX());
		view.setMxy(right.getY());
		view.setMxz(right.getZ());
		view.setTx(-right.dotProduct(center));

		view.setMyx(-up.getX());
		view.setMyy(-up.getY());
		view.setMyz(-up.getZ());
		view.setTy(up.dotProduct(center));

		view.setMzx(forward.getX());
		view.setMzy(forward.getY());
		view.setMzz(forward.getZ());
		view.setTz(distance - forward.dotProduct(center));
		return view;
	}

	private WritableImage fitThumbnail(WritableImage source) {
		PixelReader reader = source.getPixelReader();
		int minX = imageSize;
		int minY = imageSize;
		int maxX = -1;
		int maxY = -1;

		for (int y = 0; y < imageSize; y++) {
			for (int x = 0; x < imageSize; x++) {
				int alpha = (reader.getArgb(x, y) >>> 24) & 0xff;
				if (alpha <= 2)
					continue;

				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}
		}

		if (maxX < minX || maxY < minY)
			return source;

		double sourceWidth = maxX - minX + 1;
		double sourceHeight = maxY - minY + 1;
		double available = imageSize - THUMBNAIL_MARGIN * 2.0;
		double scale = Math.min(available / sourceWidth, available / sourceHeight);
		double width = sourceWidth * scale;
		double height = sourceHeight * scale;

		Canvas canvas = new Canvas(imageSize, imageSize);
		GraphicsContext graphics = canvas.getGraphicsContext2D();
		graphics.drawImage(source, minX, minY, sourceWidth, sourceHeight, (imageSize - width) / 2,
				(imageSize - height) / 2, width, height);

		SnapshotParameters params = new SnapshotParameters();
		params.setFill(Color.TRANSPARENT);

		WritableImage result = new WritableImage(imageSize, imageSize);
		canvas.snapshot(params, result);
		return result;
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

		double zCenter = (b.getMax().z - b.getMin().z) / 2;
		Group content = new Group();

		for (CSG csg : csgList) {
			if (csg.isHide())
				continue;
			if (csg.isInGroup())
				continue;
			try {
				if (!views.containsKey(csg.getName())) {
					MeshView newMesh = csg.movez(-zCenter).newMesh();

					if (csg.isHole()) {
						PhongMaterial material = new PhongMaterial(new Color(0.25, 0.25, 0.25, 0.75));
						material.setSpecularColor(Color.BLACK);
						newMesh.setMaterial(material);
						newMesh.setOpacity(0.25);
					} else if (newMesh.getMaterial() instanceof PhongMaterial) {
						PhongMaterial material = (PhongMaterial) newMesh.getMaterial();
						material.setSpecularColor(Color.BLACK);
					}

					if (csg.isWireFrame())
						newMesh.setDrawMode(DrawMode.LINE);
					else
						newMesh.setDrawMode(DrawMode.FILL);

					newMesh.setCullFace(CullFace.BACK);
					views.put(csg.getName(), newMesh);
					csgs.put(csg.getName(), csg);
					Log.debug("Adding to thumbnail " + csg.getName());
				}
				content.getChildren().add(views.get(csg.getName()));

			} catch (Throwable t) {
				com.neuronrobotics.sdk.common.Log.error(t);
			}
		}

		PerspectiveCamera camera = new PerspectiveCamera(true);

		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<WritableImage> imageRef = new AtomicReference<>();
		BowlerKernel.runLater(() -> {
			try {
				javafx.geometry.Bounds rendered = content.getBoundsInLocal();

				double width = rendered.getWidth();
				double height = rendered.getHeight();
				double depth = rendered.getDepth();
				double radius = Math.sqrt(width * width + height * height + depth * depth) / 2;

				double halfFov = Math.toRadians(camera.getFieldOfView() / 2);
				double cameraDistance = Math.max(1.0, radius / Math.sin(halfFov) * 1.2);

				content.getTransforms().add(thumbnailView(rendered, cameraDistance));

				AmbientLight ambient = new AmbientLight(Color.color(0.38, 0.38, 0.38));

				PointLight key = new PointLight(Color.color(0.56, 0.56, 0.56));
				key.setConstantAttenuation(1);
				key.setLinearAttenuation(0);
				key.setQuadraticAttenuation(0);
				key.setTranslateX(-radius * 1.4);
				key.setTranslateY(-radius * 1.8);
				key.setTranslateZ(cameraDistance - radius * 1.8);

				PointLight fill = new PointLight(Color.color(0.10, 0.10, 0.10));
				fill.setConstantAttenuation(1);
				fill.setLinearAttenuation(0);
				fill.setQuadraticAttenuation(0);
				fill.setTranslateX(radius * 1.4);
				fill.setTranslateY(radius * 0.6);
				fill.setTranslateZ(cameraDistance - radius);

				Group sceneRoot = new Group(content, ambient, key, fill);
				Scene scene = new Scene(sceneRoot, imageSize, imageSize, true, SceneAntialiasing.BALANCED);
				scene.setFill(Color.TRANSPARENT);
				scene.setCamera(camera);

				camera.setNearClip(0.1);
				camera.setFarClip(Math.max(9000.0, cameraDistance + radius * 2));

				WritableImage snapshot = new WritableImage(imageSize, imageSize);
				scene.snapshot(snapshot);

				imageRef.set(fitThumbnail(snapshot));
			} catch (Throwable t) {
				Log.error(t);
			} finally {
				// Cached MeshViews must be detached before they can be reused.
				content.getChildren().clear();
				latch.countDown();
			}
		});
		try {
			if (!latch.await(2, TimeUnit.SECONDS))
				throw new NoImageException("JavaFX thread did not complete within 2 seconds");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new NoImageException("Interrupted while waiting for JavaFX thumbnail rendering");
		}

		WritableImage result = imageRef.get();
		if (result == null)
			throw new NoImageException("JavaFX thumbnail rendering failed");
		return result;
	}
}
