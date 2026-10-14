package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;

/**
 * A CSG's geometry: the mesh, the feature edges extracted from it, and the
 * geometry read back out of a STEP file.
 */
public final class STEPFile {

	private static final String SCHEMA = "AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF {1 0 10303 442 1 1 4 }";
	private static final String PROTOCOL = "ap242_managed_model_based_3d_engineering_mim_lf";
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

	private final CSG csg;
	private final List<FeatureEdgeExtractor.Edge> edges;
	private final List<StepImport.Shape> shapes;

	public STEPFile(CSG csg, double angleDegrees, double topologyRepairTolerance, double stitchTolerance) {
		if (csg == null)
			throw new IllegalArgumentException("csg must not be null");

		this.csg = csg;
		this.edges = Collections.unmodifiableList(new ArrayList<>(
				FeatureEdgeExtractor.extract(csg, angleDegrees, topologyRepairTolerance, stitchTolerance)));
		this.shapes = List.of();
	}

	/** Reads geometry from a STEP file, see {@link StepImport#readShapes(InputStream)}. */
	public STEPFile(InputStream step) throws IOException {
		this.csg = null;
		this.edges = List.of();
		this.shapes = StepImport.readShapes(step);
	}

	/** The CSG this instance was built from, or null after reading a STEP file. */
	public CSG getCsg() {
		return csg;
	}

	/** Feature edges of {@link #getCsg()}, in extraction order. */
	public List<FeatureEdgeExtractor.Edge> getEdges() {
		return edges;
	}

	/**
	 * Geometry read from a STEP file: one shape per representation, each with its
	 * mesh and any edges. Empty when this instance was built from a CSG.
	 */
	public List<StepImport.Shape> getShapes() {
		return shapes;
	}

	/** Meshes of the shapes read from a STEP file, in file order. */
	public List<CSG> getImported() {
		List<CSG> meshes = new ArrayList<>();
		for (StepImport.Shape shape : shapes)
			if (shape.getMesh() != null)
				meshes.add(shape.getMesh());
		return Collections.unmodifiableList(meshes);
	}

	/**
	 * The mesh as a single {@code TRIANGULATED_SURFACE_SET} plus the feature edges
	 * as a {@code GEOMETRIC_CURVE_SET} of straight curves, in millimetres.
	 */
	public String toStep() {
		return render(name() + ".step", name());
	}

	public void write(File file) throws IOException {
		Files.write(file.toPath(), toStep().getBytes(StandardCharsets.UTF_8));
	}

	private String render(String fileName, String productName) {
		Emitter emitter = new Emitter();

		long applicationContext = emitter.add("APPLICATION_CONTEXT('mechanical design')");
		emitter.add("APPLICATION_PROTOCOL_DEFINITION('international standard','" + PROTOCOL + "',2020,#"
				+ applicationContext + ")");
		long productContext = emitter.add("PRODUCT_CONTEXT('',#" + applicationContext + ",'mechanical')");
		long product = emitter
				.add("PRODUCT(" + quote(productName) + "," + quote(productName) + ",'',(#" + productContext + "))");
		long formation = emitter.add("PRODUCT_DEFINITION_FORMATION('','',#" + product + ")");
		long definitionContext = emitter
				.add("PRODUCT_DEFINITION_CONTEXT('part definition',#" + applicationContext + ",'design')");
		long definition = emitter.add("PRODUCT_DEFINITION('design','',#" + formation + ",#" + definitionContext + ")");
		long definitionShape = emitter.add("PRODUCT_DEFINITION_SHAPE('','',#" + definition + ")");

		long lengthUnit = emitter.add("(LENGTH_UNIT()NAMED_UNIT(*)SI_UNIT(.MILLI.,.METRE.))");
		long uncertainty = emitter.add("UNCERTAINTY_MEASURE_WITH_UNIT(LENGTH_MEASURE(1.E-6),#" + lengthUnit
				+ ",'distance_accuracy_value','')");
		long context = emitter
				.add("(GEOMETRIC_REPRESENTATION_CONTEXT(3)GLOBAL_UNCERTAINTY_ASSIGNED_CONTEXT((#" + uncertainty
						+ "))GLOBAL_UNIT_ASSIGNED_CONTEXT((#" + lengthUnit + "))REPRESENTATION_CONTEXT('3D',''))");

		int vertexCount = (int) csg.getVertCount();
		List<int[]> triangles = triangleIndices(csg.getTriangles(), vertexCount);
		List<FeatureEdgeExtractor.Edge> segments = segments();

		List<Long> items = new ArrayList<>();
		if (!triangles.isEmpty())
			items.add(mesh(emitter, triangles, vertexCount));
		if (!segments.isEmpty())
			items.add(curveSet(emitter, segments));

		if (items.isEmpty())
			throw new IllegalStateException("No geometry to export");

		StringBuilder references = new StringBuilder("(");
		for (int i = 0; i < items.size(); i++) {
			if (i > 0)
				references.append(',');
			references.append('#').append(items.get(i));
		}
		references.append(')');

		long representation = emitter
				.add("SHAPE_REPRESENTATION(" + quote(productName) + "," + references + ",#" + context + ")");
		emitter.add("SHAPE_DEFINITION_REPRESENTATION(#" + definitionShape + ",#" + representation + ")");

		StringBuilder file = new StringBuilder();
		file.append("ISO-10303-21;\n");
		file.append("HEADER;\n");
		file.append("FILE_DESCRIPTION(('CaDoodle CSG'),'2;1');\n");
		file.append("FILE_NAME(").append(quote(fileName)).append(',')
				.append(quote(LocalDateTime.now().format(TIMESTAMP)))
				.append(",('CaDoodle'),('CaDoodle'),'STEPFile','CaDoodle','');\n");
		file.append("FILE_SCHEMA(('").append(SCHEMA).append("'));\n");
		file.append("ENDSEC;\n");
		file.append("DATA;\n");
		file.append(emitter.out);
		file.append("ENDSEC;\n");
		file.append("END-ISO-10303-21;\n");
		return file.toString();
	}

