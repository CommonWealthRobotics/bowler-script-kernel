package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;

public class VoiceTest {

  @Test
  public void test() {
	  BowlerKernel.speak(
				"This is the default voice");
	  BowlerKernel.speak("Fast talking", 300, 0, 300, 1.0, 1.0);
	  BowlerKernel.speak("Slow talking", 10, 0, 300, 1.0, 1.0);
	  BowlerKernel.speak("Voice one", 100, 0, 300, 1.0, 1.0);
	  BowlerKernel.speak("Voice two", 100, 0, 200, 1.0, 1.0);
	  BowlerKernel.speak("Voice three", 100, 0, 100, 1.0, 1.0);
	  BowlerKernel.speak("Voice four", 100, 0, 50, 1.0, 1.0);
			BowlerKernel.speak(
				"we are the borg",
				100, //rate 
				0, //slur
				300,  //voice
				1,   //echo
				0.4 	//volume
			);
			BowlerKernel.speak(
				"can make Lots of",
				300, //rate 
				0, //slur
				300,  //voice
				0.5,   //echo
				1 	//volume
			);
////
			BowlerKernel.speak(
					"robot voice of starscream",
					100, //rate 
					90, //slur
					200,  //voice
					1,   //echo
					1 	//volume
			);

  }

}
