package com.neuronrobotics.bowlerstudio.scripting;

import java.io.PrintStream;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ICSGProgress;

/** Relays explicit progress records from the bundled FreeCAD exporter. */
final class FreecadProgressStream extends PrintStream {
	private static final String PREFIX = "CADOODLE_PROGRESS\t";
	private final ICSGProgress progress;

	FreecadProgressStream(PrintStream output) {
		this(output, CSG.getProgressMoniter());
	}

	FreecadProgressStream(PrintStream output, ICSGProgress progress) {
		super(output, true);
		this.progress = progress;
	}

	@Override
	public void println(String line) {
		super.println(line);
		if (line == null || !line.startsWith(PREFIX))
			return;
		String[] fields = line.substring(PREFIX.length()).split("\t", 3);
		if (fields.length != 3)
			throw new IllegalArgumentException("Invalid FreeCAD progress record: " + line);
		int index = Integer.parseInt(fields[0]);
		int total = Integer.parseInt(fields[1]);
		if (total <= 0 || index < 0 || index >= total)
			throw new IllegalArgumentException("Invalid FreeCAD progress counts: " + line);
		progress.progressUpdate(index, total, fields[2], null);
	}
}
