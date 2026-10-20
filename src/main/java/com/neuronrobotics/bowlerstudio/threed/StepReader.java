package com.neuronrobotics.bowlerstudio.threed;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal ISO 10303-21 (STEP part 21) reader. Parses the exchange structure into
 * numbered instances and resolves entity attributes by name, taking the single
 * inheritance chain into account so that callers never guess at positions.
 *
 * <p>
 * Only the entities registered below can have their attributes resolved; any
 * other entity is still parsed so that references and instance numbering stay
 * intact.
 */
final class StepReader {

	static final class StepException extends RuntimeException {
		private static final long serialVersionUID = 1L;

		StepException(String message) {
			super(message);
		}
	}

	static final class Instance {
		private final int id;
		private final List<String> entities;
		private final List<Value> parameters;

		private Instance(int id, List<String> entities, List<Value> parameters) {
			this.id = id;
			this.entities = entities;
			this.parameters = parameters;
		}

		int id() {
			return id;
		}

		/** Entity names; more than one for a complex instance. */
		List<String> entities() {
			return entities;
		}

		String primaryEntity() {
			return entities.get(0);
		}

		boolean is(String name) {
			for (String entity : entities)
				if (entity.equals(name))
					return true;
			return false;
		}

		Value parameter(int index) {
			return index >= 0 && index < parameters.size() ? parameters.get(index) : null;
		}
	}

	static final class Value {
		enum Kind {
			REFERENCE, STRING, ENUM, NUMBER, LIST, TYPED, OMITTED, DERIVED
		}

		private final Kind kind;
		private final int reference;
		private final double number;
		private final String text;
		private final List<Value> list;

		private Value(Kind kind, int reference, double number, String text, List<Value> list) {
			this.kind = kind;
			this.reference = reference;
			this.number = number;
			this.text = text;
			this.list = list;
		}

		static Value reference(int id) {
			return new Value(Kind.REFERENCE, id, 0, null, null);
		}

		static Value string(String text) {
			return new Value(Kind.STRING, 0, 0, text, null);
		}

		static Value enumeration(String text) {
			return new Value(Kind.ENUM, 0, 0, text, null);
		}

		static Value number(double value, String raw) {
			return new Value(Kind.NUMBER, 0, value, raw, null);
		}

		static Value list(List<Value> values) {
			return new Value(Kind.LIST, 0, 0, null, values);
		}

		static Value typed(String name, Value inner) {
			List<Value> wrapper = new ArrayList<>(1);
			wrapper.add(inner);
			return new Value(Kind.TYPED, 0, 0, name, wrapper);
		}

		static Value omitted() {
			return new Value(Kind.OMITTED, 0, 0, null, null);
		}

		static Value derived() {
			return new Value(Kind.DERIVED, 0, 0, null, null);
		}

		Kind kind() {
			return kind;
		}

		int reference() {
			if (kind != Kind.REFERENCE)
				throw new StepException("Expected a reference but found " + describe());
			return reference;
		}

		double number() {
			if (kind != Kind.NUMBER)
				throw new StepException("Expected a number but found " + describe());
			return number;
		}

		String text() {
			if (kind != Kind.STRING && kind != Kind.ENUM && kind != Kind.TYPED)
				throw new StepException("Expected text but found " + describe());
			return text;
		}

		/** Enumeration such as {@code T} for {@code .T.}. */
		String enumeration() {
			if (kind != Kind.ENUM)
				throw new StepException("Expected an enumeration but found " + describe());
			return text;
		}

		/** Name of a typed parameter such as {@code LENGTH_MEASURE(1.0)}. */
		String typeName() {
			return kind == Kind.TYPED ? text : null;
		}

		/** Payload of a typed parameter, or this value when it is not typed. */
		Value unwrap() {
			return kind == Kind.TYPED && !list.isEmpty() ? list.get(0) : this;
		}

		List<Value> list() {
			if (kind != Kind.LIST)
				throw new StepException("Expected a list but found " + describe());
			return list;
		}

		boolean isList() {
			return kind == Kind.LIST;
		}

		boolean isOmitted() {
			return kind == Kind.OMITTED || kind == Kind.DERIVED || kind == Kind.LIST && list.isEmpty();
		}

