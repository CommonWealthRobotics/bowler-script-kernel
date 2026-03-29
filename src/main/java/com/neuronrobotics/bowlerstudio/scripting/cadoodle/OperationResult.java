package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

public enum OperationResult {
	PRUNE, INSERT, ABORT, APPEND, ASK;

	// Static factory method (case-insensitive)
	public static OperationResult fromString(String name) {
		for (OperationResult value : OperationResult.values()) {
			if (value.name().equalsIgnoreCase(name)) {
				return value;
			}
		}
		throw new IllegalArgumentException("No enum constant OperationResult with name: " + name);
	}

	@Override
	public String toString() {
		return name(); // Or name().toLowerCase(), etc.
	}
}
