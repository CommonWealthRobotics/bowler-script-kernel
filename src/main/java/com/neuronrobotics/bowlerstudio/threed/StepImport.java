package com.neuronrobotics.bowlerstudio.threed;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.neuronrobotics.bowlerstudio.threed.StepReader.Instance;
import com.neuronrobotics.bowlerstudio.threed.StepReader.StepException;
import com.neuronrobotics.bowlerstudio.threed.StepReader.Value;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.scene.paint.Color;

/**
 * Builds CSG geometry from the contents of a STEP file: tessellated meshes,
 * geometric curve sets and BRep topology. Geometry is returned in the model's
 * own length unit converted to millimetres, matching what {@link STEPFile}
 * writes.
 */
public final class StepImport {

	private static final Color IMPORT_COLOR = Color.GRAY;

	private StepImport() {
	}

	/** One shape from the file: its mesh plus any free edges. */
	public static final class Shape {
		private final String name;
		private final CSG mesh;
		private final List<FeatureEdgeExtractor.Edge> edges;

		private Shape(String name, CSG mesh, List<FeatureEdgeExtractor.Edge> edges) {
			this.name = name;
			this.mesh = mesh;
			this.edges = Collections.unmodifiableList(edges);
		}

		public String getName() {
			return name;
		}

		/** Mesh geometry, or null when the shape carries no tessellation. */
		public CSG getMesh() {
			return mesh;
		}

		/** Free edges, either from curve sets or from BRep topology. */
		public List<FeatureEdgeExtractor.Edge> getEdges() {
			return edges;
		}
	}

	/** Axis aligned bounds, for checking what a file actually described. */
	public static final class Bounds {
		public final double minX;
		public final double minY;
		public final double minZ;
		public final double maxX;
		public final double maxY;
		public final double maxZ;

		private Bounds(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			this.minX = minX;
			this.minY = minY;
			this.minZ = minZ;
			this.maxX = maxX;
			this.maxY = maxY;
			this.maxZ = maxZ;
		}

		public static Bounds of(CSG csg) {
			eu.mihosoft.vrl.v3d.Bounds bounds = csg.getBounds();
			return new Bounds(bounds.getMin().x, bounds.getMin().y, bounds.getMin().z, bounds.getMax().x,
					bounds.getMax().y, bounds.getMax().z);
		}

		public static Bounds of(List<FeatureEdgeExtractor.Edge> edges) {
			if (edges.isEmpty())
				throw new IllegalArgumentException("No edges to measure");

			double minX = Double.MAX_VALUE;
			double minY = Double.MAX_VALUE;
			double minZ = Double.MAX_VALUE;
			double maxX = -Double.MAX_VALUE;
			double maxY = -Double.MAX_VALUE;
			double maxZ = -Double.MAX_VALUE;

			for (FeatureEdgeExtractor.Edge edge : edges) {
				minX = Math.min(minX, Math.min(edge.a.x, edge.b.x));
				minY = Math.min(minY, Math.min(edge.a.y, edge.b.y));
				minZ = Math.min(minZ, Math.min(edge.a.z, edge.b.z));
				maxX = Math.max(maxX, Math.max(edge.a.x, edge.b.x));
				maxY = Math.max(maxY, Math.max(edge.a.y, edge.b.y));
				maxZ = Math.max(maxZ, Math.max(edge.a.z, edge.b.z));
			}

			return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
		}

		public double sizeX() {
			return maxX - minX;
		}

		public double sizeY() {
			return maxY - minY;
		}

		public double sizeZ() {
			return maxZ - minZ;
		}

		@Override
		public String toString() {
			return sizeX() + " x " + sizeY() + " x " + sizeZ();
		}
	}

