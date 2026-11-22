package com.neuronrobotics.bowlerstudio.scripting;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class FreecadProgressStreamTest {
	@Test
	public void relaysExplicitRecordsAndPreservesDiagnosticOutput() {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		List<Integer> indexes = new ArrayList<>();
		List<Integer> totals = new ArrayList<>();
		List<String> messages = new ArrayList<>();
		FreecadProgressStream stream = new FreecadProgressStream(new PrintStream(output),
				(index, total, message, shape) -> {
					indexes.add(index);
					totals.add(total);
					messages.add(message);
				});
		stream.println("FreeCAD diagnostic");
		stream.println("CADOODLE_PROGRESS\t0\t3\tMeshing solid");
		stream.println("CADOODLE_PROGRESS\t2\t3\tArchive written");
		assertEquals(java.util.Arrays.asList(0, 2), indexes);
		assertEquals(java.util.Arrays.asList(3, 3), totals);
		assertEquals(2, messages.size());
		assertTrue(output.toString().contains("FreeCAD diagnostic"));
		assertTrue(output.toString().contains("Archive written"));
	}

	@Test
	public void rejectsInvalidProtocolCountsWithoutReportingProgress() {
		for (String record : new String[]{"0\t0\tstage", "-1\t2\tstage", "2\t2\tstage", "bad\t2\tstage", "0\t2"}) {
			FreecadProgressStream stream = new FreecadProgressStream(new PrintStream(new ByteArrayOutputStream()),
					(index, total, message, shape) -> fail("invalid progress was forwarded"));
			try {
				stream.println("CADOODLE_PROGRESS\t" + record);
				fail("invalid record was accepted");
			} catch (IllegalArgumentException expected) {
			}
		}
	}
}
