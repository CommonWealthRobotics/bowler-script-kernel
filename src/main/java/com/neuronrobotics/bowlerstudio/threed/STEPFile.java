package com.neuronrobotics.bowlerstudio.threed;

import java.io.File;
import java.io.FileInputStream;
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
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.TickToc;
import javafx.scene.paint.Color;

/**
 * Writes CSG geometry as a STEP file, either a single part or an assembly of
 * several, and reads the geometry back out of a STEP file.
 */
public final class STEPFile {

	private static final String SCHEMA = "AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF {1 0 10303 442 1 1 4 }";
	private static final String PROTOCOL = "ap242_managed_model_based_3d_engineering_mim_lf";
	private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

	/** Feature edge extraction defaults, for callers that do not choose their own. */
	public static final double DEFAULT_ANGLE_DEGREES = 35.0;
	public static final double DEFAULT_TOPOLOGY_TOLERANCE = 0.00001;
	public static final double DEFAULT_STITCH_TOLERANCE = 0.002;

	private static final String ASSEMBLY_NAME = "assembly";

	/** One part to write: its geometry and the feature edges taken from it. */
	private static final class Part {
		private final CSG csg;
		private final List<FeatureEdgeExtractor.Edge> edges;

		private Part(CSG csg, List<FeatureEdgeExtractor.Edge> edges) {
			this.csg = csg;
			this.edges = edges;
		}

		private String name() {
			String name = csg.getName();
			return name == null || name.isBlank() ? "shape" : name.trim();
		}
	}

	private final List<Part> parts;
	private final List<StepImport.Shape> shapes;

	/** One part with the default feature edge settings. */
	public STEPFile(CSG csg) {
		this(Collections.singletonList(csg), DEFAULT_ANGLE_DEGREES, DEFAULT_TOPOLOGY_TOLERANCE,
				DEFAULT_STITCH_TOLERANCE);
	}

	/** An assembly of parts with the default feature edge settings. */
	public STEPFile(List<CSG> csgs) {
		this(csgs, DEFAULT_ANGLE_DEGREES, DEFAULT_TOPOLOGY_TOLERANCE, DEFAULT_STITCH_TOLERANCE);
	}

	public STEPFile(CSG csg, double angleDegrees, double topologyRepairTolerance, double stitchTolerance) {
		this(Collections.singletonList(csg), angleDegrees, topologyRepairTolerance, stitchTolerance);
	}

	/**
	 * The parts to write. Each keeps its own coordinates, so an assembly places
	 * every part where it already is.
	 */
	public STEPFile(List<CSG> csgs, double angleDegrees, double topologyRepairTolerance, double stitchTolerance) {
		if (csgs == null || csgs.isEmpty())
			throw new IllegalArgumentException("csgs must not be empty");

		List<Part> parts = new ArrayList<>();
		for (CSG csg : csgs) {
			if (csg == null)
				throw new IllegalArgumentException("csgs must not contain null");
			parts.add(new Part(csg,
					FeatureEdgeExtractor.extract(csg, angleDegrees, topologyRepairTolerance, stitchTolerance)));
		}
		this.parts = Collections.unmodifiableList(parts);
		this.shapes = List.of();
	}

	/** Reads geometry from a STEP file, see {@link StepImport#readShapes(InputStream)}. */
	public STEPFile(InputStream step) throws IOException {
		this(step, true);
	}

	/**
	 * Reads geometry from a STEP file.
	 *
	 * @param preferTessellation
	 *            when false, a part the file describes both as BRep topology and as
	 *            a tessellation is read from its BRep faces, which is what a CAD
	 *            package shows; use this to check that the BRep covers the mesh.
	 */
	public STEPFile(InputStream step, boolean preferTessellation) throws IOException {
		this.parts = List.of();
		this.shapes = StepImport.readShapes(step, preferTessellation);
	}

	/**
	 * Reads every file into its own STEPFile, one per file, in the order given.
	 * Use this to import a set of STEP files at once.
	 */
	public static List<STEPFile> read(List<File> files) throws IOException {
		List<STEPFile> read = new ArrayList<>();
		for (File file : files)
			try (InputStream stream = new FileInputStream(file)) {
				read.add(new STEPFile(stream));
			}
		return read;
	}

	/** The first part's CSG, or null after reading a STEP file. */
	public CSG getCsg() {
		return parts.isEmpty() ? null : parts.get(0).csg;
	}

