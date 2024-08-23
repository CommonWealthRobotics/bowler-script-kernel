package com.neuronrobotics.bowlerkernel.Bezier3d;

import java.util.ArrayList;
import javafx.scene.paint.Color;
import java.util.HashMap;
import java.util.List;

import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.*;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.paint.PhongMaterial;

public class Manipulation {
	public HashMap<EventType<MouseEvent>, EventHandler<MouseEvent>> map = new HashMap<>();
	double startx = 0;
	double starty = 0;
	double newx = 0;
	double newy = 0;
	double newz = 0;
	boolean dragging = false;
	private double increment = 0.000001;
	private static IInteractiveUIElementProvider ui = new IInteractiveUIElementProvider() {
	};

	private ArrayList<Runnable> eventListeners = new ArrayList<>();
	private ArrayList<Runnable> saveListeners = new ArrayList<>();
	private ArrayList<Manipulation> dependants = new ArrayList<>();
	private Affine manipulationMatrix;
	private TransformNR orintation;
	private TransformNR globalPose= new TransformNR();
	private TransformNR currentPose=new TransformNR();
	private IFrameProvider frameOfReference = ()->new TransformNR();
	//private PhongMaterial color;// = new PhongMaterial(getColor());
	//private PhongMaterial highlight = new PhongMaterial(Color.GOLD);

	private enum DragState {
		IDLE, Dragging
	}

	private DragState state = DragState.IDLE;

	public void addEventListener(Runnable r) {
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
		// TODO Auto-generated method stub
		saveListeners.clear();
		eventListeners.clear();
	}

	private void fireMove(TransformNR trans) {
		for (Manipulation R : dependants) {
			R.performMove(trans);
		}
		for (Runnable R : eventListeners) {
			R.run();
		}
	}

	private void fireSave() {
		new Thread(() -> {
			for (Runnable R : saveListeners) {
				R.run();
			}
		}).start();
	}
//	public manipulation(Affine mm, Vector3d o, CSG m, TransformNR p) {
//		
//	}
	public Manipulation(Affine mm, Vector3d o, TransformNR p) {
		this.manipulationMatrix = mm;
		this.orintation = new TransformNR(o.x, o.y, o.z);
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
				switch (name) {
				case "MOUSE_PRESSED":
					pressed(event);
					break;
				case "MOUSE_DRAGGED":
					dragged(event);
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
					// System.out.println("UNKNOWN! Mouse event "+name);
					break;
				}

			}
		};
	}

	private void pressed(MouseEvent event) {
		state = DragState.Dragging;
		new Thread(() -> {
			event.consume();
			dragging = false;
			for (Manipulation R : dependants) {

				R.dragging = false;
			}
		}).start();
	}

	private double getDepthNow() {
		return -1600 / getUi().getCamerDepth();
	}

	private void release(MouseEvent event) {
		mouseRelease(event);
		for (Manipulation R : dependants)
			R.mouseRelease(event);
		state = DragState.IDLE;
		//manip.getMesh().setMaterial(color);
	}

	private void dragged(MouseEvent event) {
		if(state==DragState.Dragging) {
			getUi().runLater(() -> {
				setDragging(event);
				double deltx = (startx - event.getScreenX());
				double delty = (starty - event.getScreenY());
				double x = deltx/  getDepthNow() ;
				double y = delty/  getDepthNow() ;
				//System.out.println("Moved "+x+" "+y);
				if(Double.isFinite(y) && Double.isFinite(x)) {			
					TransformNR trans = new TransformNR(x, y, 0, new RotationNR());
					performMove(trans);
				}else {
					System.out.println("ERROR?");
				}
			});
			event.consume();
		}
	}

	public boolean isMoving() {
		return state == DragState.Dragging;
	}

	private void mouseRelease(MouseEvent event) {
		if (dragging) {
			dragging = false;
			getGlobalPose().setX(newx);
			getGlobalPose().setY(newy);
			getGlobalPose().setZ(newz);
			if(event!=null)
				event.consume();
			fireSave();
		}
	}

	private void setDragging(MouseEvent event) {
		if (dragging == false) {
			startx = event.getScreenX();
			starty = event.getScreenY();
		}
		dragging = true;
		for (Manipulation R : dependants) {
			R.setDragging(event);
		}
	}

	private void performMove(TransformNR trans) {
		TransformNR camerFrame = getUi().getCamerFrame();
		TransformNR globalTMP = new TransformNR(camerFrame.getRotation());
		try {
			
			TransformNR global = globalTMP.times(trans);
			TransformNR wp = getFrameOfReference().copy();
			wp.setX(0);
			wp.setY(0);
			wp.setZ(0);
			global=wp.inverse().times(global);
			
			newx = round((global.getX() * orintation.getX() ));
			newy = round((global.getY() * orintation.getY() ));
			newz = round((global.getZ() * orintation.getZ() ));
			
			TransformNR globalTrans = globalPose.copy().setRotation(new RotationNR());
			global.setX(newx);
			global.setY(newy);
			global.setZ(newz);
			global.setRotation(new RotationNR());
			TransformNR o =wp.times(global).times(wp.inverse()).setRotation(new RotationNR());
			global=globalTrans.times(o);

	
			global.setRotation(new RotationNR());
			setGlobal(global);
			//System.out.println(" drag "+global.getX()+" , "+global.getY()+" ,"+global.getZ());

		}catch(Throwable t) {
			t.printStackTrace();
		}
		fireMove(trans);
	}
	private double round(double in) {
		return Math.round(in / increment) * increment;
	}
	
	private void setGlobal(TransformNR global) {
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

	public void set(double newX, double newY, double newZ) {
		newx = newX;
		newy = newY;
		newz = newZ;
		getGlobalPose().setX(newX);
		getGlobalPose().setY(newY);
		getGlobalPose().setZ(newZ);
		setGlobal(new TransformNR(newX, newY, newZ, new RotationNR()));
		for (Runnable R : eventListeners) {
			R.run();
		}

	}

	public void reset() {
		// TODO Auto-generated method stub
		
	}

	public TransformNR getGlobalPose() {
		return globalPose;
	}
	public TransformNR getGlobalPoseInReferenceFrame() {
		TransformNR globalPose = getGlobalPose().copy();
		TransformNR wp = new TransformNR( getFrameOfReference() .getRotation());
		globalPose=wp.times(globalPose);
		globalPose.setRotation(new RotationNR());
		return globalPose;
	}

	public void setGlobalPose(TransformNR globalPose) {
		this.globalPose = globalPose;
	}

	public double getIncrement() {
		return increment;
	}

	public void setIncrement(double increment) {
		this.increment = increment;
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

}
