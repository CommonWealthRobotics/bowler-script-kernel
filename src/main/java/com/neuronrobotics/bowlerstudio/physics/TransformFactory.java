package com.neuronrobotics.bowlerstudio.physics;

import javax.vecmath.Quat4f;

import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import javafx.scene.transform.Affine;

// TODO: Auto-generated Javadoc
/**
 * A factory for creating Transform objects.
 */
public class TransformFactory extends com.neuronrobotics.sdk.addons.kinematics.TransformFactory{
	
	
	public static void nrToBullet(TransformNR nr,com.bulletphysics.linearmath.Transform bullet){
		bullet.origin.set(
				(float)nr.getX(), 
				(float)nr.getY(), 
				(float)nr.getZ());
		bullet.setRotation(new Quat4f(
				(float)nr.getRotation().getRotationMatrix2QuaturnionX(),
				(float)nr.getRotation().getRotationMatrix2QuaturnionY(), 
				(float)nr.getRotation().getRotationMatrix2QuaturnionZ(), 
				(float)nr.getRotation().getRotationMatrix2QuaturnionW()));
	}
	
	public static TransformNR bulletToNr(com.bulletphysics.linearmath.Transform bullet){
		Quat4f out= new Quat4f();
		bullet.getRotation(out);
		return new TransformNR(bullet.origin.x,
				bullet.origin.y,
				bullet.origin.z, out.w, out.x, out.y, out.z);
	}
	
	public static void bulletToAffine(Affine affine,com.bulletphysics.linearmath.Transform bullet){
		TransformFactory.getTransform(bulletToNr(bullet), affine);
	}
	public static void affineToBullet(Affine affine,com.bulletphysics.linearmath.Transform bullet){
		TransformNR nr = getTransform(affine);
		nrToBullet(nr,bullet);
	}
}