	/** Feature edges of {@link #getCsg()}, empty after reading a STEP file. */
	public List<FeatureEdgeExtractor.Edge> getEdges() {
		return parts.isEmpty() ? List.of() : parts.get(0).edges;
	}

	/**
	 * The planar faces the first part is written as: their planes and boundary
	 * loops. Empty after reading a STEP file; see {@link #getShapes()} for the
	 * faces a file described.
	 */
	public List<StepFace> getFaces() {
		return parts.isEmpty() ? List.of() : StepBRepWrite.faces(parts.get(0).csg);
	}

	/** The CSGs to write, in order; empty after reading a STEP file. */
	public List<CSG> getParts() {
		List<CSG> csgs = new ArrayList<>();
		for (Part part : parts)
			csgs.add(part.csg);
		return Collections.unmodifiableList(csgs);
	}

	/**
	 * Geometry read from a STEP file: one shape per representation, each with its
	 * mesh and any edges. Empty when this instance was built from CSGs.
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
	 * Every part in millimetres: its coplanar triangles as BRep faces bounded by
	 * connected wires, so a CAD package shows a solid with selectable faces, plus a
	 * tessellated representation of the mesh itself so a reader reproduces the
	 * source triangles exactly. Several parts become a STEP assembly with one
	 * product per part.
	 */
	public String toStep() {
		if (parts.isEmpty())
			throw new IllegalStateException("No geometry to export: this instance was read from a STEP file");

		Log.debug("STEPFile.toStep: " + parts.size() + " part(s)");
		TickToc.tic("toStep");
		StepEmitter emitter = new StepEmitter();
		String fileName;
		if (parts.size() == 1) {
			fileName = parts.get(0).name() + ".step";
			part(emitter, parts.get(0));
		} else {
			fileName = ASSEMBLY_NAME + ".step";
			assembly(emitter, parts);
		}
		Log.debug("STEPFile.toStep: emitted " + emitter.text().length() + " characters");
		return file(fileName, emitter.text());
	}

	public void write(File file) throws IOException {
		Files.write(file.toPath(), toStep().getBytes(StandardCharsets.UTF_8));
	}

	/** The file for one part, with no assembly structure around it. */
	private static void part(StepEmitter emitter, Part part) {
		Contexts contexts = contexts(emitter);
		Product product = product(emitter, contexts, part.name());

		List<Long> items = StepBRepWrite.items(emitter, part.csg, part.name());
		// a representation must carry a shape, so a part with no mesh cannot be
		// written on its own
		if (items.size() < 2)
			throw new IllegalStateException("No geometry to export for part " + part.name());

		long representation = emitter.add("ADVANCED_BREP_SHAPE_REPRESENTATION(" + quote(part.name()) + ","
				+ references(items) + ",#" + contexts.representation + ")");
		emitter.add("SHAPE_DEFINITION_REPRESENTATION(#" + product.definitionShape + ",#" + representation + ")");
		tessellation(emitter, part, contexts, representation);
		colour(emitter, part.csg, contexts.representation, items);
	}

	/**
	 * The part's mesh as its own representation, related to the BRep one. A CAD
	 * package reads the BRep; a reader that can use the tessellation reproduces
	 * the source triangles exactly instead of re-triangulating the faces.
	 */
	private static void tessellation(StepEmitter emitter, Part part, Contexts contexts, long brep) {
		long tessellated = StepTessellatedWrite.representation(emitter, part.csg, part.name(), contexts.representation);
		if (tessellated == 0)
			return;
		// plain relationship: the tessellation describes the same shape, at the same
		// coordinates, so there is nothing to transform
		emitter.add("SHAPE_REPRESENTATION_RELATIONSHIP(" + quote(part.name()) + "," + quote("tessellation of the BRep")
				+ ",#" + brep + ",#" + tessellated + ")");
	}

