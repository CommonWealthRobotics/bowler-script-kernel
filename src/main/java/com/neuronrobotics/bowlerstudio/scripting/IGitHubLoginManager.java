package com.neuronrobotics.bowlerstudio.scripting;

public interface IGitHubLoginManager {
	/**
	 * Prompt user for login information and return when it has been entered
	 * @param loginID 
	 * @return an array of strings of length 2 that contains the username in the 0th index, 
	 * and the password in the 1th index
	 */
	public String [] prompt(String loginID);
	
	
}