		String describe() {
			switch (kind) {
				case REFERENCE :
					return "#" + reference;
				case STRING :
					return "'" + text + "'";
				case ENUM :
					return "." + text + ".";
				case NUMBER :
					return String.valueOf(number);
				case LIST :
					return "a list of " + list.size();
				case TYPED :
					return text + "(...)";
				case OMITTED :
					return "$";
				default :
					return "*";
			}
		}
	}

	private final Map<Integer, Instance> instances;

	private StepReader(Map<Integer, Instance> instances) {
		this.instances = instances;
	}

	static StepReader parse(InputStream stream) throws IOException {
		return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
	}

	static StepReader parse(String text) {
		return new StepReader(new Parser(text).parse());
	}

	Map<Integer, Instance> instances() {
		return instances;
	}

	Instance instance(Value value) {
		if (value == null || value.kind() != Value.Kind.REFERENCE)
			throw new StepException("Expected a reference but found " + (value == null ? "nothing" : value.describe()));
		Instance instance = instances.get(value.reference());
		if (instance == null)
			throw new StepException("Reference #" + value.reference() + " has no instance in this file");
		return instance;
	}

	/** Follows a reference, or null for an omitted/absent attribute. */
	Instance optionalInstance(Value value) {
		if (value == null || value.kind() == Value.Kind.OMITTED || value.kind() == Value.Kind.DERIVED)
			return null;
		return instance(value);
	}

	/** A list attribute, unwrapping a typed parameter such as {@code LIST(1,2,3)}. */
	List<Value> list(Value value) {
		if (value == null)
			throw new StepException("Expected a list but the attribute is absent");
		return value.unwrap().list();
	}

	/** A numeric attribute, unwrapping a typed parameter such as {@code POSITIVE_LENGTH_MEASURE(5.)}. */
	double number(Value value) {
		if (value == null)
			throw new StepException("Expected a number but the attribute is absent");
		return value.unwrap().number();
	}

	int integer(Value value) {
		return (int) number(value);
	}

	boolean isTrue(Value value) {
		return value != null && value.kind() == Value.Kind.ENUM && "T".equals(value.enumeration());
	}

	/**
	 * Attribute value addressed by name. A simple instance serializes the whole
	 * inheritance chain, so positions come from the flattened list; a complex
	 * instance serializes every part's own attributes only, in part order.
	 */
	Value attribute(Instance instance, String entity, String name) {
		List<String> parts = instance.entities();

		if (parts.size() == 1) {
			String part = parts.get(0);
			if (isAssignable(part, entity)) {
				int position = flatAttributes(part).indexOf(name);
				if (position >= 0)
					return instance.parameter(position);
			}
			throw new StepException(entity + "." + name + " is not available from " + part);
		}

		int offset = 0;
		for (String part : parts) {
			List<String> own = ownAttributes(part);
			if (isAssignable(part, entity)) {
				int position = own.indexOf(name);
				if (position >= 0)
					return instance.parameter(offset + position);
			}
			offset += own.size();
		}

		throw new StepException(entity + "." + name + " is not available from " + instance.primaryEntity());
	}

	/** True when an instance is of the given entity or one of its subtypes. */
	static boolean kindOf(Instance instance, String entity) {
		for (String part : instance.entities())
			if (isAssignable(part, entity))
				return true;
		return false;
	}

	/**
	 * True when every part of an instance is registered, so its attributes can be
	 * resolved. A complex instance that carries an unregistered part cannot be
	 * read at all, since the parts serialize in order and an unknown arity makes
	 * the following parts' positions unknowable.
	 */
	static boolean supports(Instance instance) {
		for (String part : instance.entities())
			if (!OWN.containsKey(part))
				return false;
		return true;
	}

	/**
	 * Like {@link #attribute}, but null when the instance simply does not have that
	 * attribute, for asking whether an entity is of a kind rather than reading it.
	 */
	Value optionalAttribute(Instance instance, String entity, String name) {
		for (String part : instance.entities())
			if (isAssignable(part, entity) && OWN.containsKey(part) && flatContains(part, name))
				return attribute(instance, entity, name);
		return null;
	}

	private static boolean flatContains(String entity, String name) {
		return flatAttributes(entity).contains(name);
	}

	private static boolean isAssignable(String candidate, String expected) {
		String current = candidate;
		while (current != null) {
			if (current.equals(expected))
				return true;
			current = supertype(current);
		}
		return false;
	}

