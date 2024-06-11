package com.neuronrobotics.sdk.addons.gamepad;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import net.java.games.input.Component;
import net.java.games.input.Controller;
import net.java.games.input.Event;
import net.java.games.input.EventQueue;

import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.NonBowlerDevice;
import com.neuronrobotics.sdk.util.ThreadUtil;

import eu.mihosoft.vrl.v3d.JavaFXInitializer;

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
	
	private static ArrayList<Controller> controllers(){
		ArrayList<Controller> back = new ArrayList<Controller>();
		
		ControllerEnvironment defaultEnvironment = ControllerEnvironment.getDefaultEnvironment();
		Controller[] getDefaultEnvironmentGetControllers = defaultEnvironment.getControllers();
		for (int i = 0; i < getDefaultEnvironmentGetControllers.length; i++) {
			if (!getDefaultEnvironmentGetControllers[i].getName().contains("Wacom")) {
				back.add(getDefaultEnvironmentGetControllers[i]);
			}

		}
		return back;
	}

	public static ArrayList<String> getControllers() {
		ArrayList<Controller> getDefaultEnvironmentGetControllers = controllers();
		ArrayList<String> cons = new ArrayList<>();
		for (int i = 0; i < getDefaultEnvironmentGetControllers.size(); i++) {
			Controller controller = getDefaultEnvironmentGetControllers.get(i);
			String name = controller.getName();
			if(! name.contains("Wacom")){
				cons.add( name);
			}
		}
		return cons;
	}

	/**
	 * Instantiates a new bowler j input device.
	 */
	public BowlerJInputDevice(String... names) {
		
		setControllerByName(names!=null?Arrays.asList(names):null);
	}
	public BowlerJInputDevice( List<String> searches) {
		setControllerByName(searches);
	}
	
	private void setControllerByName(List<String> names) {
		searches =names;
		ArrayList<Controller> getDefaultEnvironmentGetControllers = controllers();
		int index = 0;

		if (names == null && getDefaultEnvironmentGetControllers.size() > 0) {
			controller = getDefaultEnvironmentGetControllers.get(index);
		} else {		
			for (String n : searches) {
				for (int i = 0; i < getDefaultEnvironmentGetControllers.size(); i++) {
					Controller c = getDefaultEnvironmentGetControllers.get(i);
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
						while (run) {
							if (controller == null) {
								while (run) {
									try {
										setControllerByName(searches);
										break;
									} catch (Throwable t) {
										System.out.println("BowlerJInputDevice Waiting for device to be availible");
										t.printStackTrace();
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
								}else
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
		if(!PersistantControllerMap.areAllAxisMapped(name)) {
			JogTrainerWidget.run(this);
		}
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
		for (String key : PersistantControllerMap.getMappedAxis(name)) {
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
		for (String key : PersistantControllerMap.getMappedAxis(name)) {
			values += "\n\t" + key + "<-" + PersistantControllerMap.getMappedAxisName(name, key);
		}
		return name + " = " + values;
	}

	public void map(String controllerVal, String persistantVal) {
		PersistantControllerMap.map(name,controllerVal, persistantVal);
	}

	

	public static void main(String[] args) throws InterruptedException {

		JavaFXInitializer.go();
		while (true) {
			try {
				BowlerJInputDevice g = new BowlerJInputDevice("X-Box","Gamesir","Dragon"); // 
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
