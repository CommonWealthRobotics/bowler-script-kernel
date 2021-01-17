package com.neuronrobotics.sdk.addons.gamepad;

import java.io.File;
import java.util.ArrayList;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.ControllerEnvironment;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;

import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.NonBowlerDevice;
import com.neuronrobotics.sdk.util.ThreadUtil;
import org.apache.commons.lang3.SystemUtils;
import us.ihmc.tools.nativelibraries.NativeLibraryLoader;

// TODO: Auto-generated Javadoc
/**
 * The Class BowlerJInputDevice.
 */
public class BowlerJInputDevice extends NonBowlerDevice {

	/** The controller. */
	private Controller controller;

	/** The listeners. */
	private ArrayList<IGameControlEvent> listeners = new ArrayList<IGameControlEvent>();

	/** The run. */
	boolean run = true;

	/** The poller. */
	private Thread poller;
	
	public static String[] getControllers() {
		loadLibs();
		ControllerEnvironment defaultEnvironment = ControllerEnvironment.getDefaultEnvironment();
		Controller[] getDefaultEnvironmentGetControllers = defaultEnvironment.getControllers();
		String[] cons = new String[getDefaultEnvironmentGetControllers.length];
		for(int i=0;i<cons.length;i++) {
			cons[i]=getDefaultEnvironmentGetControllers[i].getName();
		}
		return cons;
	}

	/**
	 * Instantiates a new bowler j input device.
	 */
	public BowlerJInputDevice(String name) {
		loadLibs();
		ControllerEnvironment defaultEnvironment = ControllerEnvironment.getDefaultEnvironment();
		Controller[] getDefaultEnvironmentGetControllers = defaultEnvironment.getControllers();
		System.out.println("COntrollers = " + getDefaultEnvironmentGetControllers);
		if (controller != null)
			this.setController(controller);
		else
			throw new RuntimeException("Contoller must not be null");

	}

	public static void loadLibs() {
		String absolutePathToDirectory;
		if (SystemUtils.IS_OS_WINDOWS) {
			absolutePathToDirectory = NativeLibraryLoader.extractLibraries("", "jinput-raw", "jinput-raw_64",
					"jinput-dx8_64", "jinput-dx8", "jinput-wintab");
		} else if (SystemUtils.IS_OS_LINUX) {
			absolutePathToDirectory = NativeLibraryLoader.extractLibraries("", "jinput-linux64", "jinput-linux");
		} else if (SystemUtils.IS_OS_MAC) {
			absolutePathToDirectory = new File(NativeLibraryLoader.extractLibraryAbsolute("", "libjinput-osx.jnilib"))
					.getParent();
		} else {
			throw new RuntimeException("Failed to load the native library for JInput!");
		}

		System.setProperty("net.java.games.input.librarypath", absolutePathToDirectory);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.neuronrobotics.sdk.common.NonBowlerDevice#disconnectDeviceImp()
	 */
	@Override
	public void disconnectDeviceImp() {
		listeners.clear();
		poller = null;
		run = false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.neuronrobotics.sdk.common.NonBowlerDevice#connectDeviceImp()
	 */
	@Override
	public boolean connectDeviceImp() {
		if (poller == null) {
			poller = new Thread() {
				public void run() {
					setName("Game Controller Poll thread");
					Log.warning("Starting game Pad Poller");
					while (run) {
						boolean pollStat = controller.poll();
						EventQueue queue = controller.getEventQueue();
						Event event = new Event();
						while (queue.getNextEvent(event) && run) {
//							StringBuffer buffer = new StringBuffer(controller.getName());
//							buffer.append(" at ");
//							buffer.append(event.getNanos()).append(", ");
							Component comp = event.getComponent();
//							buffer.append(comp.getName()).append(" changed to ");
							float value = event.getValue();
//							if (comp.isAnalog()) {
//								buffer.append(value);
//							} else {
//								if (value > 0) {
//									buffer.append("On");
//								} else {
//									buffer.append("Off");
//								}
//							}
//							Log.info(buffer.toString());
							for (int i = 0; i < listeners.size(); i++) {
								IGameControlEvent l = listeners.get(i);
								try {
									l.onEvent(comp.getName(), value);
								} catch (Exception ex) {
									ex.printStackTrace();
								}
							}
						}
						ThreadUtil.wait(10);
					}
					poller=null;
				}
			};
			poller.start();
		}
		return true;
	}

	/**
	 * Gets the controller.
	 *
	 * @return the controller
	 */
	public String getControllerName() {
		return controller.getName();
	}

	/**
	 * Sets the controller.
	 *
	 * @param controller the new controller
	 */
	public void setController(Controller controller) {
		this.controller = controller;
	}

	/**
	 * Removes the listeners.
	 *
	 * @param l the l
	 */
	public void removeListeners(IGameControlEvent l) {
		if (listeners.contains(l))
			this.listeners.remove(l);
	}

	/**
	 * Removes all the listeners.
	 *
	 */
	public void clearListeners() {
		this.listeners.clear();
	}

	/**
	 * Adds the listeners.
	 *
	 * @param l the l
	 */
	public void addListeners(IGameControlEvent l) {
		if (!listeners.contains(l))
			this.listeners.add(l);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.neuronrobotics.sdk.common.NonBowlerDevice#getNamespacesImp()
	 */
	@Override
	public ArrayList<String> getNamespacesImp() {
		// TODO Auto-generated method stub
		return new ArrayList<String>();
	}

	public static void main(String[] args) throws InterruptedException {

		BowlerJInputDevice g = new BowlerJInputDevice(null); // This is the DyIO to talk to.
		g.connect(); // Connect to it.
		
		while(true)Thread.sleep(100);
	}

}
