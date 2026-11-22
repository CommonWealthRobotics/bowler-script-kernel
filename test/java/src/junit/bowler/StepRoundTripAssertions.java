package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;

final class StepRoundTripAssertions {
	private StepRoundTripAssertions() {
	}

	static void bounds(Bounds expected, Bounds actual) {
		assertEquals("minimum x", expected.getMin().x, actual.getMin().x, 1e-5);
		assertEquals("minimum y", expected.getMin().y, actual.getMin().y, 1e-5);
		assertEquals("minimum z", expected.getMin().z, actual.getMin().z, 1e-5);
		assertEquals("maximum x", expected.getMax().x, actual.getMax().x, 1e-5);
		assertEquals("maximum y", expected.getMax().y, actual.getMax().y, 1e-5);
		assertEquals("maximum z", expected.getMax().z, actual.getMax().z, 1e-5);
	}

	static double volume(List<CSG> parts) {
		assertTrue("3MF contains no objects", !parts.isEmpty());
		double volume = 0;
		for (CSG part : parts) {
			assertTrue("mesh is empty", part.getTriCount() > 0);
			assertTrue("native volume must be positive and finite",
					Double.isFinite(part.getVolume()) && part.getVolume() > 0);
			volume += part.getVolume();
		}
		return volume;
	}

	static void volume(double expected, double actual) {
		// 3MF stores single-precision coordinates; allow their accumulated rounding
		assertEquals("round-trip volume", expected, actual, Math.max(1e-5, expected * 1e-6));
	}
}
