package com.neuronrobotics.bowlerstudio.threed;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.vecmath.Matrix4d;

import com.neuronrobotics.bowlerstudio.threed.StepReader.Instance;
import com.neuronrobotics.bowlerstudio.threed.StepReader.StepException;
import com.neuronrobotics.bowlerstudio.threed.StepReader.Value;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.TickToc;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.CSG.OptType;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.scene.paint.Color;

/**
 * Builds CSG geometry from the contents of a STEP file: tessellated meshes,
 * geometric curve sets and BRep topology. Geometry is returned in the model's
 * own length unit converted to millimetres, matching what {@link STEPFile}
 * writes.
 *
 * <p>
 * An assembly is returned as one shape per placed part, with each part moved to
 * where its occurrence puts it. Colours a file assigns to its geometry come
 * through on the shape.
 */
public final class StepImport {

	private static final Color IMPORT_COLOR = Color.GRAY;

	private StepImport() {
	}

	/** One shape from the file: its mesh plus any free edges, and its colour. */
	public static final class Shape {
		private final String name;
		private final CSG mesh;
		private final List<FeatureEdgeExtractor.Edge> edges;
		private final Color color;
		private final List<StepFace> faces;

		private Shape(String name, CSG mesh, List<FeatureEdgeExtractor.Edge> edges, Color color, List<StepFace> faces) {
			this.name = name;
			this.mesh = mesh;
			this.edges = Collections.unmodifiableList(edges);
			this.color = color;
			this.faces = Collections.unmodifiableList(faces);
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

		/** The colour the file assigned to this shape, or null when it named none. */
		public Color getColor() {
			return color;
		}

		/** Planar BRep faces the file described, in file order; empty for a mesh only shape. */
		public List<StepFace> getFaces() {
			return faces;
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
		return readShapes(stream, true);
	}

	/**
	 * Reads the geometry in a STEP file.
	 *
	 * @param preferTessellation
	 *            when true, a part the file describes both as BRep topology and as
	 *            a tessellation of the same mesh is returned as the tessellation's
	 *            triangles, which are the exact source mesh. When false, such a part
	 *            is returned as its BRep faces triangulated, which is what a CAD
	 *            package sees, so the BRep's own coverage can be checked.
	 */
	public static List<Shape> readShapes(InputStream stream, boolean preferTessellation) throws IOException {
		Log.debug("StepImport.readShapes: parse");
		TickToc.tic("parse STEP");
		StepReader reader = StepReader.parse(stream);
		Log.debug("StepImport.readShapes: " + reader.instances().size() + " entities");
		TickToc.tic("index styles");
		Map<Integer, Color> colours = StepStyles.byItem(reader);
		TickToc.tic("index placements");
		Map<Integer, List<Transform>> placements = placements(reader);
		List<Shape> shapes = new ArrayList<>();

		// A file may describe one part twice: as BRep topology and as a tessellation
		// of the same shape. The BRep carries the part's placement, its faces and its
		// edges; the tessellation carries the exact mesh. Read them together so the
		// shape keeps its placement but gets the mesh the file actually stored.
		Map<Integer, Integer> tessellatedToBRep = tessellationRelationships(reader);
		Log.debug("StepImport.readShapes: " + tessellatedToBRep.size() + " BRep/tessellation pair(s)");
		Map<Integer, CSG> tessellations = new HashMap<>();
		if (preferTessellation)
			for (Map.Entry<Integer, Integer> relationship : tessellatedToBRep.entrySet()) {
				Instance tessellated = reader.instances().get(relationship.getKey());
				if (tessellated != null) {
					TickToc.tic("read tessellation " + relationship.getKey());
					tessellations.put(relationship.getValue(), readTessellation(reader, tessellated));
				}
			}

		TickToc.tic("read representations");
		for (Instance representation : representations(reader)) {
			// a tessellation is never a shape of its own: it either supplies the mesh
			// of its BRep twin or is ignored, so preferring the BRep does not make the
			// tessellation appear as a second shape
			if (tessellatedToBRep.containsKey(representation.id()))
				continue;

			double scale = lengthScale(reader, representation);
			Color colour = colourOf(reader, representation, colours);

			// a rep that an assembly places is drawn once per occurrence, moved each
			// time; a rep nothing places stands on its own
			List<Transform> transforms = placements.get(representation.id());
			if (transforms == null)
				transforms = Collections.singletonList(null);

			for (Transform transform : transforms) {
				List<Shape> placed = readRepresentation(reader, representation, scale, colour, transform,
						tessellations.get(representation.id()));
				shapes.addAll(placed);
			}
		}

		Log.debug("StepImport.readShapes: " + shapes.size() + " shape(s) before dedup");
		return distinct(shapes);
	}

	/** The exact mesh a tessellated representation holds, or null when it holds none. */
	private static CSG readTessellation(StepReader reader, Instance representation) {
		double scale = lengthScale(reader, representation);
		String name = text(reader.attribute(representation, "REPRESENTATION", "name"));
		MeshBuilder mesh = new MeshBuilder(scale);
		for (Value item : reader.list(reader.attribute(representation, "REPRESENTATION", "items")))
			readItem(reader, reader.instance(item), scale, mesh, new ArrayList<>());
		return mesh.toCsg(name, IMPORT_COLOR);
	}

	/**
	 * True when a representation carries indexed triangles rather than topology,
	 * so it reproduces the source mesh exactly.
	 */
	private static boolean isTessellated(StepReader reader, Instance representation) {
		for (Value item : reader.list(reader.attribute(representation, "REPRESENTATION", "items"))) {
			Instance instance = reader.instance(item);
			if (StepReader.kindOf(instance, "TESSELLATED_SURFACE_SET")
					|| StepReader.kindOf(instance, "TESSELLATED_FACE"))
				return true;
		}
		return false;
	}

	/**
	 * Tessellated representation to the BRep representation that describes the same
	 * shape, as the file's {@code SHAPE_REPRESENTATION_RELATIONSHIP} states. Names
	 * are not reliable: several parts of one model often share a name.
	 */
	private static Map<Integer, Integer> tessellationRelationships(StepReader reader) {
		Map<Integer, Integer> relationships = new HashMap<>();
		for (Instance instance : reader.instances().values()) {
			if (!StepReader.kindOf(instance, "REPRESENTATION_RELATIONSHIP"))
				continue;
			Instance first = reader
					.optionalInstance(reader.attribute(instance, "REPRESENTATION_RELATIONSHIP", "rep_1"));
			Instance second = reader
					.optionalInstance(reader.attribute(instance, "REPRESENTATION_RELATIONSHIP", "rep_2"));
			if (first == null || second == null)
				continue;

			if (isTessellated(reader, second) && isBRep(reader, first))
				relationships.put(second.id(), first.id());
			else if (isTessellated(reader, first) && isBRep(reader, second))
				relationships.put(first.id(), second.id());
		}
		return relationships;
	}

	private static boolean isBRep(StepReader reader, Instance representation) {
		return StepReader.kindOf(representation, "ADVANCED_BREP_SHAPE_REPRESENTATION")
				|| StepReader.kindOf(representation, "MANIFOLD_SURFACE_SHAPE_REPRESENTATION");
	}

	private static List<Shape> readRepresentation(StepReader reader, Instance representation, double scale,
			Color colour, Transform transform, CSG exactMesh) {
		String name = text(reader.attribute(representation, "REPRESENTATION", "name"));

		// placements are written in the file's own unit, so scale their translation
		// to millimetres along with the geometry they place
		Transform placement = scalePlacement(transform, scale);

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

		List<Shape> shapes = new ArrayList<>();

		if (!solids.isEmpty()) {
			for (Instance solid : solids) {
				MeshBuilder mesh = new MeshBuilder(scale);
				List<FeatureEdgeExtractor.Edge> edges = new ArrayList<>();
				StepBRep.read(reader, solid, scale, mesh, edges);
				// the tessellation, when the file has one, is the exact mesh of this
				// solid; the BRep only contributes the topology a mesh cannot express
				CSG source = exactMesh != null ? exactMesh : mesh.toCsg(name, colour);
				if (source != null && colour != null)
					source.setColor(colour);
				shapes.add(place(placement, new Shape(text(reader.attribute(solid, "REPRESENTATION_ITEM", "name")),
						source, unique(edges, scale), colour, StepBRep.faces(reader, solid, scale))));
			}
		}

		if (!items.isEmpty()) {
			MeshBuilder mesh = new MeshBuilder(scale);
			List<FeatureEdgeExtractor.Edge> edges = new ArrayList<>();
			List<StepFace> faces = new ArrayList<>();
			for (Instance item : items)
				readItem(reader, item, scale, mesh, edges);
			for (Instance item : items)
				if (StepBRep.isSolidModel(item))
					faces.addAll(StepBRep.faces(reader, item, scale));
			if (!mesh.isEmpty() || !edges.isEmpty())
				shapes.add(place(placement,
						new Shape(name, mesh.toCsg(name, colour), unique(edges, scale), colour, faces)));
		}

		return shapes;
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

	// --- assemblies: where each representation sits ---

	/** One part occurrence: the representation that is placed, and where. */
	private static final class Occurrence {
		private final int parent;
		private final int child;
		private final Transform transform;

		private Occurrence(int parent, int child, Transform transform) {
			this.parent = parent;
			this.child = child;
			this.transform = transform;
		}
	}

	/**
	 * World placement per representation id. An assembly relates each part's
	 * representation to the assembly's, through a
	 * {@code CONTEXT_DEPENDENT_SHAPE_REPRESENTATION} and its transformation, so
	 * following those relations from the parts nothing places gives every part its
	 * place. A representation with no mapping is a root and needs no placement.
	 */
	private static Map<Integer, List<Transform>> placements(StepReader reader) {
		Map<Integer, List<Occurrence>> children = new HashMap<>();
		Set<Integer> placed = new LinkedHashSet<>();

		for (Instance instance : reader.instances().values()) {
			Occurrence occurrence = occurrence(reader, instance);
			if (occurrence == null)
				continue;
			int parent = occurrence.parent;
			children.computeIfAbsent(parent, unused -> new ArrayList<>()).add(occurrence);
			placed.add(occurrence.child);
		}

		if (placed.isEmpty())
			return Map.of();

		Map<Integer, List<Transform>> placements = new HashMap<>();
		for (Instance representation : representations(reader))
			if (!placed.contains(representation.id()))
				descend(representation.id(), null, children, placements, new LinkedHashSet<>());

		return placements;
	}

	/** Reads one assembly relation, or null when the instance is not one. */
	private static Occurrence occurrence(StepReader reader, Instance instance) {
		if (!instance.is("CONTEXT_DEPENDENT_SHAPE_REPRESENTATION"))
			return null;

		Instance relation = reader.optionalInstance(
				reader.attribute(instance, "CONTEXT_DEPENDENT_SHAPE_REPRESENTATION", "representation_relation"));
		if (relation == null || !relation.is("REPRESENTATION_RELATIONSHIP_WITH_TRANSFORMATION"))
			return null;

		// rep_1 is the part's own representation, rep_2 the assembly's; both are
		// references, since a relationship relates representations rather than
		// repeating them
		Instance child = reader.optionalInstance(reader.attribute(relation, "REPRESENTATION_RELATIONSHIP", "rep_1"));
		Instance parent = reader.optionalInstance(reader.attribute(relation, "REPRESENTATION_RELATIONSHIP", "rep_2"));
		if (child == null || parent == null || child.id() == parent.id())
			return null;

		Transform transform = transformOf(reader, reader.optionalInstance(reader.attribute(relation,
				"REPRESENTATION_RELATIONSHIP_WITH_TRANSFORMATION", "transformation_operator")));
		if (transform == null)
			return null;

		return new Occurrence(parent.id(), child.id(), transform);
	}

	/**
	 * The placement an {@code ITEM_DEFINED_TRANSFORMATION} describes. Its first
	 * item is the frame the part is defined at within the assembly and the second
	 * the frame the occurrence refers to, so the part moves by the first inverted,
	 * composed with the second.
	 */
	private static Transform transformOf(StepReader reader, Instance transformation) {
		if (transformation == null || !transformation.is("ITEM_DEFINED_TRANSFORMATION"))
			return null;

		double[] inAssembly = frame(reader, reader
				.optionalInstance(reader.attribute(transformation, "ITEM_DEFINED_TRANSFORMATION", "transform_item_1")));
		double[] inPart = frame(reader, reader
				.optionalInstance(reader.attribute(transformation, "ITEM_DEFINED_TRANSFORMATION", "transform_item_2")));
		if (inPart == null)
			return null;
		if (inAssembly == null)
			return new Transform(new Matrix4d(inPart));

		return new Transform(new Matrix4d(multiply(inverse(inAssembly), inPart)));
	}

	private static void descend(int representation, Transform world, Map<Integer, List<Occurrence>> children,
			Map<Integer, List<Transform>> placements, Set<Integer> path) {
		placements.computeIfAbsent(representation, unused -> new ArrayList<>()).add(world);

		if (!path.add(representation))
			return; // a cycle cannot place anything new
		for (Occurrence occurrence : children.getOrDefault(representation, List.of()))
			descend(occurrence.child, compose(world, occurrence.transform), children, placements, path);
		path.remove(representation);
	}

	private static Transform compose(Transform parent, Transform child) {
		if (parent == null)
			return child;
		Matrix4d result = (Matrix4d) parent.getInternalMatrix().clone();
		result.mul(child.getInternalMatrix());
		return new Transform(result);
	}

	/** A placement as a row-major 4x4: the origin and the axes of its frame. */
	private static double[] frame(StepReader reader, Instance placement) {
		if (placement == null || !StepReader.kindOf(placement, "AXIS2_PLACEMENT_3D"))
			return null;

		double[][] frame = StepCurves.placement(reader, placement);
		double[] origin = frame[0];
		double[] x = frame[1];
		double[] y = frame[2];
		double[] z = frame[3];

		return new double[]{x[0], y[0], z[0], origin[0], x[1], y[1], z[1], origin[1], x[2], y[2], z[2], origin[2], 0, 0,
				0, 1};
	}

	/** Row-major matrix product. */
	private static double[] multiply(double[] a, double[] b) {
		double[] result = new double[16];
		for (int row = 0; row < 4; row++)
			for (int column = 0; column < 4; column++) {
				double sum = 0;
				for (int k = 0; k < 4; k++)
					sum += a[row * 4 + k] * b[k * 4 + column];
				result[row * 4 + column] = sum;
			}
		return result;
	}

	/** Inverse of a rigid frame: its rotation is orthonormal, so transpose it. */
	private static double[] inverse(double[] m) {
		double[] result = new double[16];
		result[0] = m[0];
		result[1] = m[4];
		result[2] = m[8];
		result[4] = m[1];
		result[5] = m[5];
		result[6] = m[9];
		result[8] = m[2];
		result[9] = m[6];
		result[10] = m[10];
		result[3] = -(m[0] * m[3] + m[4] * m[7] + m[8] * m[11]);
		result[7] = -(m[1] * m[3] + m[5] * m[7] + m[9] * m[11]);
		result[11] = -(m[2] * m[3] + m[6] * m[7] + m[10] * m[11]);
		result[15] = 1;
		return result;
	}

	/**
	 * Scales a placement's translation from the file's unit to millimetres. A
	 * placement's rotation carries no unit, so only the origin moves.
	 */
	private static Transform scalePlacement(Transform transform, double scale) {
		if (transform == null || scale == 1.0)
			return transform;

		Matrix4d matrix = (Matrix4d) transform.getInternalMatrix().clone();
		matrix.m03 *= scale;
		matrix.m13 *= scale;
		matrix.m23 *= scale;
		return new Transform(matrix);
	}

	/** Moves a shape to its occurrence, when it is in an assembly. */
	private static Shape place(Transform transform, Shape shape) {
		if (transform == null)
			return shape;

		CSG mesh = shape.getMesh();
		if (mesh != null) {
			// move the points in place: the occurrence only moves a part, so the mesh
			// keeps its own vertex order and indices
			double[] vertices = mesh.getVertices();
			if (vertices != null && !transform.isMirror()) {
				double[] moved = new double[vertices.length];
				for (int i = 0; i < vertices.length; i += 3) {
					Vector3d point = transform.transform(new Vector3d(vertices[i], vertices[i + 1], vertices[i + 2]));
					moved[i] = point.x;
					moved[i + 1] = point.y;
					moved[i + 2] = point.z;
				}
				mesh.setVertices(moved);
				mesh.setTriangles(mesh.getTriangles().clone());
				// moving the vertices invalidates the measured volume, and the volume is
				// what proves the shape is still a closed solid, so measure it again
				if (CSG.getDefaultOptionType() == OptType.Manifold3d)
					MeshBuilder.measure(mesh);
			} else
				// a mirror turns the surface inside out, which only the general path
				// knows how to express
				mesh = mesh.transformed(transform);
		}

		List<FeatureEdgeExtractor.Edge> edges = new ArrayList<>();
		for (FeatureEdgeExtractor.Edge edge : shape.getEdges())
			edges.add(new FeatureEdgeExtractor.Edge(transform.transform(edge.a.clone()),
					transform.transform(edge.b.clone())));

		return new Shape(shape.getName(), mesh, edges, shape.getColor(), moved(transform, shape.getFaces()));
	}

	/** Moves faces to an occurrence's frame, so they describe the shape where it sits. */
	private static List<StepFace> moved(Transform transform, List<StepFace> faces) {
		List<StepFace> moved = new ArrayList<>(faces.size());
		for (StepFace face : faces) {
			List<List<double[]>> loops = new ArrayList<>(face.loops.size());
			for (List<double[]> loop : face.loops) {
				List<double[]> points = new ArrayList<>(loop.size());
				for (double[] p : loop)
					points.add(vector(transform.transform(new Vector3d(p[0], p[1], p[2]))));
				loops.add(points);
			}
			// take the normal from two transformed points, so a translation is not
			// applied to a direction
			double[] origin = vector(transform.transform(new Vector3d(face.origin[0], face.origin[1], face.origin[2])));
			Vector3d along = transform.transform(new Vector3d(face.origin[0] + face.normal.x,
					face.origin[1] + face.normal.y, face.origin[2] + face.normal.z));
			Vector3d normal = new Vector3d(along.x - origin[0], along.y - origin[1], along.z - origin[2]).normalized();
			moved.add(new StepFace(origin, normal, loops));
		}
		return moved;
	}

	private static double[] vector(Vector3d v) {
		return new double[]{v.x, v.y, v.z};
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

		mesh.setMesh(points, triangles, scale);
	}

	private static void readTriangulatedFace(StepReader reader, Instance face, double scale, MeshBuilder mesh) {
		Instance coordinates = reader.instance(reader.attribute(face, "TESSELLATED_FACE", "coordinates"));
		List<Value> points = reader.list(reader.attribute(coordinates, "COORDINATES_LIST", "position_coords"));
		List<Value> triangles = reader.list(reader.attribute(face, "TRIANGULATED_FACE", "triangles"));

		mesh.setMesh(points, triangles, scale);
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

	// --- colour ---

	/** The colour of a representation, from any of the items it holds. */
	private static Color colourOf(StepReader reader, Instance representation, Map<Integer, Color> colours) {
		if (colours.isEmpty())
			return null;

		for (Value item : reader.list(reader.attribute(representation, "REPRESENTATION", "items"))) {
			Instance instance = reader.instance(item);
			Color colour = colours.get(instance.id());
			if (colour != null)
				return colour;

			// a tessellated solid or shell wraps its coloured faces
			if (instance.is("TESSELLATED_SOLID") || instance.is("TESSELLATED_SHELL")) {
				for (Value nested : reader.list(reader.attribute(instance, instance.primaryEntity(), "items"))) {
					colour = colours.get(reader.instance(nested).id());
					if (colour != null)
						return colour;
				}
			}
		}
		return null;
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

	/** Drops shapes whose geometry and colour repeat an earlier shape. */
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
		key.append('|');
		key.append(shape.getColor());
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
		/** True once the builder took a tessellation's own indices verbatim. */
		private boolean exact;

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

		/**
		 * Takes a tessellation's own point list and indices, keeping both exactly as
		 * the file wrote them: no welding, no reordering. That is what makes a
		 * tessellated round trip reproduce the source mesh.
		 */
		void setMesh(List<Value> points, List<Value> triangles, double factor) {
			exact = true;
			int base = vertices.size();
			for (Value point : points)
				vertices.add(scaled(point, factor));
			for (Value triangle : triangles) {
				List<Value> indices = triangle.list();
				this.triangles.add(new int[]{base + (int) indices.get(0).number() - 1,
						base + (int) indices.get(1).number() - 1, base + (int) indices.get(2).number() - 1});
			}
		}

		boolean isEmpty() {
			return triangles.isEmpty();
		}

		private static String key(double[] position) {
			return Double.toString(position[0]) + "," + Double.toString(position[1]) + ","
					+ Double.toString(position[2]);
		}

		CSG toCsg(String name) {
			return toCsg(name, IMPORT_COLOR);
		}

		CSG toCsg(String name, Color color) {
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

			CSG csg = new CSG(flat, flatTriangles, color == null ? IMPORT_COLOR : color);
			if (name != null && !name.isBlank())
				csg.setName(name);
			// Every mesh a loader hands back is measured, because measuring it is how the
			// kernel checks it is manifold: a mesh that is not closed has no volume and
			// the kernel says so. The measurement must not renumber the triangles, or a
			// tessellated round trip would stop reproducing the source, so the result is
			// taken for its numbers only and the caller's own vertex and index order is
			// kept.
			if (CSG.getDefaultOptionType() == OptType.Manifold3d)
				measure(csg);
			return csg;
		}

		/**
		 * Runs the mesh through the kernel to measure it, which is also the check that
		 * it is manifold, and copies the measured volume and surface area back onto the
		 * mesh without disturbing its own vertex or triangle order.
		 *
		 * @throws RuntimeException
		 *             when the kernel rejects the mesh as non-manifold
		 */
		static void measure(CSG csg) {
			CSG measured;
			try {
				measured = CSG.getManifold().calculateAreaAndSurfaceArea(csg);
			} catch (Throwable e) {
				throw new RuntimeException("STEP geometry is not a manifold solid", e);
			}
			csg.setVolume(measured.getVolume());
			csg.setSurfaceArea(measured.getSurfaceArea());
		}
	}
}
