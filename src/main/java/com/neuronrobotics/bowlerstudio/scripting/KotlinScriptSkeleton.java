package com.neuronrobotics.bowlerstudio.scripting;

import java.util.List;

/**
 * Provides a skeleton-code structure for kotlin scripts so that arguments can be passed to them.
 */
public interface KotlinScriptSkeleton {

    /**
     * Run the script with the arguments.
     *
     * @param args The arguments. Can be null. Can contain nulls.
     * @return The result. Can be null.
     * @throws Exception Any exceptions while running the script.
     */
    Object runScript(List<Object> args) throws Exception;
}