	/** Serialized attributes of a simple instance: supertype chain then own. */
	private static List<String> flatAttributes(String entity) {
		List<String> supertype = new ArrayList<>();
		String parent = supertype(entity);
		if (parent != null)
			supertype.addAll(flatAttributes(parent));
		supertype.addAll(ownAttributes(entity));
		return supertype;
	}

	private static List<String> ownAttributes(String entity) {
		List<String> own = OWN.get(entity);
		if (own == null)
			throw new StepException("Unsupported entity " + entity);
		return own;
	}

	private static String supertype(String entity) {
		return SUPERTYPE.get(entity);
	}

	private static final Map<String, List<String>> OWN = new HashMap<>();
	private static final Map<String, String> SUPERTYPE = new HashMap<>();

	/** Registers an entity with its direct supertype and its own attributes. */
	private static void subtype(String name, String supertype, String... attributes) {
		OWN.put(name, List.of(attributes));
		if (supertype != null)
			SUPERTYPE.put(name, supertype);
	}

	static {
		// --- representation items: name comes from representation_item ---
		subtype("REPRESENTATION_ITEM", null, "name");
		subtype("TOPOLOGICAL_REPRESENTATION_ITEM", "REPRESENTATION_ITEM");
		subtype("GEOMETRIC_REPRESENTATION_ITEM", "REPRESENTATION_ITEM");

		// --- tessellation ---
		subtype("TESSELLATED_ITEM", "GEOMETRIC_REPRESENTATION_ITEM");
		subtype("TESSELLATED_STRUCTURED_ITEM", "TESSELLATED_ITEM");
		subtype("COORDINATES_LIST", "TESSELLATED_ITEM", "npoints", "position_coords");
		subtype("TESSELLATED_SURFACE_SET", "TESSELLATED_ITEM", "coordinates", "pnmax", "normals");
		subtype("TRIANGULATED_SURFACE_SET", "TESSELLATED_SURFACE_SET", "pnindex", "triangles");
		subtype("COMPLEX_TRIANGULATED_SURFACE_SET", "TESSELLATED_SURFACE_SET", "pnindex", "normal_index",
				"triangle_strips", "triangle_fans");
		subtype("TESSELLATED_FACE", "TESSELLATED_STRUCTURED_ITEM", "coordinates", "pnmax", "normals", "geometric_link");
		subtype("TRIANGULATED_FACE", "TESSELLATED_FACE", "pnindex", "triangles");
		subtype("TESSELLATED_SOLID", "TESSELLATED_ITEM", "items", "geometric_model");
		subtype("TESSELLATED_SHELL", "TESSELLATED_ITEM", "items", "closed");

		// --- geometry ---
		subtype("POINT", "GEOMETRIC_REPRESENTATION_ITEM");
		subtype("CARTESIAN_POINT", "POINT", "coordinates");
		subtype("DIRECTION", "GEOMETRIC_REPRESENTATION_ITEM", "direction_ratios");
		subtype("VECTOR", "GEOMETRIC_REPRESENTATION_ITEM", "orientation", "magnitude");
		subtype("PLACEMENT", "GEOMETRIC_REPRESENTATION_ITEM");
		subtype("AXIS2_PLACEMENT_3D", "PLACEMENT", "location", "axis", "ref_direction");
		subtype("AXIS1_PLACEMENT", "PLACEMENT", "location", "axis");
		subtype("CURVE", "GEOMETRIC_REPRESENTATION_ITEM");
		subtype("BOUNDED_CURVE", "CURVE");
		subtype("LINE", "CURVE", "pnt", "dir");
		subtype("CONIC", "CURVE", "position");
		subtype("CIRCLE", "CONIC", "radius");
		subtype("ELLIPSE", "CONIC", "semi_axis_1", "semi_axis_2");
		subtype("POLYLINE", "BOUNDED_CURVE", "points");
		subtype("B_SPLINE_CURVE", "CURVE", "degree", "control_points_list", "curve_form", "closed_curve",
				"self_intersect");
		subtype("B_SPLINE_CURVE_WITH_KNOTS", "B_SPLINE_CURVE", "knot_multiplicities", "knots", "knot_spec");
		subtype("RATIONAL_B_SPLINE_CURVE", "B_SPLINE_CURVE", "weights_data");
		subtype("QUASI_UNIFORM_CURVE", "B_SPLINE_CURVE_WITH_KNOTS");
		subtype("UNIFORM_CURVE", "B_SPLINE_CURVE_WITH_KNOTS");
		subtype("TRIMMED_CURVE", "BOUNDED_CURVE", "basis_curve", "trim_1", "trim_2", "sense_agreement",
				"master_representation");
		// a curve lying on a surface: the 3D curve is the one that matters here
		subtype("SURFACE_CURVE", "CURVE", "curve_3d", "associated_geometry", "master_representation");
		subtype("SEAM_CURVE", "SURFACE_CURVE");
		subtype("INTERSECTION_CURVE", "SURFACE_CURVE");
		subtype("BOUNDED_SURFACE_CURVE", "SURFACE_CURVE");
		subtype("SURFACE", "GEOMETRIC_REPRESENTATION_ITEM");
		subtype("ELEMENTARY_SURFACE", "SURFACE", "position");
		subtype("PLANE", "ELEMENTARY_SURFACE");
		subtype("CYLINDRICAL_SURFACE", "ELEMENTARY_SURFACE", "radius");
		subtype("SPHERICAL_SURFACE", "ELEMENTARY_SURFACE", "radius");
		subtype("CONICAL_SURFACE", "ELEMENTARY_SURFACE", "radius", "semi_angle");
		subtype("TOROIDAL_SURFACE", "ELEMENTARY_SURFACE", "major_radius", "minor_radius");
		subtype("B_SPLINE_SURFACE", "SURFACE", "u_degree", "v_degree", "control_points_list", "surface_form",
				"u_closed", "v_closed", "self_intersect");
		subtype("B_SPLINE_SURFACE_WITH_KNOTS", "B_SPLINE_SURFACE", "u_multiplicities", "v_multiplicities", "u_knots",
				"v_knots", "knot_spec");
		subtype("RATIONAL_B_SPLINE_SURFACE", "B_SPLINE_SURFACE", "weights_data");
		subtype("GEOMETRIC_SET", "GEOMETRIC_REPRESENTATION_ITEM", "elements");
		subtype("GEOMETRIC_CURVE_SET", "GEOMETRIC_SET");

		// --- topology ---
		subtype("VERTEX", "TOPOLOGICAL_REPRESENTATION_ITEM");
		subtype("VERTEX_POINT", "VERTEX", "vertex_geometry");
		subtype("EDGE", "TOPOLOGICAL_REPRESENTATION_ITEM");
		subtype("EDGE_CURVE", "EDGE", "edge_start", "edge_end", "edge_geometry", "same_sense");
		subtype("ORIENTED_EDGE", "EDGE", "edge_start", "edge_end", "edge_element", "orientation");
		subtype("LOOP", "TOPOLOGICAL_REPRESENTATION_ITEM");
		subtype("EDGE_LOOP", "LOOP", "edge_list");
		subtype("POLY_LOOP", "LOOP", "polygon");
		subtype("VERTEX_LOOP", "LOOP", "loop_vertex");
		subtype("FACE_BOUND", "TOPOLOGICAL_REPRESENTATION_ITEM", "bound", "orientation");
		subtype("FACE_OUTER_BOUND", "FACE_BOUND");
		subtype("FACE", "TOPOLOGICAL_REPRESENTATION_ITEM");
		subtype("FACE_SURFACE", "FACE", "bounds", "face_geometry", "same_sense");
		subtype("ADVANCED_FACE", "FACE_SURFACE");
		subtype("CONNECTED_FACE_SET", "TOPOLOGICAL_REPRESENTATION_ITEM", "cfs_faces");
		subtype("CLOSED_SHELL", "CONNECTED_FACE_SET");
		subtype("OPEN_SHELL", "CONNECTED_FACE_SET");
		subtype("SOLID_MODEL", "GEOMETRIC_REPRESENTATION_ITEM");
		subtype("MANIFOLD_SOLID_BREP", "SOLID_MODEL", "outer");
		subtype("FACETED_BREP", "MANIFOLD_SOLID_BREP");
		subtype("BREP_WITH_VOIDS", "MANIFOLD_SOLID_BREP", "voids");
		subtype("SHELL_BASED_SURFACE_MODEL", "GEOMETRIC_REPRESENTATION_ITEM", "sbsm_boundary");

		// --- representations ---
		subtype("PROPERTY_DEFINITION_REPRESENTATION", null, "definition", "used_representation");
		// name is derived, so it is never serialized and must not be registered
		subtype("SHAPE_DEFINITION_REPRESENTATION", "PROPERTY_DEFINITION_REPRESENTATION");
		subtype("REPRESENTATION", null, "name", "items", "context_of_items");
		subtype("SHAPE_REPRESENTATION", "REPRESENTATION");
		subtype("ADVANCED_BREP_SHAPE_REPRESENTATION", "SHAPE_REPRESENTATION");
		subtype("MANIFOLD_SURFACE_SHAPE_REPRESENTATION", "SHAPE_REPRESENTATION");
		subtype("GEOMETRICALLY_BOUNDED_SURFACE_SHAPE_REPRESENTATION", "SHAPE_REPRESENTATION");
		subtype("GEOMETRICALLY_BOUNDED_WIREFRAME_SHAPE_REPRESENTATION", "SHAPE_REPRESENTATION");
		subtype("TESSELLATED_SHAPE_REPRESENTATION", "SHAPE_REPRESENTATION");

		// --- units and context ---
		// every part of the complex unit/context instances must be registered, since
		// resolving one attribute walks the parts that precede it
		subtype("NAMED_UNIT", null, "dimensions");
		subtype("LENGTH_UNIT", "NAMED_UNIT");
		subtype("PLANE_ANGLE_UNIT", "NAMED_UNIT");
		subtype("SOLID_ANGLE_UNIT", "NAMED_UNIT");
		subtype("SI_UNIT", "NAMED_UNIT", "prefix", "name");
		subtype("MEASURE_WITH_UNIT", null, "value_component", "unit_component");
		subtype("LENGTH_MEASURE_WITH_UNIT", "MEASURE_WITH_UNIT");
		subtype("PLANE_ANGLE_MEASURE_WITH_UNIT", "MEASURE_WITH_UNIT");
		subtype("CONVERSION_BASED_UNIT", "NAMED_UNIT", "name", "conversion_factor");
		subtype("REPRESENTATION_CONTEXT", null, "context_identifier", "context_type");
		subtype("GEOMETRIC_REPRESENTATION_CONTEXT", "REPRESENTATION_CONTEXT", "dimensions");
		subtype("GLOBAL_UNCERTAINTY_ASSIGNED_CONTEXT", null, "uncertainty");
		subtype("GLOBAL_UNIT_ASSIGNED_CONTEXT", null, "units");

		// --- product structure: what turns several shapes into an assembly ---
		subtype("PRODUCT_DEFINITION_SHAPE", null, "name", "description", "definition");
		subtype("PRODUCT_DEFINITION_RELATIONSHIP", null, "id", "name", "description", "relating_product_definition",
				"related_product_definition");
		subtype("NEXT_ASSEMBLY_USAGE_OCCURRENCE", "PRODUCT_DEFINITION_RELATIONSHIP", "reference_designator");
		subtype("REPRESENTATION_RELATIONSHIP", null, "name", "description", "rep_1", "rep_2");
		subtype("SHAPE_REPRESENTATION_RELATIONSHIP", "REPRESENTATION_RELATIONSHIP");
		subtype("REPRESENTATION_RELATIONSHIP_WITH_TRANSFORMATION", "REPRESENTATION_RELATIONSHIP",
				"transformation_operator");
		subtype("ITEM_DEFINED_TRANSFORMATION", null, "name", "description", "transform_item_1", "transform_item_2");
		subtype("CONTEXT_DEPENDENT_SHAPE_REPRESENTATION", null, "representation_relation",
				"represented_product_relation");

		// --- presentation: colours attached to geometry ---
		subtype("STYLED_ITEM", "REPRESENTATION_ITEM", "styles", "item");
		subtype("PRESENTATION_STYLE_ASSIGNMENT", null, "styles");
		subtype("SURFACE_STYLE_USAGE", null, "side", "style");
		subtype("SURFACE_SIDE_STYLE", null, "name", "styles");
		subtype("SURFACE_STYLE_FILL_AREA", null, "fill_area");
		subtype("FILL_AREA_STYLE", null, "name", "fill_styles");
		subtype("FILL_AREA_STYLE_COLOUR", null, "name", "fill_colour");
		subtype("COLOUR_RGB", null, "name", "red", "green", "blue");
		subtype("DRAUGHTING_PRE_DEFINED_COLOUR", "REPRESENTATION_ITEM");
		subtype("CURVE_STYLE", null, "name", "curve_font", "curve_width", "curve_colour");
	}