	/** 1-based triangle indices, degenerate triangles dropped, or empty if unusable. */
	private static List<int[]> triangleIndices(long[] triangles, int vertexCount) {
		List<int[]> indices = new ArrayList<>();

		if (triangles == null || vertexCount == 0)
			return indices;

		for (int t = 0; t + 2 < triangles.length; t += 3) {
			int a = (int) triangles[t];
			int b = (int) triangles[t + 1];
			int c = (int) triangles[t + 2];

			if (a == b || b == c || c == a)
				continue;
			if (a < 0 || b < 0 || c < 0 || a >= vertexCount || b >= vertexCount || c >= vertexCount)
				throw new IllegalArgumentException(
						"Triangle " + (t / 3) + " references a vertex outside 0.." + (vertexCount - 1));

			indices.add(new int[]{a + 1, b + 1, c + 1});
		}

		return indices;
	}

	/** Edges of non-zero length; a STEP DIRECTION cannot be the zero vector. */
	private List<FeatureEdgeExtractor.Edge> segments() {
		List<FeatureEdgeExtractor.Edge> segments = new ArrayList<>();
		for (FeatureEdgeExtractor.Edge edge : edges) {
			double dx = edge.b.x - edge.a.x;
			double dy = edge.b.y - edge.a.y;
			double dz = edge.b.z - edge.a.z;
			if (dx * dx + dy * dy + dz * dz > 0)
				segments.add(edge);
		}
		return segments;
	}

	private long mesh(Emitter emitter, List<int[]> triangles, int vertexCount) {
		StringBuilder points = new StringBuilder("(");
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			if (vertex > 0)
				points.append(',');
			points.append(point(csg.getVertex_X(vertex), csg.getVertex_Y(vertex), csg.getVertex_Z(vertex)));
		}
		points.append(')');

		StringBuilder indices = new StringBuilder("(");
		for (int t = 0; t < triangles.size(); t++) {
			int[] triangle = triangles.get(t);
			if (t > 0)
				indices.append(',');
			indices.append('(').append(triangle[0]).append(',').append(triangle[1]).append(',').append(triangle[2])
					.append(')');
		}
		indices.append(')');

		long coordinates = emitter.add("COORDINATES_LIST(''," + vertexCount + "," + points + ")");

		return emitter
				.add("TRIANGULATED_SURFACE_SET('mesh',#" + coordinates + "," + vertexCount + ",(),()," + indices + ")");
	}

	private static long curveSet(Emitter emitter, List<FeatureEdgeExtractor.Edge> segments) {
		StringBuilder references = new StringBuilder("(");
		for (int i = 0; i < segments.size(); i++) {
			FeatureEdgeExtractor.Edge edge = segments.get(i);
			if (i > 0)
				references.append(',');
			references.append('#').append(curve(emitter, edge.a, edge.b));
		}
		references.append(')');

		return emitter.add("GEOMETRIC_CURVE_SET('edges'," + references + ")");
	}

	/** Straight segment from a to b as a LINE trimmed by its two end points. */
	private static long curve(Emitter emitter, Vector3d a, Vector3d b) {
		double dx = b.x - a.x;
		double dy = b.y - a.y;
		double dz = b.z - a.z;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

		long origin = emitter.add("CARTESIAN_POINT(''," + point(a) + ")");
		long direction = emitter.add("DIRECTION(''," + point(dx / length, dy / length, dz / length) + ")");
		long vector = emitter.add("VECTOR('',#" + direction + "," + real(length) + ")");
		long line = emitter.add("LINE('',#" + origin + ",#" + vector + ")");
		long start = emitter.add("CARTESIAN_POINT(''," + point(a) + ")");
		long end = emitter.add("CARTESIAN_POINT(''," + point(b) + ")");

		return emitter.add("TRIMMED_CURVE('',#" + line + ",(#" + start + "),(#" + end + "),.T.,.CARTESIAN.)");
	}

	private static String point(Vector3d point) {
		return point(point.x, point.y, point.z);
	}

	private static String point(double x, double y, double z) {
		return "(" + real(x) + "," + real(y) + "," + real(z) + ")";
	}

	private static String real(double value) {
		if (!Double.isFinite(value))
			throw new IllegalArgumentException("Non-finite STEP value " + value);
		return Double.toString(value);
	}

	/** STEP string literal: quotes doubled, reverse solidus escaped, control characters dropped. */
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

	private String name() {
		String name = csg.getName();
		return name == null || name.isBlank() ? "shape" : name.trim();
	}

	private static final class Emitter {
		private final StringBuilder out = new StringBuilder();
		private long nextId;

		private long add(String entity) {
			long id = ++nextId;
			out.append('#').append(id).append('=').append(entity).append(';').append('\n');
			return id;
		}
	}
}
