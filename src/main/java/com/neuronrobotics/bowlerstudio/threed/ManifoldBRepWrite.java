package com.neuronrobotics.bowlerstudio.threed;

import java.util.stream.Collectors;

import java.util.Arrays;
import java.util.Collections;

import static com.neuronrobotics.bowlerstudio.threed.ManifoldBRepPlan.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/** Emits the indexed boundary of a native manifold without moving its vertices. */
final class ManifoldBRepWrite {
	private final StepEmitter emitter;
	private final double[] positions;
	private final Map<Integer, Long> vertices = new HashMap<>();
	private final Map<Long, Long> edges = new HashMap<>();

	private ManifoldBRepWrite(StepEmitter emitter, double[] positions) {
		this.emitter = emitter;
		this.positions = positions;
	}

	static List<Long> items(StepEmitter emitter, CSG solid, String name) {
		ManifoldBRepWrite writer = new ManifoldBRepWrite(emitter, solid.getVertices());
		Plan plan = plan(solid.getVertices(), solid.getTriangles());
		if (plan.faces().isEmpty())
			throw new IllegalArgumentException("Cannot write an empty BRep");
		List<Long> faces = new ArrayList<>();
		for (Face face : plan.faces())
			faces.add(writer.face(face, name));
		long shell = emitter.add("CLOSED_SHELL(" + quote(name) + "," + refs(faces) + ")");
		long body = emitter.add("MANIFOLD_SOLID_BREP(" + quote(name) + ",#" + shell + ")");
		return Collections.unmodifiableList(
				Arrays.asList(writer.placement(new Point(0, 0, 0), new Point(0, 0, 1), new Point(1, 0, 0)), body));
	}

	static List<StepFace> faces(CSG solid) {
		List<StepFace> result = new ArrayList<>();
		double[] points = solid.getVertices();
		for (Face face : plan(points, solid.getTriangles()).faces()) {
			List<List<double[]>> loops = new ArrayList<>();
			for (List<Integer> loop : face.loops()) {
				List<double[]> coordinates = new ArrayList<>();
				for (int v : loop)
					coordinates.add(new double[]{points[3 * v], points[3 * v + 1], points[3 * v + 2]});
				loops.add(coordinates);
			}
			result.add(new StepFace(new double[]{face.origin().x(), face.origin().y(), face.origin().z()},
					new Vector3d(face.normal().x(), face.normal().y(), face.normal().z()), loops));
		}
		return result;
	}

	private long face(Face face, String name) {
		long plane = emitter.add("PLANE('',#" + placement(face.origin(), face.normal()) + ")");
		List<Long> bounds = new ArrayList<>();
		for (List<Integer> loop : face.loops()) {
			List<Long> oriented = new ArrayList<>();
			for (int i = 0; i < loop.size(); i++) {
				int a = loop.get(i), b = loop.get((i + 1) % loop.size());
				oriented.add(emitter.add("ORIENTED_EDGE('',*,*,#" + edge(a, b) + "," + (a < b ? ".T." : ".F.") + ")"));
			}
			long boundary = emitter.add("EDGE_LOOP(''," + refs(oriented) + ")");
			bounds.add(
					emitter.add((bounds.isEmpty() ? "FACE_OUTER_BOUND" : "FACE_BOUND") + "('',#" + boundary + ",.T.)"));
		}
		return emitter.add("ADVANCED_FACE(" + quote(name) + "," + refs(bounds) + ",#" + plane + ",.T.)");
	}

	private long edge(int a, int b) {
		int low = Math.min(a, b), high = Math.max(a, b);
		long key = ((long) low << 32) | (high & 0xffffffffL);
		Long existing = edges.get(key);
		if (existing != null)
			return existing;
		Point origin = point(low), delta = point(high).minus(origin);
		double length = Math.hypot(Math.hypot(delta.x(), delta.y()), delta.z());
		if (!(length > 0))
			throw new IllegalArgumentException("Zero-length BRep edge");
		Point unit = new Point(delta.x() / length, delta.y() / length, delta.z() / length);
		long vector = emitter.add("VECTOR('',#" + direction(unit) + ",1.)");
		long curve = emitter.add("LINE('',#" + cartesian(origin) + ",#" + vector + ")");
		long edge = emitter.add("EDGE_CURVE('',#" + vertex(low) + ",#" + vertex(high) + ",#" + curve + ",.T.)");
		edges.put(key, edge);
		return edge;
	}

	private long vertex(int index) {
		return vertices.computeIfAbsent(index, v -> emitter.add("VERTEX_POINT('',#" + cartesian(point(v)) + ")"));
	}

	private Point point(int index) {
		return new Point(positions[3 * index], positions[3 * index + 1], positions[3 * index + 2]);
	}

	private long placement(Point origin, Point normal) {
		Point reference = normal.cross(Math.abs(normal.x()) < 0.9 ? new Point(1, 0, 0) : new Point(0, 1, 0));
		return placement(origin, normal, reference);
	}

	private long placement(Point origin, Point normal, Point reference) {
		return emitter.add("AXIS2_PLACEMENT_3D('',#" + cartesian(origin) + ",#" + direction(normal) + ",#"
				+ direction(reference) + ")");
	}

	private long cartesian(Point p) {
		return emitter.add("CARTESIAN_POINT(''," + coords(p) + ")");
	}

	private long direction(Point p) {
		return emitter.add("DIRECTION(''," + coords(p) + ")");
	}

	private static String coords(Point p) {
		return "(" + p.x() + "," + p.y() + "," + p.z() + ")";
	}

	private static String quote(String text) {
		return "'" + text.replace("'", "''") + "'";
	}

	private static String refs(List<Long> entities) {
		return entities.stream().map(id -> "#" + id).collect(Collectors.joining(",", "(", ")"));
	}
}