	// --- tokenizer ---

	private static final class Token {
		private final String kind;
		private final String text;
		private final int line;

		private Token(String kind, String text, int line) {
			this.kind = kind;
			this.text = text;
			this.line = line;
		}
	}

	private static final class Lexer {
		private final String text;
		private final List<Token> tokens = new ArrayList<>();
		private int index;
		private int line = 1;

		private Lexer(String text) {
			this.text = text;
		}

		private List<Token> lex() {
			while (index < text.length()) {
				char c = text.charAt(index);

				if (c == '\n') {
					line++;
					index++;
				} else if (c == ' ' || c == '\t' || c == '\r' || c == '\f') {
					index++;
				} else if (c == '/' && index + 1 < text.length() && text.charAt(index + 1) == '*') {
					skipComment();
				} else if (c == '\'') {
					readString();
				} else if (c == '#') {
					readReference();
				} else if (c == '.' && index + 1 < text.length() && Character.isDigit(text.charAt(index + 1))) {
					readNumber();
				} else if (c == '.') {
					readEnumeration();
				} else if (Character.isDigit(c) || c == '-' || c == '+') {
					readNumber();
				} else if (Character.isLetter(c) || c == '_') {
					readKeyword();
				} else {
					tokens.add(new Token("punct", String.valueOf(c), line));
					index++;
				}
			}
			return tokens;
		}

