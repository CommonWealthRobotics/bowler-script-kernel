package com.neuronrobotics.bowlerstudio.creature;

import java.util.ArrayList;

import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.CSG;

public interface IgenerateBody {

  /**
   * This function should generate the body and any limbs of a given base. the user should attach
   * any listeners from the DH link for simulation
   *
   * @param base the base to generate
   * @return simulatable CAD objects
   */
  ArrayList<CSG> generateBody(MobileBase base);
}
