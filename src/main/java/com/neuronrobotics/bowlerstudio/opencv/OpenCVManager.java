package com.neuronrobotics.bowlerstudio.opencv;

import java.util.ArrayList;

import org.opencv.videoio.VideoCapture;

import com.neuronrobotics.sdk.common.DeviceManager;
import com.neuronrobotics.sdk.common.NonBowlerDevice;

public class OpenCVManager extends NonBowlerDevice {
	private static boolean libLoaded = false;
	private int camerIndex;
	private VideoCapture capture;
	static {
		try {
			nu.pattern.OpenCV.loadLocally();
			libLoaded = true;
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private OpenCVManager(int camerIndex) {
		this.camerIndex = camerIndex;
		if (!libLoaded)
			throw new RuntimeException("OpenCV library failed to load");
		connect();
	}
	
	public static OpenCVManager get(int index) {
		return (OpenCVManager)DeviceManager.getSpecificDevice("opencv_"+index, () -> new  OpenCVManager(index));
		
	}

	@Override
	public void disconnectDeviceImp() {
		if (getCapture() != null)
			getCapture().release();
	}

	@Override
	public boolean connectDeviceImp() {
		try {
			setCapture(new VideoCapture(camerIndex));
			getCapture().open(camerIndex);
			return true;
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return false;
	}

	@Override
	public ArrayList<String> getNamespacesImp() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @return the capture
	 */
	public VideoCapture getCapture() {
		return capture;
	}

	/**
	 * @param capture the capture to set
	 */
	private void setCapture(VideoCapture capture) {
		this.capture = capture;
	}

}