	public static List<Shape> readShapes(InputStream stream) throws IOException {
		StepReader reader = StepReader.parse(stream);
		List<Instance> representations = representations(reader);
		List<Shape> shapes = new ArrayList<>();

		for (Instance representation : representations) {
			double scale = lengthScale(reader, representation);
			String name = text(reader.attribute(representation, "REPRESENTATION", "name"));

			// a solid model is one shape in its own right, so a representation that
			// holds several of them yields several shapes rather than one merged mesh
			List<Instance> solids = new ArrayList<>();
			List<Instance> items = new ArrayList<>();
			for (Value item : reader.list(reader.attribute(representation, "REPRESENTATION", "items"))) {
				Instance instance = reader.instance(item);
				if (StepBRep.isSolidModel(instance))
					solids.add(instance);
				else
					items.add(instance);
			}

			if (!solids.isEmpty()) {
				for (Instance solid : solids) {
					MeshBuilder mesh = new MeshBuilder(scale);
					List<FeatureEdgeExtractor.Edge> edges = new ArrayList<>();
					StepBRep.read(reader, solid, scale, mesh, edges);
					shapes.add(new Shape(text(reader.attribute(solid, "REPRESENTATION_ITEM", "name")), mesh.toCsg(name),
							unique(edges, scale)));
				}
			}

			if (!items.isEmpty()) {
				MeshBuilder mesh = new MeshBuilder(scale);
				List<FeatureEdgeExtractor.Edge> edges = new ArrayList<>();
				for (Instance item : items)
					readItem(reader, item, scale, mesh, edges);
				if (!mesh.isEmpty() || !edges.isEmpty())
					shapes.add(new Shape(name, mesh.toCsg(name), unique(edges, scale)));
			}
		}

		return distinct(shapes);
	}

	/**
	 * Drops repeated edges. A BRep edge is shared by two faces, so walking every
	 * face loop yields each edge twice; this leaves one edge per unique segment.
	 * Segments are matched to the precision the model is scaled to.
	 */
	private static List<FeatureEdgeExtractor.Edge> unique(List<FeatureEdgeExtractor.Edge> edges, double scale) {
		double tolerance = scale * 1e-7;
		java.util.LinkedHashMap<String, FeatureEdgeExtractor.Edge> unique = new java.util.LinkedHashMap<>();
		for (FeatureEdgeExtractor.Edge edge : edges)
			unique.putIfAbsent(key(edge, tolerance), edge);
		return new ArrayList<>(unique.values());
	}

	private static String key(FeatureEdgeExtractor.Edge edge, double tolerance) {
		String a = key(edge.a.x, edge.a.y, edge.a.z, tolerance);
		String b = key(edge.b.x, edge.b.y, edge.b.z, tolerance);
		return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
	}

	private static String key(double x, double y, double z, double tolerance) {
		return Math.round(x / tolerance) + "," + Math.round(y / tolerance) + "," + Math.round(z / tolerance);
	}

	private static void readItem(StepReader reader, Instance item, double scale, MeshBuilder mesh,
			List<FeatureEdgeExtractor.Edge> edges) {
		if (item.is("COORDINATES_LIST"))
			return; // only meaningful through the item that references it

		if (StepReader.kindOf(item, "TESSELLATED_SURFACE_SET"))
			readSurfaceSet(reader, item, scale, mesh);
		else if (StepReader.kindOf(item, "TRIANGULATED_FACE"))
			readTriangulatedFace(reader, item, scale, mesh);
		else if (StepReader.kindOf(item, "TESSELLATED_FACE"))
			readTessellatedFace(reader, item, scale, mesh);
		else if (item.is("TESSELLATED_SOLID") || item.is("TESSELLATED_SHELL"))
			for (Value nested : reader.list(reader.attribute(item, item.primaryEntity(), "items")))
				readItem(reader, reader.instance(nested), scale, mesh, edges);
		else if (item.is("GEOMETRIC_CURVE_SET") || item.is("GEOMETRIC_SET"))
			readCurveSet(reader, item, scale, edges);
		else if (StepBRep.isSolidModel(item))
			StepBRep.read(reader, item, scale, mesh, edges);
		else if (item.is("AXIS2_PLACEMENT_3D"))
			return; // the placement of the representation itself
	}

	// --- tessellated meshes ---

	private static void readSurfaceSet(StepReader reader, Instance set, double scale, MeshBuilder mesh) {
		Instance coordinates = reader.instance(reader.attribute(set, "TESSELLATED_SURFACE_SET", "coordinates"));
		List<Value> points = reader.list(reader.attribute(coordinates, "COORDINATES_LIST", "position_coords"));
		List<Value> triangles = reader.list(reader.attribute(set, "TRIANGULATED_SURFACE_SET", "triangles"));

		for (Value triangle : triangles) {
			List<Value> indices = triangle.list();
			mesh.addTriangle(indexed(points, indices.get(0), scale), indexed(points, indices.get(1), scale),
					indexed(points, indices.get(2), scale));
		}
	}

