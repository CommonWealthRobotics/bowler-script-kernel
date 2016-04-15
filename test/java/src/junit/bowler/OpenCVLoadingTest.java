package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.imageprovider.OpenCVJNILoader;

public class OpenCVLoadingTest {

	@Test
	public void test() {
		try{
			OpenCVJNILoader.load();
		}catch(Exception e){
			fail(e.getMessage());
		}catch(Error e){
			fail(e.getMessage());
		}
	}

}
