package com.neuronrobotics.bowlerstudio;

import java.io.File;
import java.util.ArrayList;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.imageprovider.OpenCVJNILoader;
import com.sun.speech.freetts.ProcessException;
import com.sun.speech.freetts.VoiceManager;
import com.sun.speech.freetts.en.us.FeatureProcessors.WordNumSyls;

public class BowlerKernel{
	
	private static void fail(){
		System.err.println("Usage: BowlerScriptKernel scripts <file 1> .. <file n> # This will load one script after the next ");
		System.err.println("Usage: BowlerScriptKernel pipe <file 1> .. <file n> # This will load one script then take the list of objects returned and pss them to the next script as its 'args' variable ");
		System.exit(1);
	}

    /**
     * @param args the command line arguments
     * @throws Exception 
     */
    @SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception {
    		if(args.length==0){
    			fail();
    		}
    		OpenCVJNILoader.load();              // Loads the OpenCV JNI (java native interface)
    		boolean startLoadingScripts=false;
    		for(String s :args){
    			if(startLoadingScripts){
    				try{
    					ScriptingEngine.inlineFileScriptRun(new File(s), null);
    				}catch(Error e)
    				{
    					e.printStackTrace();
    					fail();
    				}
    			}
    			if(s.contains("scripts")){
    				startLoadingScripts=true;
    			}
    		}
    		startLoadingScripts=false;
    		Object ret=null;
    		for(String s :args){

    			if(startLoadingScripts){
    				try{
    					ret=ScriptingEngine.inlineFileScriptRun(new File(s), (ArrayList<Object>)ret);
    				}catch(Error e)
    				{
    					e.printStackTrace();
    					fail();
    				}
    			}
    			if(s.contains("pipe")){
    				startLoadingScripts=true;
    			}
    		}
    	
    }
    

	public static int speak(String msg){
		System.setProperty("freetts.voices", "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");
		VoiceManager voiceManager = VoiceManager.getInstance();
		com.sun.speech.freetts.Voice voice = voiceManager
				.getVoice("kevin16");
		Thread t = new Thread() {
			public void run() {
				setName("Speaking Thread");

				
				if(voice !=null){
					voice.setRate(200f);
					voice.allocate();
					voice.speak(msg);
					voice.deallocate();
				}else{
					System.out.println("All voices available:");
					com.sun.speech.freetts.Voice[] voices=voiceManager.getVoices();
					for (int i=0; i < voices.length; i++) {
					  System.out.println("    " + voices[i].getName() + " ("+ voices[i].getDomain()+ " domain)");
					}
				}
			}
		};
		t.start();
		WordNumSyls feature = (WordNumSyls)voice.getFeatureProcessor("word_numsyls");
		if(feature!=null)
		try {
			
			System.out.println("Syllables# = "+feature.process(null));
		} catch (ProcessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return 0;
	}

}
