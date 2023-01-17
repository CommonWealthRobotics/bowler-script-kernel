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

public class CartesianManipulator{
	public Affine manipulationMatrix= new Affine();
	CSG manip1 = new Cylinder(0,5,40,10).toCSG()
	.setColor(Color.BLUE);
	CSG manip2 = new Cylinder(0,5,40,10).toCSG()
	.roty(-90)
	.setColor(Color.RED);
	CSG manip3 = new Cylinder(0,5,40,10).toCSG()
	.rotx(90)
	.setColor(Color.GREEN);
	private manipulation manipulation;
	private com.neuronrobotics.bowlerkernel.Bezier3d.manipulation manipulation2;
	private com.neuronrobotics.bowlerkernel.Bezier3d.manipulation manipulation3;
	
	public CartesianManipulator(TransformNR globalPose) {
		manip1.setMfg(incoming -> null);
		manip2.setMfg(incoming -> null);
		manip3.setMfg(incoming -> null);
		manipulation = new manipulation( manipulationMatrix, new Vector3d(0,0,1), manip1, globalPose);
		manipulation2 = new manipulation( manipulationMatrix, new Vector3d(0,1,0), manip3, globalPose);
		manipulation3 = new manipulation( manipulationMatrix, new Vector3d(1,0,0), manip2, globalPose);
	}
	
	public void addEventListener(Runnable r) {
		manipulation.addEventListener(r);
		manipulation2.addEventListener(r);
		manipulation3.addEventListener(r);
	}

	public void addSaveListener(Runnable r) {
		manipulation.addSaveListener(r);
		manipulation2.addSaveListener(r);
		manipulation3.addSaveListener(r);
	}
	
	public List<CSG> get(){
		return Arrays.asList(manip1, manip2, manip3);
	}

	public void clearListeners() {
		// TODO Auto-generated method stub
		manipulation.clearListeners();
		manipulation2.clearListeners();
		manipulation3.clearListeners();
	}
}
