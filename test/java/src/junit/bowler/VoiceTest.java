package junit.bowler;

import static org.junit.Assert.*;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;

public class VoiceTest {

  @Test
  public void test() {
	  BowlerKernel.speak(
				"This is the default voice",
				300, //rate 
				120, //pitch
				41,  //range
				1,   //shift
				1 	//volume
			);
			BowlerKernel.speak(
				"we are the borg",
				300, //rate 
				120, //pitch
				41,  //range
				2,   //shift
				0.01 	//volume
			);
			BowlerKernel.speak(
				"can make",
				10, //rate 
				220, //pitch
				81,  //range
				0.75,   //shift
				1 	//volume
			);
//
//			BowlerKernel.speak(
//				"Lots of",
//				125, //rate 
//				120, //pitch
//				41,  //range
//				1,   //shift
//				1 	//volume
//			);
//
//			BowlerKernel.speak(
//				"differents kinds",
//				175, //rate 
//				220, //pitch
//				41,  //range
//				2,   //shift
//				1 	//volume
//			);
//
//			BowlerKernel.speak(
//				"of voices",
//				175, //rate 
//				120, //pitch
//				41,  //range
//				0.05,   //shift
//				1 	//volume
//			);
  }

}
