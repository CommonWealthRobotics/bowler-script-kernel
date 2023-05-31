package junit.bowler;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.AudioPlayer;
import com.neuronrobotics.bowlerstudio.AudioStatus;
import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.ISpeakingProgress;
import com.neuronrobotics.bowlerstudio.lipsync.RhubarbManager;



public class TTSTest {
		
	@Test 
	public void TTSText() {
		AudioPlayer.setLambda(new RhubarbManager());
		ISpeakingProgress sp = new ISpeakingProgress() {
			
			@Override
			public void update(double percentage, AudioStatus status) {
				// TODO Auto-generated method stub
				System.out.println(percentage+" "+status.toString());
			}
		};
		//BowlerKernel.speak("Coqui one text to speech", 200, 0, 800, 1.0, 1.0,sp);
//		BowlerKernel.speak("Coqui three ", 200, 0, 802, 1.0, 1.0,null);
//		BowlerKernel.speak("Coqui two text to speech", 200, 0, 801, 1.0, 1.0,null);
//		BowlerKernel.speak("Coqui two second shot", 200, 0, 801, 1.0, 1.0,null);
		
//		for(int i=800;i<(800+CoquiDockerManager.getNummberOfOptions());i++) {
//			BowlerKernel.speak("Coqui " + i + " text to speech", 200, 0, i, 1.0, 1.0,null);
//			System.out.println("\n\nVoice finished");
//		}
	}

	
}
