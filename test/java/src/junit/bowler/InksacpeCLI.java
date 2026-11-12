package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.util.GeometrySimplification;
import com.neuronrobotics.video.OSUtil;

public class InksacpeCLI {

	@Test
	public void test() throws Exception {
		try {
			GeometrySimplification.simplifySVG(new File("Test.SVG"));
		} catch (Exception ex) {
			if (OSUtil.isWindows())
				ex.printStackTrace();
			else
				throw ex;
		}
	}

}