	private static void readTriangulatedFace(StepReader reader, Instance face, double scale, MeshBuilder mesh) {
		Instance coordinates = reader.instance(reader.attribute(face, "TESSELLATED_FACE", "coordinates"));
		List<Value> points = reader.list(reader.attribute(coordinates, "COORDINATES_LIST", "position_coords"));
		List<Value> triangles = reader.list(reader.attribute(face, "TRIANGULATED_FACE", "triangles"));

		for (Value triangle : triangles) {
			List<Value> indices = triangle.list();
			mesh.addTriangle(indexed(points, indices.get(0), scale), indexed(points, indices.get(1), scale),
					indexed(points, indices.get(2), scale));
		}
	}

	private static void readTessellatedFace(StepReader reader, Instance face, double scale, MeshBuilder mesh) {
		// a face without an index list still delimits its own point set as a polygon
		Instance coordinates = reader.instance(reader.attribute(face, "TESSELLATED_FACE", "coordinates"));
		List<Value> points = reader.list(reader.attribute(coordinates, "COORDINATES_LIST", "position_coords"));
		List<double[]> polygon = new ArrayList<>();
		for (Value point : points)
			polygon.add(scaled(point, scale));
		mesh.addPolygon(polygon);
	}

	private static double[] indexed(List<Value> points, Value index, double scale) {
		int position = (int) index.number() - 1; // STEP tessellation indices are 1-based
		if (position < 0 || position >= points.size())
			throw new StepException("Tessellation index " + (position + 1) + " is outside the coordinate list");
		return scaled(points.get(position), scale);
	}

	private static double value(Value number) {
		return number.unwrap().number();
	}

	private static double[] scaled(Value point, double factor) {
		List<Value> coordinates = point.isList() ? point.list() : null;
		if (coordinates == null)
			throw new StepException("Expected a coordinate tuple but found " + point.describe());
		return new double[]{value(coordinates.get(0)) * factor, value(coordinates.get(1)) * factor,
				value(coordinates.get(2)) * factor};
	}

	// --- curve sets ---

	private static void readCurveSet(StepReader reader, Instance set, double scale,
			List<FeatureEdgeExtractor.Edge> edges) {
		for (Value element : reader.list(reader.attribute(set, "GEOMETRIC_SET", "elements"))) {
			Instance curve = reader.instance(element);
			List<double[]> points = StepCurves.sample(reader, curve, null, null, 16);
			appendPolyline(points, scale, edges);
		}
	}

	/** Emits one edge per segment of a sampled polyline. */
	static void appendPolyline(List<double[]> points, double factor, List<FeatureEdgeExtractor.Edge> edges) {
		for (int i = 0; i + 1 < points.size(); i++)
			addSegment(scaled(points.get(i), factor), scaled(points.get(i + 1), factor), edges);
	}

	/** Appends the segment between two points, skipping it when they coincide. */
	static void addSegment(double[] from, double[] to, List<FeatureEdgeExtractor.Edge> edges) {
		double dx = to[0] - from[0];
		double dy = to[1] - from[1];
		double dz = to[2] - from[2];
		if (dx * dx + dy * dy + dz * dz == 0)
			return;
		edges.add(new FeatureEdgeExtractor.Edge(vector(from), vector(to)));
	}

	static Vector3d vector(double[] point) {
		return new Vector3d(point[0], point[1], point[2]);
	}

	static double[] scaled(double[] point, double factor) {
		if (factor == 1.0)
			return point;
		return new double[]{point[0] * factor, point[1] * factor, point[2] * factor};
	}

	// --- representations and units ---

