package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.util.GeometrySimplification;

public class InksacpeCLI {

	@Test
	public void test() {
		GeometrySimplification.simplifySVG(new File("Test.SVG"));
	}

}