		private void skipComment() {
			int end = text.indexOf("*/", index + 2);
			if (end < 0)
				throw new StepException("Unterminated comment starting on line " + line);
			for (int i = index; i < end; i++)
				if (text.charAt(i) == '\n')
					line++;
			index = end + 2;
		}

		private void readString() {
			int start = line;
			StringBuilder value = new StringBuilder();
			index++;
			while (true) {
				if (index >= text.length())
					throw new StepException("Unterminated string starting on line " + start);
				char c = text.charAt(index);
				if (c == '\'') {
					if (index + 1 < text.length() && text.charAt(index + 1) == '\'') {
						value.append('\'');
						index += 2;
						continue;
					}
					index++;
					break;
				}
				if (c == '\n')
					line++;
				value.append(c);
				index++;
			}
			tokens.add(new Token("string", value.toString(), start));
		}

		private void readReference() {
			int start = index + 1;
			int end = start;
			while (end < text.length() && Character.isDigit(text.charAt(end)))
				end++;
			if (end == start)
				throw new StepException("Bare '#' on line " + line);
			tokens.add(new Token("reference", text.substring(start, end), line));
			index = end;
		}

		private void readEnumeration() {
			int end = text.indexOf('.', index + 1);
			if (end < 0)
				throw new StepException("Unterminated enumeration on line " + line);
			tokens.add(new Token("enum", text.substring(index + 1, end), line));
			index = end + 1;
		}

