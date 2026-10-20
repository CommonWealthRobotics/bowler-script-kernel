package com.neuronrobotics.bowlerstudio.threed;

/** Collects STEP entities, numbering them in the order they are added. */
final class StepEmitter {

	private final StringBuilder out = new StringBuilder();
	private long nextId;

	long add(String entity) {
		long id = ++nextId;
		out.append('#').append(id).append('=').append(entity).append(';').append('\n');
		return id;
	}

	String text() {
		return out.toString();
	}
}