	/**
	 * The file for several parts: an assembly product whose representation places
	 * one occurrence of every part product, following the AP242 assembly pattern
	 * of {@code NEXT_ASSEMBLY_USAGE_OCCURRENCE} plus a transformed relationship.
	 */
	private static void assembly(StepEmitter emitter, List<Part> parts) {
		Contexts contexts = contexts(emitter);
		Product assembly = product(emitter, contexts, ASSEMBLY_NAME);

		long[] definitions = new long[parts.size()];
		long[] partPlacements = new long[parts.size()];
		long[] representations = new long[parts.size()];
		for (int i = 0; i < parts.size(); i++) {
			Part part = parts.get(i);
			Product product = product(emitter, contexts, part.name());
			definitions[i] = product.definition;

			// the part's own representation, at its own coordinates, reaches the
			// solid through the item list, so only the geometry is coloured
			List<Long> items = StepBRepWrite.items(emitter, part.csg, part.name());
			List<Long> geometry = items.size() > 1 ? List.of(items.get(1)) : items;
			partPlacements[i] = placement(emitter);

			List<Long> representationItems = new ArrayList<>();
			representationItems.add(partPlacements[i]);
			representationItems.addAll(geometry);

			representations[i] = emitter.add("ADVANCED_BREP_SHAPE_REPRESENTATION(" + quote(part.name()) + ","
					+ references(representationItems) + ",#" + contexts.representation + ")");
			emitter.add(
					"SHAPE_DEFINITION_REPRESENTATION(#" + product.definitionShape + ",#" + representations[i] + ")");
			tessellation(emitter, part, contexts, representations[i]);
			colour(emitter, part.csg, contexts.representation, geometry);
		}

		List<Long> assemblyItems = new ArrayList<>();
		long[] assemblyPlacements = new long[parts.size()];
		for (int i = 0; i < parts.size(); i++) {
			assemblyPlacements[i] = placement(emitter);
			assemblyItems.add(assemblyPlacements[i]);
		}

		long assemblyRepresentation = emitter.add("SHAPE_REPRESENTATION(" + quote(ASSEMBLY_NAME) + ","
				+ references(assemblyItems) + ",#" + contexts.representation + ")");
		emitter.add(
				"SHAPE_DEFINITION_REPRESENTATION(#" + assembly.definitionShape + ",#" + assemblyRepresentation + ")");

		for (int i = 0; i < parts.size(); i++) {
			String name = parts.get(i).name();
			long occurrence = emitter.add("NEXT_ASSEMBLY_USAGE_OCCURRENCE(" + quote(Integer.toString(i)) + ","
					+ quote(name) + "," + quote(name) + ",#" + assembly.definition + ",#" + definitions[i] + ","
					+ quote(name) + ")");
			long occurrenceShape = emitter.add("PRODUCT_DEFINITION_SHAPE('','',#" + occurrence + ")");
			long transform = emitter.add(
					"ITEM_DEFINED_TRANSFORMATION('','',#" + assemblyPlacements[i] + ",#" + partPlacements[i] + ")");
			long relationship = emitter
					.add("(REPRESENTATION_RELATIONSHIP(" + quote(name) + "," + quote(name) + ",#" + representations[i]
							+ ",#" + assemblyRepresentation + ")REPRESENTATION_RELATIONSHIP_WITH_TRANSFORMATION(#"
							+ transform + ")SHAPE_REPRESENTATION_RELATIONSHIP())");
			emitter.add("CONTEXT_DEPENDENT_SHAPE_REPRESENTATION(#" + relationship + ",#" + occurrenceShape + ")");
		}
	}

	/** The representation-wide entities every product in the file refers to. */
	private static final class Contexts {
		private final long product;
		private final long definition;
		private final long representation;

		private Contexts(long product, long definition, long representation) {
			this.product = product;
			this.definition = definition;
			this.representation = representation;
		}
	}

	private static Contexts contexts(StepEmitter emitter) {
		long application = emitter.add("APPLICATION_CONTEXT('mechanical design')");
		emitter.add("APPLICATION_PROTOCOL_DEFINITION('international standard','" + PROTOCOL + "',2020,#" + application
				+ ")");

		long product = emitter.add("PRODUCT_CONTEXT('',#" + application + ",'mechanical')");
		long definition = emitter.add("PRODUCT_DEFINITION_CONTEXT('part definition',#" + application + ",'design')");

		long lengthUnit = emitter.add("(LENGTH_UNIT()NAMED_UNIT(*)SI_UNIT(.MILLI.,.METRE.))");
		long uncertainty = emitter.add("UNCERTAINTY_MEASURE_WITH_UNIT(LENGTH_MEASURE(1.E-6),#" + lengthUnit
				+ ",'distance_accuracy_value','')");
		long representation = emitter
				.add("(GEOMETRIC_REPRESENTATION_CONTEXT(3)GLOBAL_UNCERTAINTY_ASSIGNED_CONTEXT((#" + uncertainty
						+ "))GLOBAL_UNIT_ASSIGNED_CONTEXT((#" + lengthUnit + "))REPRESENTATION_CONTEXT('3D',''))");

		return new Contexts(product, definition, representation);
	}