	/**
	 * Every shape representation in the file. A file often describes one part in
	 * several representations, for instance the BRep and a tessellation of it, so
	 * representations that produce identical geometry are collapsed afterwards
	 * rather than by guessing which representation is authoritative.
	 */
	private static List<Instance> representations(StepReader reader) {
		List<Instance> shapes = new ArrayList<>();
		for (Instance instance : reader.instances().values())
			if (StepReader.kindOf(instance, "SHAPE_REPRESENTATION"))
				shapes.add(instance);
		return shapes;
	}

	/** Drops shapes whose geometry repeats an earlier shape. */
	private static List<Shape> distinct(List<Shape> shapes) {
		List<Shape> distinct = new ArrayList<>();
		java.util.Set<String> keys = new java.util.LinkedHashSet<>();
		for (Shape shape : shapes)
			if (keys.add(signature(shape)))
				distinct.add(shape);
		return distinct;
	}

	private static String signature(Shape shape) {
		StringBuilder key = new StringBuilder();
		if (shape.getMesh() != null) {
			CSG mesh = shape.getMesh();
			Bounds bounds = Bounds.of(mesh);
			key.append(mesh.getVertCount()).append('/').append(mesh.getTriCount()).append('/');
			key.append(round(bounds.minX)).append(',').append(round(bounds.minY)).append(',').append(round(bounds.minZ))
					.append(',');
			key.append(round(bounds.maxX)).append(',').append(round(bounds.maxY)).append(',')
					.append(round(bounds.maxZ));
		}
		key.append('|');
		if (!shape.getEdges().isEmpty()) {
			Bounds bounds = Bounds.of(shape.getEdges());
			key.append(shape.getEdges().size()).append('/');
			key.append(round(bounds.minX)).append(',').append(round(bounds.minY)).append(',').append(round(bounds.minZ))
					.append(',');
			key.append(round(bounds.maxX)).append(',').append(round(bounds.maxY)).append(',')
					.append(round(bounds.maxZ));
		}
		return key.toString();
	}

	private static String round(double value) {
		return Long.toString(Math.round(value * 1e6));
	}

	/**
	 * Millimetres per unit for one representation. The unit that applies is the
	 * length unit assigned to the representation's own context; a file that
	 * converts units, such as one written in inches, also carries the base unit
	 * that conversion refers to, so the conversion-based unit is preferred.
	 */
	static double lengthScale(StepReader reader, Instance representation) {
		Value context = reader.attribute(representation, "REPRESENTATION", "context_of_items");
		Instance assigned = reader.optionalInstance(context);
		if (assigned != null) {
			Value units = reader.optionalAttribute(assigned, "GLOBAL_UNIT_ASSIGNED_CONTEXT", "units");
			if (units != null) {
				Instance lengthUnit = null;
				for (Value unit : reader.list(units)) {
					Instance candidate = reader.instance(unit);
					if (!StepReader.kindOf(candidate, "LENGTH_UNIT"))
						continue;
					// a conversion-based unit is the model unit, a bare SI unit is the base
					if (lengthUnit == null || candidate.is("CONVERSION_BASED_UNIT"))
						lengthUnit = candidate;
				}
				if (lengthUnit != null)
					return millimetresPerUnit(reader, lengthUnit);
			}
		}

		return lengthScale(reader);
	}

	/** Millimetres per unit, when no context narrows it down. */
	static double lengthScale(StepReader reader) {
		Instance lengthUnit = null;
		for (Instance instance : reader.instances().values()) {
			if (!StepReader.kindOf(instance, "LENGTH_UNIT"))
				continue;
			if (lengthUnit == null || instance.is("CONVERSION_BASED_UNIT"))
				lengthUnit = instance;
		}
		return millimetresPerUnit(reader, lengthUnit);
	}

	/** Millimetres per one of the given unit. */
	private static double millimetresPerUnit(StepReader reader, Instance unit) {
		if (unit == null)
			return 1.0;

		if (unit.is("SI_UNIT"))
			return isMetre(reader, unit) ? millimetresPerMetre(prefix(reader, unit)) : 1.0;

		if (unit.is("CONVERSION_BASED_UNIT")) {
			// conversion_factor is a measure with unit, e.g. LENGTH_MEASURE_WITH_UNIT(2.54, centi-metre)
			Instance measure = reader
					.optionalInstance(reader.attribute(unit, "CONVERSION_BASED_UNIT", "conversion_factor"));
			if (measure == null)
				return 1.0;
			double factor = reader.number(reader.attribute(measure, "MEASURE_WITH_UNIT", "value_component"));
			Instance factorUnit = reader
					.optionalInstance(reader.attribute(measure, "MEASURE_WITH_UNIT", "unit_component"));
			return factor * millimetresPerUnit(reader, factorUnit);
		}

		return 1.0;
	}

