package com.neuronrobotics.bowlerkernel.Bezier3d;

import java.util.ArrayList;
import javafx.geometry.Point2D;
import javafx.geometry.Point3D;

import java.util.HashMap;
import java.util.List;

import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.*;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.MouseEvent;
//import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;

public class Manipulation {

	// For ResizingHandle communication
	@FunctionalInterface
	public interface DragCallback {
		Point3D onDrag(double screenX, double screenY, double snapGridValue);
	}
	private DragCallback onDragCallback = null;

	public HashMap<EventType<MouseEvent>, EventHandler<MouseEvent>> map = new HashMap<>();
	private double startXpix = 0; // drag X-start position on screen
	private double startYpix = 0; // drag Y-start position on screen
	private Point3D startingPointWorld = null;
	private Point3D newWorldPos = new Point3D(0, 0, 0);
	private boolean zMove = false;

	private double newX = 0;
	private double newY = 0;
	private double newZ = 0;
	private boolean dragging = false;
	private boolean snapGridEnabled = true;
	private boolean startCorrected = false; // Keep track if starting point was corrected
	private double snapGridValue = SNAP_GRID_OFF;

	public static final double SNAP_GRID_OFF = 0.0009;

	private static IInteractiveUIElementProvider ui = new IInteractiveUIElementProvider() {

		@Override
		public PerspectiveCamera getCamera() {
			// TODO Auto-generated method stub
			return null;
		}
	};

	private ArrayList<EventHandler<MouseEvent>> eventListeners = new ArrayList<>();
	private ArrayList<Runnable> saveListeners = new ArrayList<>();
	private ArrayList<Manipulation> dependants = new ArrayList<>();
	private Affine manipulationMatrix;
	private TransformNR orientation;
	private TransformNR globalPose  = new TransformNR();
	private TransformNR currentPose = new TransformNR();
	private IFrameProvider frameOfReference = ()->new TransformNR();

	private double gridOffsetX = 0;
	private double gridOffsetY = 0;
	private double gridOffsetZ = 0;
	private Point3D startingWorkplanePosition = null;

	public Manipulation(Affine mm, Vector3d o, TransformNR p, DragCallback callback, boolean zMove) {
		this(mm, o, p);
		this.onDragCallback = callback;
		this.zMove = zMove;
	}

