package com.neuronrobotics.bowlerstudio.threed;

/** Collects STEP entities, numbering them in the order they are added. */
final class StepEmitter {

	private final StringBuilder out = new StringBuilder();
	private long nextId;
    private final java.util.Map<Long, String> kinds = new java.util.HashMap<>();

	long add(String entity) {
		long id = ++nextId;
        kinds.put(id, entity.substring(0, entity.indexOf('(')).trim());
		out.append('#').append(id).append('=').append(entity).append(';').append('\n');
		return id;
	}

    boolean is(long id, String kind) {
        return kind.equals(kinds.get(id));
    }

	String text() {
		return out.toString();
	}
}
