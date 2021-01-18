package com.neuronrobotics.sdk.addons.gamepad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
	private List<String> searches;
	static {
		net.java.games.input.ControllerEnvironment.getDefaultEnvironment();
	}

	public static String[] getControllers() {
		ControllerEnvironment defaultEnvironment = ControllerEnvironment.getDefaultEnvironment();
		Controller[] getDefaultEnvironmentGetControllers = defaultEnvironment.getControllers();
		ArrayList<String> cons = new ArrayList<>();
		for (int i = 0; i < getDefaultEnvironmentGetControllers.length; i++) {
			Controller controller = getDefaultEnvironmentGetControllers[i];
			String name = controller.getName();
			if(! name.contains("Wacom")){
				cons.add( name);
			}
		}
		String[] finalvals = new String[cons.size()];
		for (int i=0;i<finalvals.length;i++) {
			finalvals[i]=cons.get(i);
		}
		return finalvals;
	}

	/**
	 * Instantiates a new bowler j input device.
	 */
	public BowlerJInputDevice(String... names) {
		
		setControllerByName(names!=null?Arrays.asList(names):null);
	}

	private void setControllerByName(List<String> names) {
		searches =names;
		ControllerEnvironment defaultEnvironment = ControllerEnvironment.getDefaultEnvironment();

		Controller[] getDefaultEnvironmentGetControllers = defaultEnvironment.getControllers();
		int index = 0;
		for (int i = 0; i < getDefaultEnvironmentGetControllers.length; i++) {
			if (!getDefaultEnvironmentGetControllers[i].getName().contains("Wacom")) {
				index = i;
				break;
			}

		}

		if (names == null && getDefaultEnvironmentGetControllers.length > 0) {
			controller = getDefaultEnvironmentGetControllers[index];
		} else {		
			for (String n : searches) {
				for (int i = 0; i < getDefaultEnvironmentGetControllers.length; i++) {
					Controller c = getDefaultEnvironmentGetControllers[i];
					if (c.getName().toLowerCase().contains(n.toLowerCase())) {
						controller = c;
						break;
					} else
						System.out.println("Non match: " + c.getName() + " " + n);
				}
			}
		}
		if (controller != null) {
			this.setController(controller);
			return;
		}

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
						PersistantControllerMap.getGitSource();
					} catch (Exception e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					try {
						while (run) {
							if (controller == null) {
								while (run) {
									try {
										setControllerByName(searches);
										break;
									} catch (Throwable t) {
										System.out.println("BowlerJInputDevice Waiting for device to be availible");
										// t.printStackTrace();
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
								controller = null;
								continue;
							}
							EventQueue queue = controller.getEventQueue();
							Event event = new Event();
							while (queue.getNextEvent(event) && run) {
								Component comp = event.getComponent();
								float value = event.getValue();
								String n = comp.getName();
								if (n.contentEquals("pov")) {
									double angle = Math.PI * 2 * value;
									if (angle > 0) {
										sendValue((float) Math.sin(angle), "pov-up-down");
										sendValue((float) Math.cos(angle), "pov-left-right");
									} else {
										sendValue((float) 0, "pov-up-down");
										sendValue((float) 0, "pov-left-right");
									}
								}
								sendValue(value, n);
							}
							ThreadUtil.wait(16);
						}
					} catch (Throwable t) {
						t.printStackTrace();
					}
					disconnect();
				}

				private void sendValue(float value, String n) {
					n = PersistantControllerMap.getMappedAxisName(name, n);
					if (Math.abs(value) < 0.0001 && value != 0)
						value = 0;
					recentValue.put(n, (double) value);
					for (int i = 0; i < listeners.size(); i++) {
						IGameControlEvent l = listeners.get(i);
						try {
							l.onEvent(n, value);
						} catch (Throwable ex) {
							ex.printStackTrace();
						}
					}
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
		System.out.println("Found! " + controller.getName());
		this.name = controller.getName();
		controller.poll();
		EventQueue queue = controller.getEventQueue();
		Event event = new Event();
		while (queue.getNextEvent(event)) {
			// drain startup events
		}
		this.controller = controller;
		recentValue.clear();
		
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
		String values = " unmaped:";
		for (String key : recentValue.keySet()) {
			if (!PersistantControllerMap.isMapedAxis(name, key))
				values += "\n\t" + key + " = " + recentValue.get(key);
		}
		values += "\nMaped:";
		for (String key : PersistantControllerMap.getParamMap(name).keySet()) {
			String mappedAxisName = PersistantControllerMap.getMappedAxisName(name, key);
			values += "\n\t" + mappedAxisName + " (from \"" + key + "\") " + getValue(mappedAxisName);
		}
		return name + " " + values;
	}

	public String getMaps() {
		String values = "";
		for (String key : recentValue.keySet()) {
			values += "\n\t" + key + " = " + recentValue.get(key);
		}
		values += "\nMaps:";
		for (String key : PersistantControllerMap.getParamMap(name).keySet()) {
			values += "\n\t" + key + "<-" + PersistantControllerMap.getMappedAxisName(name, key);
		}
		return name + " = " + values;
	}

	public void map(String controllerVal, String persistantVal) {
		PersistantControllerMap.setObject(name, controllerVal, persistantVal);
		PersistantControllerMap.save();
	}

	public static List<String> getDefaultMaps() {
		return Arrays.asList("l-joy-up-down", "l-joy-left-right", "r-joy-up-down", "r-joy-left-right", "l-trig-button",
				"r-trig-button", "x-mode", "y-mode", "a-mode", "b-mode", "start", "select", "analog-trig");
	}

	public static void main(String[] args) throws InterruptedException {
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "y", "l-joy-up-down");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "rz", "r-joy-up-down");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "x", "l-joy-left-right");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "z", "r-joy-left-right");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "Left Thumb", "l-trig-button");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "Right Thumb", "r-trig-button");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "X", "x-mode");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "Y", "y-mode");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "A", "a-mode");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "B", "b-mode");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "Start", "start");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "Select", "select");
//		PersistantControllerMap.setObject("Gamesir-T4pro_21FD", "slider", "analog-trig");
//		PersistantControllerMap.save();

		while (true) {
			try {
				BowlerJInputDevice g = new BowlerJInputDevice("X-Box","Gamesir"); // 
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
