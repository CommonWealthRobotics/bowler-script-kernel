package com.neuronrobotics.bowlerstudio.physics;

import javax.vecmath.Matrix4d;
import javax.vecmath.Quat4d;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3d;

import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import javafx.application.Platform;
import javafx.scene.transform.Affine;

// TODO: Auto-generated Javadoc

/**
 * A factory for creating Transform objects.
 */
@SuppressWarnings("restriction")
public class TransformFactory {
	
	/**
	 * Gets the transform.
	 *
	 * @param x the x
	 * @param y the y
	 * @param z the z
	 * @return the transform
	 */
	public static Affine newAffine(double x, double y, double z){
		return nrToAffine(new TransformNR(x, y, z, new RotationNR()));
	}
	
	/**
	 * Gets the transform.
	 *
	 * @param input the input
	 * @return the transform
	 */
	public static Affine nrToAffine(TransformNR input){
		Affine rotations =new Affine();
		return nrToAffine( input , rotations);
	}
	
	/**
	 * Gets the transform.
	 *
	 * @param input the input
	 * @return the transform
	 */
	public static TransformNR affineToNr(Affine input){
		TransformNR rotations =new TransformNR();
		return affineToNr( rotations,input  );
	}
	/**
	 * Gets the transform.
	 *
	 * @param outputValue the input
	 * @param rotations the rotations
	 * @return the transform
	 */
	public static TransformNR affineToNr(TransformNR outputValue ,Affine rotations){
		double[][] poseRot = outputValue
				.getRotationMatrixArray();
		
		poseRot[0][0]=rotations.getMxx();
		poseRot[0][1]=rotations.getMxy();
		poseRot[0][2]=rotations.getMxz();
		poseRot[1][0]=rotations.getMyx();
		poseRot[1][1]=rotations.getMyy();
		poseRot[1][2]=rotations.getMyz();
		poseRot[2][0]=rotations.getMzx();
		poseRot[2][1]=rotations.getMzy();
		poseRot[2][2]=rotations.getMzz();
		
		outputValue.set(rotations.getTx(),rotations.getTy(),rotations.getTz(),poseRot);
		return outputValue;
	}
	
	/**
	 * Gets the transform.
	 *
	 * @param input the input
	 * @param rotations the rotations
	 * @return the transform
	 */
	public static Affine nrToAffine(TransformNR input ,Affine rotations){
	    if (!Platform.isFxApplicationThread()) {
	    	new RuntimeException("This method must be in UI thread!").printStackTrace();
	    }
	    if(input==null )
	    	return rotations;
	    if( rotations==null)
	    	rotations=new Affine();
		double[][] poseRot = input
				.getRotationMatrixArray();
		try {
			rotations.setMxx(poseRot[0][0]);
			rotations.setMxy(poseRot[0][1]);
			rotations.setMxz(poseRot[0][2]);
			rotations.setMyx(poseRot[1][0]);
			rotations.setMyy(poseRot[1][1]);
			rotations.setMyz(poseRot[1][2]);
			rotations.setMzx(poseRot[2][0]);
			rotations.setMzy(poseRot[2][1]);
			rotations.setMzz(poseRot[2][2]);
			rotations.setTx(input.getX());
			rotations.setTy(input.getY());
			rotations.setTz(input.getZ());
		}catch(Exception e) {
			e.printStackTrace();
		}
		return rotations;
	}
	public static void nrToBullet(TransformNR nr, com.bulletphysics.linearmath.Transform bullet) {
		bullet.origin.set((float) nr.getX(), (float) nr.getY(), (float) nr.getZ());
		bullet.setRotation(new Quat4f((float) nr.getRotation().getRotationMatrix2QuaturnionX(),
				(float) nr.getRotation().getRotationMatrix2QuaturnionY(),
				(float) nr.getRotation().getRotationMatrix2QuaturnionZ(),
				(float) nr.getRotation().getRotationMatrix2QuaturnionW()));
	}

	public static TransformNR bulletToNr(com.bulletphysics.linearmath.Transform bullet) {
		Quat4f out = new Quat4f();
		bullet.getRotation(out);
		return new TransformNR(bullet.origin.x, bullet.origin.y, bullet.origin.z, out.w, out.x, out.y, out.z);
	}

	public static void bulletToAffine(Affine affine, com.bulletphysics.linearmath.Transform bullet) {
		if (!Platform.isFxApplicationThread()) {
	    	new RuntimeException("This method must be in UI thread!").printStackTrace();
	    }
		// synchronized(out){
		double[][] vals = bulletToNr(bullet).getRotationMatrix().getRotationMatrix();
		// we explicitly test norm against one here, saving a division
		// at the cost of a test and branch. Is it worth it?
		// compute xs/ys/zs first to save 6 multiplications, since xs/ys/zs
		// will be used 2-4 times each.
		affine.setMxx(vals[0][0]);
		affine.setMxy(vals[0][1]);
		affine.setMxz(vals[0][2]);
		affine.setMyx(vals[1][0]);
		affine.setMyy(vals[1][1]);
		affine.setMyz(vals[1][2]);
		affine.setMzx(vals[2][0]);
		affine.setMzy(vals[2][1]);
		affine.setMzz(vals[2][2]);
		// }
		affine.setTx(bullet.origin.x);
		affine.setTy(bullet.origin.y);
		affine.setTz(bullet.origin.z);
	}

	public static void affineToBullet(Affine affine, com.bulletphysics.linearmath.Transform bullet) {
		TransformNR nr = affineToNr(affine);
		nrToBullet(nr, bullet);
	}

	public static eu.mihosoft.vrl.v3d.Transform nrToCSG(TransformNR nr) {
		Quat4d q1 = new Quat4d();
		q1.w = nr.getRotation().getRotationMatrix2QuaturnionW();
		q1.x = nr.getRotation().getRotationMatrix2QuaturnionX();
		q1.y = nr.getRotation().getRotationMatrix2QuaturnionY();
		q1.z = nr.getRotation().getRotationMatrix2QuaturnionZ();
		Vector3d t1 = new Vector3d();
		t1.x = nr.getX();
		t1.y = nr.getY();
		t1.z = nr.getZ();
		double s = 1.0;

		Matrix4d rotation = new Matrix4d(q1, t1, s);
		return new eu.mihosoft.vrl.v3d.Transform(rotation);
	}

	public static TransformNR scale(TransformNR incoming, double scale) {
		return new TransformNR(incoming.getX() * scale, incoming.getY() * scale, incoming.getZ() * scale,
				new RotationNR(Math.toDegrees(incoming.getRotation().getRotationTilt()) * scale,
						Math.toDegrees(incoming.getRotation().getRotationAzimuth()) * scale,
						Math.toDegrees(incoming.getRotation().getRotationElevation()) * scale));
	}

	public static TransformNR csgToNR(eu.mihosoft.vrl.v3d.Transform csg) {
		Matrix4d rotation = csg.getInternalMatrix();
		Quat4d q1 = new Quat4d();
		rotation.get(q1);
		Vector3d t1 = new Vector3d();
		rotation.get(t1);

		return new TransformNR(t1.x, t1.y, t1.z, new RotationNR(q1.w, q1.x, q1.y, q1.z));
	}

}
