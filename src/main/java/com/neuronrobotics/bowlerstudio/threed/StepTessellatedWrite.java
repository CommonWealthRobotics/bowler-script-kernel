package com.neuronrobotics.bowlerstudio.threed;

import java.util.List;

import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.TickToc;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Writes a mesh as an AP242 tessellated representation: the coordinate list and
 * the triangle indices exactly as the CSG holds them.
 *
 * <p>
 * The planar BRep {@link StepBRepWrite} writes is what a CAD package shows and
 * edits, but it describes the surface rather than the mesh: its faces are
 * re-triangulated on the way back in, so a round trip can never return the
 * source's own triangles. This representation carries the triangles themselves,
 * so a reader that understands it reproduces the mesh bit for bit. Both
 * representations describe the same shape and are related to each other, which
 * is how AP242 expresses a part that has topology and a tessellation of it.
 */
final class StepTessellatedWrite {

	private StepTessellatedWrite() {
	}

	/**
	 * A {@code TESSELLATED_SHAPE_REPRESENTATION} over the mesh, or 0 when the mesh
	 * has no triangles to describe.
	 */
	static long representation(StepEmitter emitter, CSG csg, String name, long context) {
		double[] vertices = csg.getVertices();
		long[] triangles = csg.getTriangles();
		if (vertices == null || triangles == null || triangles.length < 3 || vertices.length < 9)
			return 0;

		Log.debug("StepTessellatedWrite: writing " + (vertices.length / 3) + " vertices, "
				+ (triangles.length / 3) + " triangles for " + name);
		TickToc.tic("write tessellation " + name);
		long coordinates = coordinates(emitter, vertices);
		// a triangulated surface set is a tessellated surface set with an index list
		// per triangle, so its parameters are: name, coordinates, pnmax, normals,
		// pnindex, triangles
		long surface = emitter.add("TRIANGULATED_SURFACE_SET(" + quote(name) + ",#" + coordinates + ",$,$,$,"
				+ triangles(triangles) + ")");

		// the placement keeps the representation's own frame at the origin, matching
		// what the BRep representation does
		long origin = emitter.add("CARTESIAN_POINT('',(0.,0.,0.))");
		long axis = emitter.add("DIRECTION('',(0.,0.,1.))");
		long reference = emitter.add("DIRECTION('',(1.,0.,0.))");
		long placement = emitter.add("AXIS2_PLACEMENT_3D('',#" + origin + ",#" + axis + ",#" + reference + ")");

		return emitter.add("TESSELLATED_SHAPE_REPRESENTATION(" + quote(name) + ",(#" + placement + ",#" + surface
				+ "),#" + context + ")");
	}

	/** The mesh's points as a coordinate list, in the mesh's own vertex order. */
	private static long coordinates(StepEmitter emitter, double[] vertices) {
		int count = vertices.length / 3;
		StringBuilder list = new StringBuilder("(");
		for (int i = 0; i < count; i++) {
			if (i > 0)
				list.append(',');
			list.append(coords(vertices[i * 3], vertices[i * 3 + 1], vertices[i * 3 + 2]));
		}
		return emitter.add("COORDINATES_LIST(''," + count + "," + list.append(')') + ")");
	}

	/** The triangle index list, one based as STEP tessellation requires. */
	private static String triangles(long[] triangles) {
		StringBuilder list = new StringBuilder("(");
		for (int i = 0; i + 2 < triangles.length; i += 3) {
			if (i > 0)
				list.append(',');
			list.append('(').append(triangles[i] + 1).append(',').append(triangles[i + 1] + 1).append(',')
					.append(triangles[i + 2] + 1).append(')');
		}
		return list.append(')').toString();
	}

	private static String coords(double x, double y, double z) {
		return "(" + real(x) + "," + real(y) + "," + real(z) + ")";
	}

	private static String real(double value) {
		if (!Double.isFinite(value))
			throw new IllegalArgumentException("Non-finite STEP value " + value);
		return Double.toString(value);
	}

	private static String quote(String value) {
		StringBuilder escaped = new StringBuilder("'");
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character == '\'')
				escaped.append("''");
			else if (character == '\\')
				escaped.append("\\\\");
			else if (character >= 0x20)
				escaped.append(character);
		}
		return escaped.append('\'').toString();
	}
}