	private static boolean isMetre(StepReader reader, Instance unit) {
		Value name = reader.attribute(unit, "SI_UNIT", "name");
		return name != null && name.kind() == Value.Kind.ENUM && "METRE".equals(name.enumeration());
	}

	private static String prefix(StepReader reader, Instance unit) {
		Value prefix = reader.attribute(unit, "SI_UNIT", "prefix");
		return prefix != null && prefix.kind() == Value.Kind.ENUM ? prefix.enumeration() : null;
	}

	private static double millimetresPerMetre(String prefix) {
		if (prefix == null)
			return 1000.0;
		switch (prefix) {
			case "MILLI" :
				return 1.0;
			case "CENTI" :
				return 10.0;
			case "DECI" :
				return 100.0;
			case "MICRO" :
				return 0.001;
			case "NANO" :
				return 1e-6;
			case "KILO" :
				return 1_000_000.0;
			default :
				return 1000.0;
		}
	}

	private static String text(Value value) {
		return value == null || value.kind() != Value.Kind.STRING ? "" : value.text();
	}

	/** Accumulates triangles, reusing positions so the result is an indexed mesh. */
	static final class MeshBuilder {
		private final double factor;
		private final Map<String, Integer> indices = new LinkedHashMap<>();
		private final List<double[]> vertices = new ArrayList<>();
		private final List<int[]> triangles = new ArrayList<>();

		private MeshBuilder(double factor) {
			this.factor = factor;
		}

		int vertex(double[] position) {
			double[] scaled = scaled(position, factor);
			String key = key(scaled);
			Integer existing = indices.get(key);
			if (existing != null)
				return existing;
			int index = vertices.size();
			indices.put(key, index);
			vertices.add(scaled);
			return index;
		}

		void addTriangle(double[] a, double[] b, double[] c) {
			int ia = vertex(a);
			int ib = vertex(b);
			int ic = vertex(c);
			if (ia == ib || ib == ic || ic == ia)
				return;
			triangles.add(new int[]{ia, ib, ic});
		}

		/** Triangulates a planar polygon given in order; used for tessellated faces. */
		void addPolygon(List<double[]> polygon) {
			List<Integer> loop = new ArrayList<>();
			for (double[] point : polygon)
				loop.add(vertex(point));
			for (int[] triangle : Triangulator.triangulate(loop, vertices))
				triangles.add(triangle);
		}

		/** Positions of the vertices accumulated so far, indexed by vertex number. */
		List<double[]> positions() {
			return vertices;
		}

		void addTriangle(int a, int b, int c) {
			if (a == b || b == c || c == a)
				return;
			triangles.add(new int[]{a, b, c});
		}

		boolean isEmpty() {
			return triangles.isEmpty();
		}

		private static String key(double[] position) {
			return Double.toString(position[0]) + "," + Double.toString(position[1]) + ","
					+ Double.toString(position[2]);
		}

		CSG toCsg(String name) {
			if (triangles.isEmpty())
				return null;

			double[] flat = new double[vertices.size() * 3];
			for (int i = 0; i < vertices.size(); i++) {
				flat[i * 3] = vertices.get(i)[0];
				flat[i * 3 + 1] = vertices.get(i)[1];
				flat[i * 3 + 2] = vertices.get(i)[2];
			}

			long[] flatTriangles = new long[triangles.size() * 3];
			for (int i = 0; i < triangles.size(); i++) {
				flatTriangles[i * 3] = triangles.get(i)[0];
				flatTriangles[i * 3 + 1] = triangles.get(i)[1];
				flatTriangles[i * 3 + 2] = triangles.get(i)[2];
			}

			CSG csg = new CSG(flat, flatTriangles, IMPORT_COLOR);
			if (name != null && !name.isBlank())
				csg.setName(name);
			return csg;
		}
	}
}
