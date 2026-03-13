package junit.bowler;

import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.AudioPlayer;
import com.neuronrobotics.bowlerstudio.AudioStatus;
import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.ISpeakingProgress;
import com.neuronrobotics.bowlerstudio.lipsync.RhubarbManager;

public class TTSTest {
	@Before
	public void setup() throws InvalidRemoteException, TransportException, IOException, GitAPIException, Exception {
		BowlerKernel.startupProcedures();
	}
	@Test
	@Ignore
	public void TTSText() {
		AudioPlayer.setLambda(new RhubarbManager());
		ISpeakingProgress sp = new ISpeakingProgress() {

			@Override
			public void update(double percentage, AudioStatus status) {
				// Auto-generated method stub
				com.neuronrobotics.sdk.common.Log.error(percentage + " " + status.toString());
			}
		};
		BowlerKernel.speak("Coqui one text to speech", 200, 0, 800, 1.0, 1.0, sp);
		AudioPlayer.setLambda(com.neuronrobotics.bowlerstudio.lipsync.VoskLipSync.get());
		BowlerKernel.speak("Coqui one text to speech", 200, 0, 800, 1.0, 1.0, sp);

		// BowlerKernel.speak("Coqui three ", 200, 0, 802, 1.0, 1.0,null);
		// BowlerKernel.speak("Coqui two text to speech", 200, 0, 801, 1.0, 1.0,null);
		// BowlerKernel.speak("Coqui two second shot", 200, 0, 801, 1.0, 1.0,null);

		// for(int i=800;i<(800+CoquiDockerManager.getNummberOfOptions());i++) {
		// BowlerKernel.speak("Coqui " + i + " text to speech", 200, 0, i, 1.0,
		// 1.0,null);
		// com.neuronrobotics.sdk.common.Log.error("\n\nVoice finished");
		// }
	}

}
