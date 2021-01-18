package com.neuronrobotics.sdk.addons.gamepad;

public interface IGameControlEvent {
	/**
	 * On event.
	 *
	 * @param name the name of the value that changed
	 * @param value the value
	 */
	public void onEvent(String name,float value);
}
