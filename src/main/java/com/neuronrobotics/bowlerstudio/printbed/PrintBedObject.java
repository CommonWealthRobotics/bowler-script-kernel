package com.neuronrobotics.bowlerstudio.printbed;

import java.util.Arrays;
import java.util.List;

import com.neuronrobotics.bowlerkernel.Bezier3d.manipulation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.scene.transform.Affine;

public class PrintBedObject {
	private double xMax;
	private double xMin;
	private double yMax;
	private double yMin;
	private CSG part;
	private String name;
	private manipulation manip;
	private Affine affine = new Affine();
	private TransformNR globalPose;
	public PrintBedObject(String name, CSG part, double xMax, double xMin, double yMax, double yMin, TransformNR startPose){
		this.part =  part;
		this.name =  name;
		this.xMax =  xMax;
		this.xMin =  xMin;
		this.yMax =  yMax;
		this.yMin =  yMin;
		this.globalPose = startPose;
		
		manip = new manipulation(affine, new Vector3d(1, 1, 0), part, startPose);
		checkBounds();
	}
	public void addEventListener(Runnable r) {
		manip.addEventListener(r);
	}

	public void addSaveListener(Runnable r) {
		manip.addSaveListener(r);
	}
	public List<CSG> get() {
		return Arrays.asList(part);
	}

	public double getX() {
		return manip.currentPose.getX();
	}

	public double getY() {
		return manip.currentPose.getY();
	}

	public double getZ() {
		return manip.currentPose.getZ();
	}
	public void checkBounds() {
		double minYTest = part.getMinY()-yMin+globalPose.getY();
		double maxYTest = part.getMaxY()-yMax+globalPose.getY();
		double minXTest = part.getMinX()-xMin+globalPose.getX();
		double maxXTest = part.getMaxX()-xMax+globalPose.getX();
		if(minYTest<0)
			globalPose.translateY(-minYTest);
		if(minXTest<0)
			globalPose.translateX(-minXTest);
		if(maxYTest>0)
			globalPose.translateY(-maxYTest);
		if(maxXTest>0)
			globalPose.translateX(-maxXTest);
		manip.set(globalPose.getX(), globalPose.getY(), globalPose.getZ());
	}

}
