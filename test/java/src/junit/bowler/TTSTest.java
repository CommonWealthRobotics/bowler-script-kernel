package junit.bowler;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.imageio.ImageIO;

import org.junit.Test;

import com.neuronrobotics.bowlerkernel.djl.ImagePredictorType;
import com.neuronrobotics.bowlerkernel.djl.PredictorFactory;
import com.neuronrobotics.bowlerstudio.BowlerKernel;

import ai.djl.Application;
import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.modality.cv.output.DetectedObjects;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;



public class TTSTest {
		
	@Test 
	public void TTSText() {
//		BowlerKernel.speak("Coqui one text to speech", 200, 0, 800, 1.0, 1.0,null);
//		BowlerKernel.speak("Coqui three ", 200, 0, 802, 1.0, 1.0,null);
//		BowlerKernel.speak("Coqui two text to speech", 200, 0, 801, 1.0, 1.0,null);
//		BowlerKernel.speak("Coqui two second shot", 200, 0, 801, 1.0, 1.0,null);
		
		BowlerKernel.speak("Coqui 803 text to speech", 200, 0, 803, 1.0, 1.0,null);
		BowlerKernel.speak("Coqui 804 text to speech", 200, 0, 804, 1.0, 1.0,null);
		BowlerKernel.speak("Coqui 805 text to speech", 200, 0, 805, 1.0, 1.0,null);
//		
	}

	
}