	public Manipulation(Affine mm, Vector3d o, TransformNR p) {
		this.manipulationMatrix = mm;
		this.orientation = new TransformNR(o.x, o.y, o.z);
		//this.manip = m;
		//color = new PhongMaterial(m.getColor());
		this.setGlobalPose(p);
		setCurrentPose(p.copy());

		getUi().runLater(() -> {
			try {
				TransformFactory.nrToAffine(getGlobalPose(), manipulationMatrix);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		});

		map.put(MouseEvent.ANY, getMouseEvents());
	}

	public boolean isWorkplaneRotated() {
		TransformNR workplane = getFrameOfReference();
		RotationNR workplaneRotation = workplane.getRotation();
		double[][] rm = workplaneRotation.getRotationMatrix();
		return (Math.abs(rm[0][2]) > 1e-9) || (Math.abs(rm[1][2]) > 1e-9) || (Math.abs(rm[2][2] - 1.0) > 1e-9);
	}

	// Calculate the starting point based on the active work plane
	public void setStartingWorkplanePosition(Point3D startingPointWorld) {

		this.startingPointWorld = startingPointWorld;

		startCorrected = false;
		snapGridEnabled = true;
		gridOffsetX = 0;
		gridOffsetY = 0;
		gridOffsetZ = 0;

	this.startingWorkplanePosition = startingPointWorld;

	double x = startingPointWorld.getX();
	double y = startingPointWorld.getY();
	double z = startingPointWorld.getZ();

			// Don't use XY-offsets on rotated work planes
			// Or perhaps, use one corner of the object as origin (0, 0)?
			if (isWorkplaneRotated()) {
				x = 0;
				y = 0;
			}

			this.startingWorkplanePosition = new Point3D(x, y, z);
	}

	private void calculateGridOffsets() {

		if ((startingWorkplanePosition == null) || (snapGridValue <= 0))
			return;

		double gridX = Math.round(startingWorkplanePosition.getX() / snapGridValue) * snapGridValue;
		double gridY = Math.round(startingWorkplanePosition.getY() / snapGridValue) * snapGridValue;
		double gridZ = Math.round(startingWorkplanePosition.getZ() / snapGridValue) * snapGridValue;

		gridOffsetX = (gridX - startingWorkplanePosition.getX()) * orientation.getX();
		gridOffsetY = (gridY - startingWorkplanePosition.getY()) * orientation.getY();
		gridOffsetZ = (gridZ - startingWorkplanePosition.getZ()) * orientation.getZ();

	}

	public enum DragState {
		IDLE, Dragging
	}

	private DragState state = DragState.IDLE;
	private boolean resizeAllowed = true;

	public void addEventListener(EventHandler<MouseEvent> r) {
		if (eventListeners.contains(r))
			return;

		eventListeners.add(r);
	}

	public void addDependant(Manipulation r) {
		if (dependants.contains(r))
			return;

		dependants.add(r);
	}

	public void addSaveListener(Runnable r) {
		if (saveListeners.contains(r))
			return;

		saveListeners.add(r);
	}

	public void clearListeners() {
		// Auto-generated method stub
		saveListeners.clear();
		eventListeners.clear();
	}

	private void fireMove(TransformNR trans, MouseEvent event2) {
		for (Manipulation R : dependants)
			R.performMove(trans, event2);

		//com.neuronrobotics.sdk.common.Log.debug("Mouse event "+event2.getEventType());
		for (EventHandler<MouseEvent> R : eventListeners)
			R.handle(event2);

	}

	public void fireSave() {
		new Thread(() -> {
			for (Runnable R : saveListeners)
				R.run();

		}).start();
	}

	public EventHandler<MouseEvent> getMouseEvents() {
		return new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				String name = event.getEventType().getName();

				if (event.isControlDown())
					return;

				switch (name) {

				case "MOUSE_PRESSED":
					if (event.isPrimaryButtonDown())
						pressed(event);
					break;

				case "MOUSE_DRAGGED":
					dragged(event, event);
					break;

				case "MOUSE_RELEASED":
					release(event);
					break;

				case "MOUSE_MOVED":
					// ignore
					break;
//				case "MOUSE_ENTERED":
//					m.getMesh().setMaterial(highlight);
//					break;
//				case "MOUSE_EXITED":
//					if (state == DragState.IDLE)
//						m.getMesh().setMaterial(color);
//					break;
				default:
					// com.neuronrobotics.sdk.common.Log.error("UNKNOWN! Mouse event "+name);
					break;
				}

			}
		};
	}

	private void pressed(MouseEvent event) {
		setState(DragState.Dragging);

		new Thread(() -> {
			event.consume();
			dragging = false;

			for (Manipulation R : dependants)
				R.dragging = false;

		}).start();
	}

	private void dragged(MouseEvent event, MouseEvent event2) {

		if (resizeAllowed && (getState() == DragState.Dragging)) {

			double x = 0;
			double y = 0;
			double z = 0;

			if (onDragCallback != null) {

				// Request new world position based on mouse scene position
				newWorldPos = onDragCallback.onDrag(event.getSceneX(), event.getSceneY(), snapGridValue);

				if (zMove)
					z = newWorldPos.getZ() - startingPointWorld.getZ();
				else {
					x = newWorldPos.getX() - startingPointWorld.getX();
					y = newWorldPos.getY() - startingPointWorld.getY();
				}

				if (Double.isFinite(y) && Double.isFinite(x)) {
					final TransformNR trans = new TransformNR(x, y, z, new RotationNR());

					getUi().runLater(() -> {
						setDragging(event);
						performMoveTranslate(trans, event2);
					});

				} else
					com.neuronrobotics.sdk.common.Log.error("ERROR?");

				event.consume();
			} else { // Fallback, previous mouse maniplation

				double deltaX = (startXpix - event.getScreenX());
				double deltaY = (startYpix - event.getScreenY());
				x = deltaX / getDepthNow();
				y = deltaY / getDepthNow();

				if (Double.isFinite(y) && Double.isFinite(x)) {
					final TransformNR trans = new TransformNR(x, y, z, new RotationNR());

					getUi().runLater(() -> {
						setDragging(event);
						performMove(trans, event2);
					});

				} else
					com.neuronrobotics.sdk.common.Log.error("ERROR?");

				event.consume();
			}
		}
	}

	public boolean isMoving() {
		return (getState() == DragState.Dragging);
	}

	private void setDragging(MouseEvent event) {

		if (!dragging) {
			startXpix = event.getScreenX();
			startYpix = event.getScreenY();

			dragging = true;
			startCorrected = false;
		}

		for (Manipulation R : dependants)
			R.setDragging(event);

	}

	private void mouseRelease(MouseEvent event) {

		if (dragging) {
			dragging = false;
			getGlobalPose().setX(newX);
			getGlobalPose().setY(newY);
			getGlobalPose().setZ(newZ);

			if (event != null)
				event.consume();

			fireSave();
		}
	}

	private void release(MouseEvent event) {
		mouseRelease(event);
		for (Manipulation R : dependants)
			R.mouseRelease(event);

		setState(DragState.IDLE);
		//manip.getMesh().setMaterial(color);
	}

	private double getDepthNow() {
		return -1600 / getUi().getCamerDepth();
	}

