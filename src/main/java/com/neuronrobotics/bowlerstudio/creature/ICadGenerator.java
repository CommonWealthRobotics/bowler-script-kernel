package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.util.ArrayList;

import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.CSG;

public interface ICadGenerator {
	/**
	 * This function should use the D-H parameters to generate cad objects to build this configuration
	 * the user should attach any listeners from the DH link for simulation
	 * @param dhLinks the list of DH configurations
	 * @return simulatable CAD objects
	 */
	ArrayList<CSG> generateCad(DHParameterKinematics dh, boolean toManufacture );
	/**
	 * This function should generate the body and any limbs of a given base. 
	 * the user should attach any listeners from the DH link for simulation
	 * @param base the base to generate
	 * @return simulatable CAD objects
	 */
	ArrayList<CSG> generateBody(MobileBase base , boolean toManufacture);
}
