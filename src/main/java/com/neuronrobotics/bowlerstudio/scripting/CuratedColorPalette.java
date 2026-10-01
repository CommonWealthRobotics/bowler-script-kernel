package com.neuronrobotics.bowlerstudio.scripting;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.scene.paint.Color;

public final class CuratedColorPalette {

	private static final String DEFAULT_PALETTE_RESOURCE = "default-palette.txt";

	private static final List<List<Color>> DEFAULT_ROWS = loadPalette(DEFAULT_PALETTE_RESOURCE);
	private static final List<Color> DEFAULT_COLORS = flatten(DEFAULT_ROWS);

	private CuratedColorPalette() {
	}

	public static List<List<Color>> getRows() {
		return DEFAULT_ROWS;
	}

	public static List<Color> getColors() {
		return DEFAULT_COLORS;
	}

	public static Color nearest(Color input) {
		if (input == null || DEFAULT_COLORS.isEmpty()) {
			return input;
		}

		Color nearest = DEFAULT_COLORS.get(0);
		double nearestDistance = colorDistanceSquared(input, nearest);

		for (int i = 1; i < DEFAULT_COLORS.size(); i++) {
			Color candidate = DEFAULT_COLORS.get(i);
			double distance = colorDistanceSquared(input, candidate);

			if (distance < nearestDistance) {
				nearest = candidate;
				nearestDistance = distance;
			}
		}

		return nearest;
	}

	private static double colorDistanceSquared(Color a, Color b) {
		double red = a.getRed() - b.getRed();
		double green = a.getGreen() - b.getGreen();
		double blue = a.getBlue() - b.getBlue();

		return red * red + green * green + blue * blue;
	}

	private static List<List<Color>> loadPalette(String resourceName) {
		InputStream stream = CuratedColorPalette.class.getResourceAsStream(resourceName);

		if (stream == null) {
			throw new IllegalStateException("Palette resource not found: " + resourceName);
		}

		List<List<Color>> rows = new ArrayList<>();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {

			String line;

			while ((line = reader.readLine()) != null) {
				line = line.trim();

				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}

				String[] values = line.split(",");
				List<Color> row = new ArrayList<>();

				for (String value : values) {
					String hex = value.trim();

					if (!hex.startsWith("#")) {
						hex = "#" + hex;
					}

					row.add(Color.web(hex));
				}

				rows.add(Collections.unmodifiableList(row));
			}

		} catch (IOException e) {
			throw new RuntimeException("Failed to load palette: " + resourceName, e);
		}

		if (rows.isEmpty()) {
			throw new IllegalStateException("Palette is empty: " + resourceName);
		}

		return Collections.unmodifiableList(rows);
	}

	private static List<Color> flatten(List<List<Color>> rows) {
		List<Color> colors = new ArrayList<>();

		for (List<Color> row : rows) {
			colors.addAll(row);
		}

		return Collections.unmodifiableList(colors);
	}
}