		private void readNumber() {
			int start = index;
			index++;
			while (index < text.length() && "0123456789.eE+-".indexOf(text.charAt(index)) >= 0)
				index++;
			tokens.add(new Token("number", text.substring(start, index), line));
		}

		private void readKeyword() {
			int start = index;
			while (index < text.length()) {
				char c = text.charAt(index);
				if (Character.isLetterOrDigit(c) || c == '_' || c == '-')
					index++;
				else
					break;
			}
			tokens.add(new Token("keyword", text.substring(start, index).toUpperCase(), line));
		}
	}

	// --- parser ---

	private static final class Parser {
		private final List<Token> tokens;
		private int index;

		private Parser(String text) {
			this.tokens = new Lexer(text).lex();
		}

		private Map<Integer, Instance> parse() {
			Map<Integer, Instance> instances = new LinkedHashMap<>();

			expectKeyword("ISO-10303-21");
			expectPunctuation(";");
			expectKeyword("HEADER");
			expectPunctuation(";");
			while (!peekIsKeyword("ENDSEC")) {
				skipTyped();
				expectPunctuation(";");
			}
			expectKeyword("ENDSEC");
			expectPunctuation(";");
			expectKeyword("DATA");
			expectPunctuation(";");

			while (!peekIsKeyword("ENDSEC")) {
				Token id = expect("reference");
				expectPunctuation("=");

				List<String> entities = new ArrayList<>();
				List<Value> parameters = new ArrayList<>();

				if (isPunctuation("(")) {
					// complex instance: parts are listed, one parameter list overall
					next();
					while (!isPunctuation(")"))
						readPart(entities, parameters);
					expectPunctuation(")");
				} else {
					readPart(entities, parameters);
				}
				expectPunctuation(";");

				int number = Integer.parseInt(id.text);
				if (instances.put(number, new Instance(number, entities, parameters)) != null)
					throw new StepException("Duplicate instance #" + number + " on line " + id.line);
			}

			expectKeyword("ENDSEC");
			expectPunctuation(";");
			expectKeyword("END-ISO-10303-21");
			expectPunctuation(";");
			return instances;
		}

