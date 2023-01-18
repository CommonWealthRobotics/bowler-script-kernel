package com.neuronrobotics.bowlerkernel.Bezier3d;

import java.util.ArrayList;
import java.util.HashMap;

import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.sdk.addons.kinematics.math.*;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.input.MouseEvent;
import javafx.scene.transform.Affine;

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
		this.globalPose = p;
		getUi().runLater(() -> {
			TransformFactory.nrToAffine(globalPose, manipulationMatrix);
		});
		map.put(MouseEvent.MOUSE_PRESSED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
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
		});

		map.put(MouseEvent.MOUSE_DRAGGED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				getUi().runLater(() -> {
					setDragging(event);
					double deltx = (startx - event.getScreenX());
					double delty = (starty - event.getScreenY());
					TransformNR trans = new TransformNR(deltx / depth, delty / depth, 0, new RotationNR());

					performMove( trans,camFrame);
				});
				event.consume();
			}




		});

		map.put(MouseEvent.MOUSE_RELEASED, new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent event) {
				mouseRelease(event);
				for (manipulation R : dependants) 
					R.mouseRelease(event);
			}


		});
		manip.getStorage().set("manipulator", map);
		manip.setManipulator(manipulationMatrix);
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
		TransformNR global = camFrame2.times(trans);
		newx = (global.getX() * orintation.x + globalPose.getX());
		newy = (global.getY() * orintation.y + globalPose.getY());
		newz = (global.getZ() * orintation.z + globalPose.getZ());
		global.setX(newx);
		global.setY(newy);
		global.setZ(newz);

		global.setRotation(new RotationNR());
		getUi().runLater(() -> {
			TransformFactory.nrToAffine(global, manipulationMatrix);
		});
		// System.out.println(" drag "+global.getX()+" , "+global.getY()+" ,
		// "+global.getZ()+" "+deltx+" "+delty);
		fireMove(trans,camFrame2);
	}
	public static IInteractiveUIElementProvider getUi() {
		return ui;
	}

	public static void setUi(IInteractiveUIElementProvider ui) {
		manipulation.ui = ui;
	}



}
