package com.neuronrobotics.bowlerstudio.physics;

import java.util.ArrayList;


import eu.mihosoft.vrl.v3d.CSG;

public interface IPhysicsCore {

	ArrayList<CSG> getCsgFromEngine();

	void clear();

	void remove(IPhysicsManager manager);

	void add(IPhysicsManager manager);

	void stepMs(double timeStep);

	void step(float timeStep);

	void stopPhysicsThread();

	void startPhysicsThread(int ms);


}
