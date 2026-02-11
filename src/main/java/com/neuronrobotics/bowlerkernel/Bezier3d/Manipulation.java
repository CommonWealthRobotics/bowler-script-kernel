package com.neuronrobotics.bowlerkernel.Bezier3d;

import java.util.ArrayList;
import javafx.scene.paint.Color;
import javafx.geometry.Point3D;
import java.util.HashMap;
import java.util.List;

import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.*;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.PerspectiveCamera;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.paint.PhongMaterial;

public class Manipulation {
	public HashMap<EventType<MouseEvent>, EventHandler<MouseEvent>> map = new HashMap<>();
	double startx = 0; // drag X-start position on screen
	double starty = 0; // drag Y-start position on screen
	double newx = 0;
	double newy = 0;
	double newz = 0;
	boolean dragging = false;
	boolean snapGridEnabled = true;
	private boolean startCorrected = false; // Keep track if starting point was corrected

	public static final double SNAP_GRID_OFF = 0.000001;
	private double snapGridValue = SNAP_GRID_OFF;
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

	public boolean isWorkplaneRotated() {
		TransformNR workplane = getFrameOfReference();
		RotationNR workplaneRotation = workplane.getRotation();
		double[][] rm = workplaneRotation.getRotationMatrix();
		return (Math.abs(rm[0][2]) > 1e-9) || (Math.abs(rm[1][2]) > 1e-9) || (Math.abs(rm[2][2] - 1.0) > 1e-9);
	}

