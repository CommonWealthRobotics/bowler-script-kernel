package com.neuronrobotics.bowlerstudio.threed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.neuronrobotics.bowlerstudio.threed.StepReader.Instance;
import com.neuronrobotics.bowlerstudio.threed.StepReader.Value;

import javafx.scene.paint.Color;

/**
 * Reads the colours a STEP file assigns to its geometry. A {@code STYLED_ITEM}
 * names the geometric item it colours and the presentation styles that carry the
 * colour, which is a {@code COLOUR_RGB} or a named
 * {@code DRAUGHTING_PRE_DEFINED_COLOUR}. Colours are keyed by the id of the item
 * they apply to, so the geometry reader can look one up for whatever it read.
 */
final class StepStyles {

	private StepStyles() {
	}

	/** Colour per styled geometric item, by the item's instance id. */
	static Map<Integer, Color> byItem(StepReader reader) {
		Map<Integer, Color> colours = new HashMap<>();
		for (Instance instance : reader.instances().values()) {
			if (!instance.is("STYLED_ITEM"))
				continue;

			// a styled item is only about geometry when it is a plain styled item; a
			// complex one is an annotation that merely mentions the relation, so its
			// styles belong to the annotation rather than to the geometry
			if (instance.entities().size() != 1)
				continue;

			Color colour = colourOfStyledItem(reader, instance);
			if (colour == null)
				continue;

			Instance item = reader.optionalInstance(reader.optionalAttribute(instance, "STYLED_ITEM", "item"));
			if (item != null)
				colours.put(item.id(), colour);
		}
		return colours;
	}

	private static Color colourOfStyledItem(StepReader reader, Instance styled) {
		for (Value style : list(reader, styled, "STYLED_ITEM", "styles")) {
			Color colour = colourOfStyle(reader, reader.optionalInstance(style));
			if (colour != null)
				return colour;
		}
		return null;
	}

	/**
	 * The colour a presentation style carries. A style nests its colour several
	 * layers down, either as a fill area under a surface style or as the colour a
	 * curve style names directly, so walk the style tree rather than hard coding
	 * one branch.
	 */
	private static Color colourOfStyle(StepReader reader, Instance style) {
		if (style == null || !StepReader.supports(style))
			return null;

		if (style.is("COLOUR_RGB")) {
			double red = reader.number(reader.attribute(style, "COLOUR_RGB", "red"));
			double green = reader.number(reader.attribute(style, "COLOUR_RGB", "green"));
			double blue = reader.number(reader.attribute(style, "COLOUR_RGB", "blue"));
			return Color.color(clamp(red), clamp(green), clamp(blue));
		}

		if (style.is("DRAUGHTING_PRE_DEFINED_COLOUR"))
			return named(reader.attribute(style, "DRAUGHTING_PRE_DEFINED_COLOUR", "name"));

		for (Instance nested : nested(reader, style)) {
			Color colour = colourOfStyle(reader, nested);
			if (colour != null)
				return colour;
		}
		return null;
	}

	/**
	 * The presentation styles one instance refers to, for descending the style
	 * tree. Some of these attributes hold a list of styles and some a single
	 * style, so a lone reference is treated as a list of one.
	 */
	private static List<Instance> nested(StepReader reader, Instance style) {
		List<Instance> nested = new ArrayList<>();
		if (style.is("PRESENTATION_STYLE_ASSIGNMENT"))
			add(reader, nested, style, "PRESENTATION_STYLE_ASSIGNMENT", "styles", true);
		else if (style.is("SURFACE_STYLE_USAGE"))
			add(reader, nested, style, "SURFACE_STYLE_USAGE", "style", false);
		else if (style.is("SURFACE_SIDE_STYLE"))
			add(reader, nested, style, "SURFACE_SIDE_STYLE", "styles", true);
		else if (style.is("SURFACE_STYLE_FILL_AREA"))
			add(reader, nested, style, "SURFACE_STYLE_FILL_AREA", "fill_area", false);
		else if (style.is("FILL_AREA_STYLE"))
			add(reader, nested, style, "FILL_AREA_STYLE", "fill_styles", true);
		else if (style.is("FILL_AREA_STYLE_COLOUR"))
			add(reader, nested, style, "FILL_AREA_STYLE_COLOUR", "fill_colour", false);
		else if (style.is("CURVE_STYLE"))
			add(reader, nested, style, "CURVE_STYLE", "curve_colour", false);
		return nested;
	}

	private static void add(StepReader reader, List<Instance> nested, Instance instance, String entity, String name,
			boolean asList) {
		Value value = reader.optionalAttribute(instance, entity, name);
		for (Value style : asList ? list(reader, instance, entity, name) : values(value)) {
			Instance resolved = reader.optionalInstance(style);
			if (resolved != null)
				nested.add(resolved);
		}
	}

	/** A list attribute, or a single value as a list of one. */
	private static List<Value> list(StepReader reader, Instance instance, String entity, String name) {
		Value value = reader.optionalAttribute(instance, entity, name);
		if (value == null)
			return List.of();
		if (value.isList())
			return value.list();
		List<Value> single = new ArrayList<>(1);
		single.add(value);
		return single;
	}

	private static List<Value> values(Value value) {
		return value == null ? List.of() : List.of(value);
	}

	/** The predefined colours of ISO 10303-46, which are also CSS colour names. */
	private static Color named(Value value) {
		if (value == null || value.kind() != Value.Kind.STRING)
			return null;

		String name = value.text().toLowerCase();
		switch (name) {
			case "red" :
				return Color.RED;
			case "green" :
				return Color.GREEN;
			case "blue" :
				return Color.BLUE;
			case "yellow" :
				return Color.YELLOW;
			case "magenta" :
				return Color.MAGENTA;
			case "cyan" :
				return Color.CYAN;
			case "black" :
				return Color.BLACK;
			case "white" :
				return Color.WHITE;
			default :
				return null;
		}
	}

	private static double clamp(double channel) {
		return Math.max(0, Math.min(1, channel));
	}
}
