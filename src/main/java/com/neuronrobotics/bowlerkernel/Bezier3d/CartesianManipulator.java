package com.neuronrobotics.bowlerkernel.Bezier3d;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cylinder;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

public class CartesianManipulator {
	public Affine manipulationMatrix = new Affine();
	CSG manip1 = new Cylinder(0, 5, 40, 10).toCSG().setColor(Color.BLUE);
	CSG manip2 = new Cylinder(0, 5, 40, 10).toCSG().roty(-90).setColor(Color.RED);
	CSG manip3 = new Cylinder(0, 5, 40, 10).toCSG().rotx(90).setColor(Color.GREEN);
	private manipulation[] manipulationList = new manipulation[3];
	TransformNR globalPose;

	public CartesianManipulator(TransformNR globalPose) {
		this.globalPose = globalPose;
		manip1.setMfg(incoming -> null);
		manip2.setMfg(incoming -> null);
		manip3.setMfg(incoming -> null);
		manipulationList[0] = new manipulation(manipulationMatrix, new Vector3d(0, 0, 1), manip1, globalPose);
		manipulationList[1] = new manipulation(manipulationMatrix, new Vector3d(0, 1, 0), manip3, globalPose);
		manipulationList[2] = new manipulation(manipulationMatrix, new Vector3d(1, 0, 0), manip2, globalPose);
	}

	public void addEventListener(Runnable r) {
		for (int i = 0; i < 3; i++)
			manipulationList[i].addEventListener(r);
	}

	public void addSaveListener(Runnable r) {
		for (int i = 0; i < 3; i++)
			manipulationList[i].addSaveListener(r);
	}

	public List<CSG> get() {
		return Arrays.asList(manip1, manip2, manip3);
	}

	public double getX() {
		return manipulationList[2].currentPose.getX();
	}

	public double getY() {
		return manipulationList[1].currentPose.getY();
	}

	public double getZ() {
		return manipulationList[0].currentPose.getZ();
	}

	public void addDependant(CartesianManipulator r) {
		for (int i = 0; i < 3; i++)
			manipulationList[i].addDependant(r.manipulationList[i]);
	}
	public boolean isMoving() {
		for (int i = 0; i < 3; i++)
			if(manipulationList[i].isMoving())
				return true;
		return false;
	}

	public void clearListeners() {
		// TODO Auto-generated method stub
		for (int i = 0; i < 3; i++)
			manipulationList[i].clearListeners();
	}

	public void set(double newX, double newY, double newZ) {
		for (int i = 0; i < 3; i++)
			manipulationList[i].set(newX,newY,newZ);
	}
}