	// Calculate the starting point based on the active work plane
	public void setStartingWorkplanePosition(Point3D startingPoint) {

		gridOffsetX = 0;
		gridOffsetY = 0;
		gridOffsetZ = 0;
		snapGridEnabled = true; // Auto reset to enabled, disable lasts only for one drag
		startCorrected = false;

		try {

			TransformNR workplane = getFrameOfReference();
			Vector3d origin  = new Vector3d(workplane.getX(), workplane.getY(), workplane.getZ());
			Vector3d clicked = new Vector3d(startingPoint.getX(), startingPoint.getY(), startingPoint.getZ());
			Vector3d diff	 = clicked.minus(origin);

			RotationNR workplaneRotation = workplane.getRotation();
			double[][] rm = workplaneRotation.getRotationMatrix();

			boolean rotated = (Math.abs(rm[0][2]) > 1e-9) || (Math.abs(rm[1][2]) > 1e-9) || (Math.abs(rm[2][2] - 1.0) > 1e-9);

			// Get active work plane axis
			Vector3d xAxis = new Vector3d(rm[0][0], rm[1][0], rm[2][0]);
			Vector3d yAxis = new Vector3d(rm[0][1], rm[1][1], rm[2][1]);
			Vector3d zAxis = new Vector3d(rm[0][2], rm[1][2], rm[2][2]);

			// Get only the perpendicular parts
			double x = diff.dot(xAxis);
			double y = diff.dot(yAxis);
			double z = diff.dot(zAxis);

			// Don't use XY-offsets on rotated work planes
			// Or perhaps, use one corner of the object as origin (0, 0)?
			if (rotated) {
				x = 0;
				y = 0;
			}

			startingWorkplanePosition = new Point3D(x, y, z);

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void calculateGridOffsets() {
		if ((startingWorkplanePosition == null) || (snapGridValue <= 0))
			return;

		double gx = Math.round(startingWorkplanePosition.getX() / snapGridValue) * snapGridValue;
		double gy = Math.round(startingWorkplanePosition.getY() / snapGridValue) * snapGridValue;
		double gz = Math.round(startingWorkplanePosition.getZ() / snapGridValue) * snapGridValue;

		gridOffsetX = (gx - startingWorkplanePosition.getX()) * orientation.getX();
		gridOffsetY = (gy - startingWorkplanePosition.getY()) * orientation.getY();
		gridOffsetZ = (gz - startingWorkplanePosition.getZ()) * orientation.getZ();
		
		com.neuronrobotics.sdk.common.Log.debug(">>> calculateGridOffsets gridOffsetX:" + gridOffsetX + " Y:" + gridOffsetY + " Z:" + gridOffsetZ);
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
			R.performMove(trans,event2);

		//com.neuronrobotics.sdk.common.Log.debug("Mouse event "+event2.getEventType());
		for (EventHandler<MouseEvent> R : eventListeners)
			R.handle(event2);

	}

	public void fireSave() {
		new Thread(() -> {
			for (Runnable R : saveListeners)
				try {
					R.run();
				}catch(Exception ex) {
					Log.error(ex);
				}

		}).start();
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
					dragged(event,event);
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

	private double getDepthNow() {
		return -1600 / getUi().getCamerDepth();
	}

	private void release(MouseEvent event) {
		mouseRelease(event);
		for (Manipulation R : dependants)
			R.mouseRelease(event);

		setState(DragState.IDLE);
		//manip.getMesh().setMaterial(color);
	}

	private void dragged(MouseEvent event, MouseEvent event2) {

		if (resizeAllowed && (getState() == DragState.Dragging)) {

			getUi().runLater(() -> {
				setDragging(event);
				double deltx = (startx - event.getScreenX());
				double delty = (starty - event.getScreenY());
				double x = deltx / getDepthNow() ;
				double y = delty / getDepthNow() ;

				//com.neuronrobotics.sdk.common.Log.error("Moved "+x+" "+y);
				if (Double.isFinite(y) && Double.isFinite(x)) {			
					TransformNR trans = new TransformNR(x, y, 0, new RotationNR());
					performMove(trans,event2);
				} else
					com.neuronrobotics.sdk.common.Log.error("ERROR?");

			});
			event.consume();
		}
	}

	public boolean isMoving() {
		return (getState() == DragState.Dragging);
	}

	private void mouseRelease(MouseEvent event) {

		if (dragging) {
			dragging = false;
			getGlobalPose().setX(newx);
			getGlobalPose().setY(newy);
			getGlobalPose().setZ(newz);

			if (event != null)
				event.consume();

			fireSave();
		}
	}

	private void setDragging(MouseEvent event) {

		if (!dragging) {
			startx = event.getScreenX();
			starty = event.getScreenY();
			dragging = true;
			startCorrected = false;
		}

		for (Manipulation R : dependants)
			R.setDragging(event);

	}

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

			if (snapGridEnabled) {
				newx = snapToGrid(global.getX() * orientation.getX()) + gridOffsetX;
				newy = snapToGrid(global.getY() * orientation.getY()) + gridOffsetY;
				newz = snapToGrid(global.getZ() * orientation.getZ()) + gridOffsetZ;
			} else {
				newx = global.getX() * orientation.getX();
				newy = global.getY() * orientation.getY();
				newz = global.getZ() * orientation.getZ();
			}

			if (!startCorrected && dragging) {
					calculateGridOffsets(); // Calculate only AFTER the first call!

				startx += gridOffsetX;
				starty += gridOffsetY;
				startCorrected = true;
			}

			TransformNR globalTrans = globalPose.copy().setRotation(new RotationNR());
			
			global.setX(newx);
			global.setY(newy);
			global.setZ(newz);
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

	private double snapToGrid(double in) {
		if (!snapGridEnabled)
			return in;

		if (snapGridValue <= SNAP_GRID_OFF)
			snapGridValue = SNAP_GRID_OFF;

		return Math.round(in / snapGridValue) * snapGridValue;
	}

	public void setSnapGridStatus(boolean status) {
		this.snapGridEnabled = status;
	}

	public void setGlobal(TransformNR global) {
		getCurrentPose().setX(newx);
		getCurrentPose().setY(newy);
		getCurrentPose().setZ(newz);
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
		newx = 0;
		newy = 0;
		newz = 0;
		getGlobalPose().setX(0);
		getGlobalPose().setY(0);
		getGlobalPose().setZ(0);
		setGlobal(new TransformNR(0, 0, 0, new RotationNR()));
	}

	public void set(double newX, double newY, double newZ) {

		newx = newX;
		newy = newY;
		newz = newZ;

		getGlobalPose().setX(newX);
		getGlobalPose().setY(newY);
		getGlobalPose().setZ(newZ);
		setGlobal(new TransformNR(newX, newY, newZ, new RotationNR()));

		for (EventHandler<MouseEvent> R : eventListeners)
			R.handle(null);

	}

	public void setInReferenceFrame(double newX, double newY, double newZ) {
		TransformNR inLocal = new TransformNR(newX,  newY,  newZ);
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
		globalPose=wp.times(globalPose);
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