package com.neuronrobotics.bowlerkernel.Bezier3d;

import java.util.ArrayList;
import javafx.scene.paint.Color;
import java.util.HashMap;

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
public class manipulation {
	HashMap<EventType<MouseEvent>, EventHandler<MouseEvent>> map = new HashMap<>();
	double startx = 0;
	double starty = 0;
	double newx = 0;
	double newy = 0;
	double newz = 0;
	TransformNR camFrame = null;
	boolean dragging = false;
	double depth = 0;
	private static IInteractiveUIElementProvider ui = new IInteractiveUIElementProvider() {
	};

	private ArrayList<Runnable> eventListeners = new ArrayList<>();
	private ArrayList<Runnable> saveListeners = new ArrayList<>();
	private ArrayList<manipulation> dependants = new ArrayList<>();
	private Affine manipulationMatrix;
	private Vector3d orintation;
	private CSG manip;
	private TransformNR globalPose;
	TransformNR currentPose;
	private PhongMaterial color;// = new PhongMaterial(getColor());
	private PhongMaterial highlight = new PhongMaterial(Color.GOLD);
	private enum DragState{
		IDLE,
		Dragging
	}
	private DragState state = DragState.IDLE;
	public void addEventListener(Runnable r) {
		if (eventListeners.contains(r))
			return;
		eventListeners.add(r);
	}
	public void addDependant(manipulation r) {
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
	
	
	private void fireMove( TransformNR trans, TransformNR camFrame2) {
		for (manipulation R : dependants) {
			R.performMove(trans,camFrame2);
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

	public manipulation(Affine mm, Vector3d o, CSG m, TransformNR p) {
		this.manipulationMatrix = mm;
		this.orintation = o;
		this.manip = m;
		color=new PhongMaterial(m.getColor());
		this.globalPose = p;
		currentPose=p.copy();
		getUi().runLater(() -> {
			TransformFactory.nrToAffine(globalPose, manipulationMatrix);
		});
		
		map.put(MouseEvent.ANY, event -> {
			String name = event.getEventType().getName();
			switch(name) {
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
			case "MOUSE_ENTERED":
				m.getMesh().setMaterial(highlight);
				break;	
			case "MOUSE_EXITED":
				if(state==DragState.IDLE)
					m.getMesh().setMaterial(color);
				break;	
			default:
				//System.out.println("UNKNOWN! Mouse event "+name);
				break;
			}
			
		});
		manip.getStorage().set("manipulator", map);
		manip.setManipulator(manipulationMatrix);
	}
	
	private void pressed(MouseEvent event) {
		state = DragState.Dragging;
		new Thread(() -> {
			camFrame = getUi().getCamerFrame();
			depth = -1600 / getUi().getCamerDepth();
			event.consume();
			dragging = false;
			for (manipulation R : dependants) {
				R.camFrame = getUi().getCamerFrame();
				R.depth = -1600 / getUi().getCamerDepth();
				R.dragging = false;
			}
		}).start();
	}
	private void release(MouseEvent event) {
		mouseRelease(event);
		for (manipulation R : dependants) 
			R.mouseRelease(event);
		state = DragState.IDLE;
		manip.getMesh().setMaterial(color);
	}
	private void dragged(MouseEvent event) {
		getUi().runLater(() -> {
			setDragging(event);
			double deltx = (startx - event.getScreenX());
			double delty = (starty - event.getScreenY());
			TransformNR trans = new TransformNR(deltx / depth, delty / depth, 0, new RotationNR());

			performMove( trans,camFrame);
		});
		event.consume();
	}
	public boolean isMoving() {
		return state==DragState.Dragging;
	}
	private void mouseRelease(MouseEvent event) {
		if (dragging) {
			dragging = false;
			globalPose.setX(newx);
			globalPose.setY(newy);
			globalPose.setZ(newz);
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
		for (manipulation R : dependants) {
			R.setDragging(event);
		}
	}
	private void performMove( TransformNR trans, TransformNR camFrame2) {
		TransformNR globalTMP = camFrame2.copy();
		globalTMP.setX(0);
		globalTMP.setY(0);
		globalTMP.setZ(0);		
		TransformNR global = globalTMP.times(trans);
		newx = (global.getX() * orintation.x + globalPose.getX());
		newy = (global.getY() * orintation.y + globalPose.getY());
		newz = (global.getZ() * orintation.z + globalPose.getZ());
		global.setX(newx);
		global.setY(newy);
		global.setZ(newz);

		global.setRotation(new RotationNR());
		setGlobal(global);
		// System.out.println(" drag "+global.getX()+" , "+global.getY()+" ,
		// "+global.getZ()+" "+deltx+" "+delty);
		fireMove(trans,camFrame2);
	}
	private void setGlobal(TransformNR global) {
		currentPose.setX(newx);
		currentPose.setY(newy);
		currentPose.setZ(newz);
		getUi().runLater(() -> {
			TransformFactory.nrToAffine(global, manipulationMatrix);
		});
	}
	public static IInteractiveUIElementProvider getUi() {
		return ui;
	}

	public static void setUi(IInteractiveUIElementProvider ui) {
		manipulation.ui = ui;
	}
	public void set(double newX, double newY, double newZ) {
		newx=newX;
		newy=newY;
		newz=newZ;
		globalPose.setX(newX);
		globalPose.setY(newY);
		globalPose.setZ(newZ);
		setGlobal(new TransformNR(newX,newY,newZ,new RotationNR()));
		for (Runnable R : eventListeners) {
			R.run();
		}
		
	}



}
