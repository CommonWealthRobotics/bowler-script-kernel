package com.neuronrobotics.bowlerstudio.assets;

import java.util.ArrayList;

import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;

public class FontSizeManager {
	private static int[] fonts = new int[] { 6, 8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 40,48 };
	private static ArrayList<IFontSizeReciver> listeners = new ArrayList<IFontSizeReciver>();
	public static final int systemDefaultFontSize =12;
	public static int[] getFontOptions() {
		return fonts;
	}
	
	
	public static double getImageScale() {
		return ((double)getDefaultSize())/(18.0);
	}
	public static int getDefaultSize() {
		return  ((Number) ConfigurationDatabase.getObject("BowlerStudioConfigs", "fontsize", systemDefaultFontSize)).intValue();
	}
	public static void setFontSize(int myFoneNum) {
		ConfigurationDatabase.setObject("BowlerStudioConfigs", "fontsize", myFoneNum);
		for(IFontSizeReciver r:listeners) {
			try {
				r.fontSizeChange(myFoneNum);
			}catch(Throwable t) {
				t.printStackTrace();
			}
		}
		new Thread(()->{
			ConfigurationDatabase.save();	
		}).start();
		
	}
	public static void addListener(IFontSizeReciver r) {
		if(listeners.contains(r))
			return;
		listeners.add(r);
		r.fontSizeChange(getDefaultSize());
	}
	public static void removeListener(IFontSizeReciver r) {
		if(listeners.contains(r))
			listeners.remove(r);
	}
}