		/** ENTITY_NAME '(' parameter, parameter ... ')' */
		private void readPart(List<String> entities, List<Value> parameters) {
			Token name = expect("keyword");
			entities.add(name.text);
			expectPunctuation("(");
			if (!isPunctuation(")")) {
				parameters.add(parseValue());
				while (isPunctuation(",")) {
					next();
					parameters.add(parseValue());
				}
			}
			expectPunctuation(")");
		}

		/** Consumes a typed instance without keeping it, as the header needs. */
		private void skipTyped() {
			expect("keyword");
			expectPunctuation("(");
			int depth = 1;
			while (depth > 0) {
				Token token = peek();
				if ("punct".equals(token.kind) && "(".equals(token.text))
					depth++;
				else if ("punct".equals(token.kind) && ")".equals(token.text))
					depth--;
				next();
			}
		}

		private Value parseValue() {
			Token token = peek();

			switch (token.kind) {
				case "reference" :
					next();
					return Value.reference(Integer.parseInt(token.text));
				case "string" :
					next();
					return Value.string(token.text);
				case "enum" :
					next();
					return Value.enumeration(token.text);
				case "number" :
					next();
					return Value.number(Double.parseDouble(token.text), token.text);
				case "keyword" :
					next();
					expectPunctuation("(");
					Value inner = parseValue();
					expectPunctuation(")");
					return Value.typed(token.text, inner);
				case "punct" :
					if ("(".equals(token.text))
						return parseList();
					if ("$".equals(token.text)) {
						next();
						return Value.omitted();
					}
					if ("*".equals(token.text)) {
						next();
						return Value.derived();
					}
					throw new StepException("Unexpected '" + token.text + "' on line " + token.line);
				default :
					throw new StepException("Unexpected token on line " + token.line);
			}
		}

		private Value parseList() {
			expectPunctuation("(");
			List<Value> values = new ArrayList<>();
			if (!isPunctuation(")")) {
				values.add(parseValue());
				while (isPunctuation(",")) {
					next();
					values.add(parseValue());
				}
			}
			expectPunctuation(")");
			return Value.list(values);
		}

		private Token peek() {
			if (index >= tokens.size())
				throw new StepException("Unexpected end of file");
			return tokens.get(index);
		}

		private Token next() {
			return tokens.get(index++);
		}

		private boolean isPunctuation(String text) {
			Token token = index < tokens.size() ? tokens.get(index) : null;
			return token != null && "punct".equals(token.kind) && text.equals(token.text);
		}

		private boolean peekIsKeyword(String text) {
			Token token = index < tokens.size() ? tokens.get(index) : null;
			return token != null && "keyword".equals(token.kind) && text.equals(token.text);
		}

		private Token expect(String kind) {
			Token token = peek();
			if (!kind.equals(token.kind))
				throw new StepException("Expected " + kind + " but found '" + token.text + "' on line " + token.line);
			return next();
		}

		private void expectKeyword(String text) {
			Token token = peek();
			if (!"keyword".equals(token.kind) || !text.equals(token.text))
				throw new StepException("Expected " + text + " but found '" + token.text + "' on line " + token.line);
			next();
		}

		private void expectPunctuation(String text) {
			Token token = peek();
			if (!"punct".equals(token.kind) || !text.equals(token.text))
				throw new StepException("Expected '" + text + "' but found '" + token.text + "' on line " + token.line);
			next();
		}
	}
}