//	public void setNewWorldPosition(Point3D newWorldPos) {
//		System.out.println("%%%%%%%%%%%% MANIPULATION RECEIVED: newWorld" + newWorldPos);
//		this.newWorldPos = newWorldPos;
//	}

	private void performMoveUnified(TransformNR trans, MouseEvent event2) {
		try {
			// Extract translation from the input (ignore any rotation)
			double xDelta = trans.getX();
			double yDelta = trans.getY();
			double zDelta = trans.getZ();

			TransformNR wp = getFrameOfReference().copy();

			// Remove translation from workplane, keep only rotation for coordinate transformation
			wp.setX(0);
			wp.setY(0);
			wp.setZ(0);

			// Transform the translation into workplane coordinates
			TransformNR global = wp.inverse().times(new TransformNR(xDelta, yDelta, zDelta, new RotationNR()));

			if (!startCorrected && dragging) {
				startCorrected = true;
				calculateGridOffsets();
			}

			if (snapGridEnabled) {
				newX = snapToGrid(global.getX() * orientation.getX()) + gridOffsetX;
				newY = snapToGrid(global.getY() * orientation.getY()) + gridOffsetY;
				newZ = snapToGrid(global.getZ() * orientation.getZ()) + gridOffsetZ;
			} else {
				newX = global.getX() * orientation.getX();
				newY = global.getY() * orientation.getY();
				newZ = global.getZ() * orientation.getZ();
			}

			// Build final transform with NO rotation
			TransformNR finalTransform = new TransformNR();
			finalTransform.setX(newX);
			finalTransform.setY(newY);
			finalTransform.setZ(newZ);
			finalTransform.setRotation(new RotationNR()); // Explicitly no rotation

			// Apply workplane transformation back
			TransformNR o = wp.times(finalTransform).times(wp.inverse());
			o.setRotation(new RotationNR()); // Ensure no rotation

			// Combine with existing global pose (translation only)
			TransformNR globalTrans = globalPose.copy().setRotation(new RotationNR());
			global = globalTrans.times(o);
			global.setRotation(new RotationNR()); // Final safety: no rotation

			setGlobal(global);

		} catch(Throwable t) {
			t.printStackTrace();
		}

		fireMove(trans, event2);
	}

	private void performMoveTranslate(TransformNR trans, MouseEvent event2) {
		try {
			// Extract translation from the input (ignore any rotation)
			TransformNR wp = getFrameOfReference().copy();

			// Remove translation from workplane, keep only rotation for coordinate transformation
			wp.setX(0);
			wp.setY(0);
			wp.setZ(0);

			if (!startCorrected && dragging) {
				startCorrected = true;
				calculateGridOffsets();
			}

			if (snapGridEnabled) {
				newX = snapToGrid(trans.getX() * orientation.getX()) + gridOffsetX;
				newY = snapToGrid(trans.getY() * orientation.getY()) + gridOffsetY;
				newZ = snapToGrid(trans.getZ() * orientation.getZ()) + gridOffsetZ;
			} else {
				newX = trans.getX() * orientation.getX();
				newY = trans.getY() * orientation.getY();
				newZ = trans.getZ() * orientation.getZ();
			}

			// Build final transform with NO rotation
			TransformNR finalTransform = new TransformNR();
			finalTransform.setX(newX);
			finalTransform.setY(newY);
			finalTransform.setZ(newZ);
			finalTransform.setRotation(new RotationNR()); // Explicitly no rotation

			// Apply workplane transformation back
			TransformNR o = wp.times(finalTransform).times(wp.inverse());
			o.setRotation(new RotationNR()); // Ensure no rotation

			// Combine with existing global pose (translation only)
			TransformNR globalTrans = globalPose.copy().setRotation(new RotationNR());
			finalTransform = globalTrans.times(o);

			setGlobal(finalTransform);

		} catch(Throwable t) {
			t.printStackTrace();
		}

		fireMove(trans, event2);
	}


	// Original perform move, compensates for rotation
	private void performMove(TransformNR trans, MouseEvent event2) {

		TransformNR camerFrame = getUi().getCamerFrame();
		TransformNR globalTMP = new TransformNR(camerFrame.getRotation());

		try {

			TransformNR global = globalTMP.times(trans);
			TransformNR wp = getFrameOfReference().copy();

			wp.setX(0);
			wp.setY(0);
			wp.setZ(0);
			global = wp.inverse().times(global);

			if (!startCorrected && dragging) {
				startCorrected = true;
				calculateGridOffsets();
			}

			if (snapGridEnabled) {
				newX = snapToGrid(global.getX() * orientation.getX()) + gridOffsetX;
				newY = snapToGrid(global.getY() * orientation.getY()) + gridOffsetY;
				newZ = snapToGrid(global.getZ() * orientation.getZ()) + gridOffsetZ;
			} else {
				newX = global.getX() * orientation.getX();
				newY = global.getY() * orientation.getY();
				newZ = global.getZ() * orientation.getZ();
			}

			TransformNR globalTrans = globalPose.copy().setRotation(new RotationNR());

			global.setX(newX);
			global.setY(newY);
			global.setZ(newZ);
			global.setRotation(new RotationNR());
			TransformNR o = wp.times(global).times(wp.inverse()).setRotation(new RotationNR());
			global = globalTrans.times(o);

			global.setRotation(new RotationNR());
			setGlobal(global);
			//com.neuronrobotics.sdk.common.Log.error(" drag "+global.getX()+" , "+global.getY()+" ,"+global.getZ());

		} catch(Throwable t) {
			t.printStackTrace();
		}

		fireMove(trans, event2);

	}

	public double snapToGrid(double in) {
		if (!snapGridEnabled || (snapGridValue <= SNAP_GRID_OFF))
			return in;

		return Math.round(in / snapGridValue) * snapGridValue;
	}

	public void setSnapGridStatus(boolean status) {
		this.snapGridEnabled = status;
	}

	public void setGlobal(TransformNR global) {
		getCurrentPose().setX(newX);
		getCurrentPose().setY(newY);
		getCurrentPose().setZ(newZ);
		getUi().runLater(() -> {
			TransformFactory.nrToAffine(global, manipulationMatrix);
		});
	}

	public static IInteractiveUIElementProvider getUi() {
		return ui;
	}

	public static void setUi(IInteractiveUIElementProvider ui) {
		Manipulation.ui = ui;
	}

	public void reset() {
		newX = 0;
		newY = 0;
		newZ = 0;
		getGlobalPose().setX(0);
		getGlobalPose().setY(0);
		getGlobalPose().setZ(0);
		setGlobal(new TransformNR(0, 0, 0, new RotationNR()));
	}

	public void set(double nX, double nY, double nZ) {

		newX = nX;
		newY = nY;
		newZ = nZ;

		getGlobalPose().setX(nX);
		getGlobalPose().setY(nY);
		getGlobalPose().setZ(nZ);
		setGlobal(new TransformNR(nX, nY, nZ, new RotationNR()));

		for (EventHandler<MouseEvent> R : eventListeners)
			R.handle(null);

	}

	public void setInReferenceFrame(double nX, double nY, double nZ) {
		TransformNR inLocal = new TransformNR(nX,  nY,  nZ);
		TransformNR wp = new TransformNR(getFrameOfReference().getRotation());
		inLocal = wp.times(inLocal);
		inLocal.setRotation(new RotationNR());
		//com.neuronrobotics.sdk.common.Log.error("Setting in reference frame:"+inLocal.toSimpleString());
		setGlobal(inLocal);

		for (EventHandler<MouseEvent> R : eventListeners)
			R.handle(null);
	}

	public TransformNR getGlobalPose() {
		return globalPose;
	}

	public TransformNR getGlobalPoseInReferenceFrame() {
		TransformNR globalPose = getGlobalPose().copy();
		TransformNR wp = new TransformNR(getFrameOfReference().getRotation());
		globalPose = wp.times(globalPose);
		globalPose.setRotation(new RotationNR());
		return globalPose;
	}

	public TransformNR getCurrentPoseInReferenceFrame() {
		TransformNR globalPose = getCurrentPose().copy();
		TransformNR wp = new TransformNR(getFrameOfReference().getRotation());
		globalPose = wp.times(globalPose);
		globalPose.setRotation(new RotationNR());
		return globalPose;
	}

	public void setGlobalPose(TransformNR globalPose) {
		this.globalPose = globalPose;
	}

	public double getIncrement() {
		return snapGridValue;
	}

	public void setIncrement(double value) {
		this.snapGridValue = value;
	}

	public TransformNR getCurrentPose() {
		return currentPose;
	}

	public void setCurrentPose(TransformNR currentPose) {
		this.currentPose = currentPose;
	}

	public void cancel() {
		release(null);
	}

	public  TransformNR getFrameOfReference() {
		return frameOfReference.get();
	}

	public void setFrameOfReference(IFrameProvider frameOfReference) {
		this.frameOfReference = frameOfReference;
	}

	public void setUnlocked(boolean resizeAllowed) {
		this.resizeAllowed = resizeAllowed;
	}

	public DragState getState() {
		return state;
	}

	public void setState(DragState state) {
		this.state = state;
	}

}