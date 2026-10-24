package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.List;

import eu.mihosoft.vrl.v3d.Vector3d;

/**
 * One planar face exactly as STEP carries it: the plane it lies in and the loops
 * that bound it.
 *
 * <p>
 * The export groups a mesh's coplanar triangles into such faces and writes their
 * boundary loops; a reader hands the same loops back. This type makes the two
 * views comparable without going through a triangulation, which a face does not
 * preserve.
 */
public final class StepFace {

	/** A point of the face's plane. */
	public final double[] origin;
	public final Vector3d normal;
	/** Boundary loops, largest first: the outer bound, then any holes. */
	public final List<List<double[]>> loops;

	StepFace(double[] origin, Vector3d normal, List<List<double[]>> loops) {
		this.origin = origin;
		this.normal = normal;
		List<List<double[]>> copy = new ArrayList<>();
		for (List<double[]> loop : loops)
			copy.add(List.copyOf(loop));
		this.loops = List.copyOf(copy);
	}

	/** True when every point of the face lies in its plane. */
	public boolean isPlanar() {
		for (List<double[]> loop : loops)
			for (double[] p : loop) {
				double offset = (p[0] - origin[0]) * normal.x + (p[1] - origin[1]) * normal.y
						+ (p[2] - origin[2]) * normal.z;
				if (Math.abs(offset) > 1e-6 * (1 + length(origin)))
					return false;
			}
		return true;
	}

	private static double length(double[] p) {
		return Math.sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2]);
	}

	@Override
	public String toString() {
		return "StepFace(" + normal + " at " + origin[0] + "," + origin[1] + "," + origin[2] + ", " + loops.size()
				+ " loops)";
	}
}
