package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.imageprovider.OpenCVJNILoader;

public class OpenCVLoadingTest {

  @Test
  public void test() {

    OpenCVJNILoader.load();

  }

}
