package com.neuronrobotics.sdk.addons.gamepad;

import java.util.ArrayList;
import java.util.HashMap;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.NonBowlerDevice;
import com.neuronrobotics.sdk.util.ThreadUtil;

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

	private HashMap<String, Double> recentValue = new HashMap<String, Double>();

	/** The poller. */
	private Thread poller;

	private String name;
	static {
		net.java.games.input.ControllerEnvironment.getDefaultEnvironment();
	}

	public static String[] getControllers() {
		ControllerEnvironment defaultEnvironment = ControllerEnvironment.getDefaultEnvironment();
		Controller[] getDefaultEnvironmentGetControllers = defaultEnvironment.getControllers();
		String[] cons = new String[getDefaultEnvironmentGetControllers.length];
		for (int i = 0; i < cons.length; i++) {
			cons[i] = getDefaultEnvironmentGetControllers[i].getName();
		}
		return cons;
	}

	/**
	 * Instantiates a new bowler j input device.
	 */
	public BowlerJInputDevice(String name) {
		this.setName(name);
		setControllerByName(name);

	}

	private void setControllerByName(String name) {
		ControllerEnvironment defaultEnvironment = ControllerEnvironment.getDefaultEnvironment();

		Controller[] getDefaultEnvironmentGetControllers = defaultEnvironment.getControllers();
		if (name == null && getDefaultEnvironmentGetControllers.length > 0) {
			controller = getDefaultEnvironmentGetControllers[0];
		} else
			for (int i = 0; i < getDefaultEnvironmentGetControllers.length; i++) {
				Controller c = getDefaultEnvironmentGetControllers[i];
				if (c.getName().toLowerCase().contains(name.toLowerCase())) {
					System.out.println("Found! " + c.getName());
					controller = c;
					this.name=c.getName();
					controller.poll();
					EventQueue queue = controller.getEventQueue();
					Event event = new Event();
					while (queue.getNextEvent(event)) {
						// drain startup events
					}
					break;
				} else
					System.out.println("Non match: " + c.getName() + " " + name);
			}
		if (controller != null)
			this.setController(controller);
		else
			throw new RuntimeException("Contoller must not be null");
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

	public double getValue(String component) {
		if (recentValue.get(component) == null)
			return 0;
		return recentValue.get(component);
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
					try {
						while (run) {
							if(controller==null) {
								while (run) {
									try {
										setControllerByName(name);
										break;
									} catch (Throwable t) {
										System.out.println("BowlerJInputDevice Waiting for device to be availible");
										//t.printStackTrace();
										try {
											Thread.sleep(1000);
										} catch (InterruptedException e) {
											// TODO Auto-generated catch block
											e.printStackTrace();
										}
									}
								}
							}
							boolean pollStat = controller.poll();
							if (!pollStat) {
								controller=null;
								continue;
							}
							EventQueue queue = controller.getEventQueue();
							Event event = new Event();
							while (queue.getNextEvent(event) && run) {
								Component comp = event.getComponent();
								float value = event.getValue();
								recentValue.put(comp.getName(), (double) value);
								for (int i = 0; i < listeners.size(); i++) {
									IGameControlEvent l = listeners.get(i);
									try {
										l.onEvent(comp.getName(), value);
									} catch (Throwable ex) {
										ex.printStackTrace();
									}
								}
							}
							ThreadUtil.wait(16);
						}
					} catch (Throwable t) {
						t.printStackTrace();
					}
					disconnect();
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
	
	@Override
	public String toString() {
		String values = "";
		for(String key:recentValue.keySet()) {
			values+="\n\t"+key+" = "+recentValue.get(key);
		}
		return name+" = "+values;
	}

	public static void main(String[] args) throws InterruptedException {

		while (true) {
			try {
				BowlerJInputDevice g = new BowlerJInputDevice("Gamesir"); // This is the DyIO to talk to.
				g.connect(); // Connect to it.
				g.addListeners((name, value) -> {
					System.out.println(g);
				});
				while (g.isAvailable())
					Thread.sleep(100);
				System.out.println("Controller clean exit");
			} catch (Throwable t) {
				System.out.println("Waiting for device to be availible");
				t.printStackTrace();
				Thread.sleep(1000);
			}
		}

	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}
