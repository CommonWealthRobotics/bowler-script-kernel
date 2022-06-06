package com.neuronrobotics.sdk.dyio.sequencer;
import java.io.File;
import com.neuronrobotics.sdk.util.ThreadUtil;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
/**
 * The Class SequencerWAV.
 */
public class SequencerWAV {
    
    /** The fn. */
    private String fn="";
    
    /** The player. */
    // constructor that takes the name of an MP3 file
    private Clip player;
    
    /** The track length. */
    private int trackLength = 37;

    public SequencerWAV(String filename) {
    	fn = filename;
        try {
        	AudioInputStream audioStream = AudioSystem.getAudioInputStream(new File(fn));

        	player = AudioSystem.getClip();

        	trackLength = (int) (((double)player.getMicrosecondLength())/1000.0);

        }
        catch (Exception e) {
            System.out.println("Problem playing file " + filename+"\r\n");
            //e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Pause.
     */
    public void pause(){
    	player.stop();
    }

    /**
     * Close.
     */
    public void close() { 
    	if (player != null) 
    		player.stop(); 
    }

    /**
     * Checks if is playing.
     *
     * @return true, if is playing
     */
    public boolean isPlaying() {
		if(player!=null)
			return (player.isRunning());
		return false;
	}
	
	/**
	 * Gets the current time.
	 *
	 * @return the current time
	 */
	public int getCurrentTime() {
		return (int) (((double) player.getMicrosecondPosition())/1000.0);
	}
	
	/**
	 * Sets the current time.
	 *
	 * @param time the new current time
	 */
	public void setCurrentTime(int time) {
		player.setMicrosecondPosition(time*1000);;
	}
	
	/**
	 * Gets the track length.
	 *
	 * @return length in Ms
	 */
	public int getTrackLength(){
		return trackLength;
	}
	
	/**
	 * Gets the percent.
	 *
	 * @return the percent
	 */
	private double getPercent() {
		if(!isPlaying()){
			return 0;
		}
		if(player!=null) {
			double pos =((double) player.getMicrosecondPosition())/1000.0;
			double len =((double) player.getMicrosecondLength())/1000.0;
			double percent = pos/len*100.0;
			return percent;
		}
		return 0;
	}

	
	/**
	 * Play step.
	 */
	public void playStep(){

		player.start();
		
	}

    /**
     * Play.
     */
    // play the MP3 file to the sound card
    public void play() {

        player.start();
    	

    }


    /**
     * The main method.
     *
     * @param args the arguments
     */
    // test client
    public static void main(String[] args) {
    	SequencerWAV mp3 = new SequencerWAV("track.mp3");
    	
		mp3.play();
		System.out.println("Track length= "+mp3.getTrackLength());
		while(mp3.isPlaying() ){
			System.out.println("Current "+mp3.getCurrentTime() +" Percent = "+mp3.getPercent());
			ThreadUtil.wait(100);
		}
		System.out.println("Finished "+mp3.getCurrentTime()+" of "+mp3.getTrackLength());
		System.exit(0);
		//mediaPlayer.

    }



	

}
