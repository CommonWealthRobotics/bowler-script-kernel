package junit.bowler;

import org.junit.Test;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.Vector3d;

public class StepRoundTripAssertionsTest {
	@Test(expected = AssertionError.class)
	public void changedMinimumZMustFailEvenWhenAllOtherBoundsMatch() {
		Bounds expected = new Bounds(new Vector3d(0, 0, 0), new Vector3d(1, 1, 1));
		Bounds actual = new Bounds(new Vector3d(0, 0, 0.1), new Vector3d(1, 1, 1));
		StepRoundTripAssertions.bounds(expected, actual);
	}

	@Test(expected = AssertionError.class)
	public void lostVolumeMustFail() {
		StepRoundTripAssertions.volume(100, 90);
	}
}
