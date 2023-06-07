package com.neuronrobotics.bowlerkernel.djl;

import java.util.ArrayList;

public class UniquePerson {
	public long UUID=1;
	public String name="";
	public ArrayList<float[]> features=new ArrayList<float[]>();
	public String referenceImageLocation;
	public long timesSeen = 1;
	public long time=System.currentTimeMillis();
	double confidenceTarget = UniquePersonFactory.getConfidence();
}