	/** A product definition and the shape definition that gives it geometry. */
	private static final class Product {
		private final long definition;
		private final long definitionShape;

		private Product(long definition, long definitionShape) {
			this.definition = definition;
			this.definitionShape = definitionShape;
		}
	}

	private static Product product(StepEmitter emitter, Contexts contexts, String name) {
		long product = emitter.add("PRODUCT(" + quote(name) + "," + quote(name) + ",'',(#" + contexts.product + "))");
		long formation = emitter.add("PRODUCT_DEFINITION_FORMATION('','',#" + product + ")");
		long definition = emitter
				.add("PRODUCT_DEFINITION('design','',#" + formation + ",#" + contexts.definition + ")");
		long definitionShape = emitter.add("PRODUCT_DEFINITION_SHAPE('','',#" + definition + ")");
		return new Product(definition, definitionShape);
	}


	private static long placement(StepEmitter emitter) {
		long origin = emitter.add("CARTESIAN_POINT('',(0.0,0.0,0.0))");
		long axis = emitter.add("DIRECTION('',(0.0,0.0,1.0))");
		long direction = emitter.add("DIRECTION('',(1.0,0.0,0.0))");
		return emitter.add("AXIS2_PLACEMENT_3D('',#" + origin + ",#" + axis + ",#" + direction + ")");
	}

	/**
	 * Attaches the part's colour to its geometry, as a
	 * {@code MECHANICAL_DESIGN_GEOMETRIC_PRESENTATION_REPRESENTATION} holding a
	 * styled item with a surface and curve style, which is how readers expect a
	 * colour to reach solid and wireframe geometry.
	 */
	private static void colour(StepEmitter emitter, CSG csg, long context, List<Long> geometry) {
		Color color = csg.getColor();
		if (color == null || color.getOpacity() == 0)
			return;

		int red = (int) Math.round(color.getRed() * 255);
		int green = (int) Math.round(color.getGreen() * 255);
		int blue = (int) Math.round(color.getBlue() * 255);

		long rgb = emitter.add("COLOUR_RGB(''," + channel(red) + "," + channel(green) + "," + channel(blue) + ")");
		long fillColour = emitter.add("FILL_AREA_STYLE_COLOUR('',#" + rgb + ")");
		long fillStyle = emitter.add("FILL_AREA_STYLE('',(#" + fillColour + "))");
		long fillArea = emitter.add("SURFACE_STYLE_FILL_AREA(#" + fillStyle + ")");
		long sideStyle = emitter.add("SURFACE_SIDE_STYLE('',(#" + fillArea + "))");
		long surfaceUsage = emitter.add("SURFACE_STYLE_USAGE(.BOTH.,#" + sideStyle + ")");
		long curveFont = emitter.add("DRAUGHTING_PRE_DEFINED_CURVE_FONT('continuous')");
		long curveStyle = emitter.add("CURVE_STYLE('',#" + curveFont + ",POSITIVE_LENGTH_MEASURE(0.1),#" + rgb + ")");
		long assignment = emitter.add("PRESENTATION_STYLE_ASSIGNMENT((#" + surfaceUsage + ",#" + curveStyle + "))");

		List<Long> styled = new ArrayList<>();
		for (Long item : geometry)
			styled.add(emitter.add("STYLED_ITEM('colour',(#" + assignment + "),#" + item + ")"));

		emitter.add("MECHANICAL_DESIGN_GEOMETRIC_PRESENTATION_REPRESENTATION(''," + references(styled) + ",#" + context
				+ ")");
	}

	/** A colour channel as the real number a STEP file writes, 0 to 1. */
	private static String channel(int value) {
		return Double.toString(value / 255.0);
	}

	private static String references(List<Long> items) {
		StringBuilder references = new StringBuilder("(");
		for (int i = 0; i < items.size(); i++) {
			if (i > 0)
				references.append(',');
			references.append('#').append(items.get(i));
		}
		return references.append(')').toString();
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

	private static String file(String fileName, String data) {
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
		file.append(data);
		file.append("ENDSEC;\n");
		file.append("END-ISO-10303-21;\n");
		return file.toString();
	}

}
