package com.neuronrobotics.bowlerstudio.creature;

import java.util.ArrayList;

import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;

import eu.mihosoft.vrl.v3d.CSG;

public interface IgenerateCad {
  /**
   * This function should use the D-H parameters to generate cad objects to build this configuration
   * the user should attach any listeners from the DH link for simulation
   *
   * @param dh the list of DH configurations
   * @return simulatable CAD objects
   */
  ArrayList<CSG> generateCad(DHParameterKinematics dh, int linkIndex);
}
