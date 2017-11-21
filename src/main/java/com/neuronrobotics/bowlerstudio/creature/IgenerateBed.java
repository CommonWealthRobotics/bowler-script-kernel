package com.neuronrobotics.bowlerstudio.creature;

import java.util.ArrayList;

import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.CSG;

public interface IgenerateBed extends ICadGenerator{
	/**
	 * This function should generate the bed or beds or parts to be used in manufacturing
	 * If parts are to be ganged up to make print beds then this should happen here
	 * @param base the base to generate
	 * @return simulatable CAD objects
	 */
	ArrayList<CSG> arrangeBed(MobileBase base );
}
