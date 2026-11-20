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

	/**
	 * How far outside a face's plane a point may sit and still be in the face, in
	 * model units: the budget the writer built the face to. A face written by the
	 * export carries that budget; a face read back from a file, whose faces a reader
	 * already accepted as planar, carries a nominal one.
	 */
	private static final double DEFAULT_PLANE_TOLERANCE = 1e-4;

	/** A point of the face's plane. */
	public final double[] origin;
	/** How far outside the plane this face's points may sit, in model units. */
	public final double planeTolerance;
	public final Vector3d normal;
	/** Boundary loops, largest first: the outer bound, then any holes. */
	public final List<List<double[]>> loops;
	/**
	 * Every point the face holds, its outline and its interior alike. A face is a
	 * plane over all of them, so a test that asks whether two faces are one plane
	 * must see the interior too: an outline can fit a plane while a triangle inside
	 * it does not, which is the surface bending gradually rather than one plane.
	 */
	public final List<double[]> points;
	/**
	 * The face's triangles' area-weighted normals summed, before normalizing, and
	 * the total area. {@link #normal} is this sum normalized and {@link #origin} is
	 * its area-weighted centroid, so two faces' values can be combined into the fit
	 * of their union exactly as the writer fits a face it fuses.
	 */
	final Vector3d normalSum;
	final double area;

	StepFace(double[] origin, Vector3d normal, List<List<double[]>> loops) {
		this(origin, normal, loops, flatten(loops));
	}

	StepFace(double[] origin, Vector3d normal, List<List<double[]>> loops, List<double[]> points) {
		// a face of unknown provenance: only its unit normal is known, so the sum is
		// that normal and the area is the unit it was measured over
		this(origin, normal, loops, points, normal, 1, DEFAULT_PLANE_TOLERANCE);
	}

	StepFace(double[] origin, Vector3d normal, List<List<double[]>> loops, List<double[]> points, Vector3d normalSum,
			double area, double planeTolerance) {
		this.origin = origin;
		this.normal = normal;
		this.normalSum = normalSum;
		this.area = area;
		this.planeTolerance = planeTolerance;
		List<List<double[]>> copy = new ArrayList<>();
		for (List<double[]> loop : loops)
			copy.add(List.copyOf(loop));
		this.loops = List.copyOf(copy);
		this.points = List.copyOf(points);
	}

	private static List<double[]> flatten(List<List<double[]>> loops) {
		List<double[]> all = new ArrayList<>();
		for (List<double[]> loop : loops)
			all.addAll(loop);
		return all;
	}

	/**
	 * True when every point of the face lies in its plane, within the budget the
	 * face was built to; see {@link #planeTolerance}.
	 */
	public boolean isPlanar() {
		for (double[] point : points) {
			double offset = (point[0] - origin[0]) * normal.x + (point[1] - origin[1]) * normal.y
					+ (point[2] - origin[2]) * normal.z;
			if (Math.abs(offset) > planeTolerance)
				return false;
		}
		return true;
	}

	@Override
	public String toString() {
		return "StepFace(" + normal + " at " + origin[0] + "," + origin[1] + "," + origin[2] + ", " + loops.size()
				+ " loops)";
	}
}
