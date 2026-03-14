package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.printbed.PrintBedManager;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.CSG;

import java.lang.reflect.Type;

public class UserManagedPrintBed implements IgenerateBed {

	private MobileBaseCadManager mobileBaseCadManager;
	Type type = new TypeToken<UserManagedPrintBedData>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	PrintBedManager pbm;
	private String source;
	public UserManagedPrintBed(File printArrangment, MobileBaseCadManager mobileBaseCadManager) throws IOException {
		this.mobileBaseCadManager = mobileBaseCadManager;
		source = mobileBaseCadManager.getMobileBase().getGitSelfSource()[0];
	}

	@Override
	public ArrayList<CSG> generateCad(DHParameterKinematics dh, int linkIndex) {
		throw new RuntimeException("This is not a cad generator");
	}

	@Override
	public ArrayList<CSG> generateBody(MobileBase base) {
		throw new RuntimeException("This is not a cad generator");

	}

	@Override
	public ArrayList<CSG> arrangeBed(MobileBase base) {
		pbm = new PrintBedManager(source, mobileBaseCadManager.getAllCad());
		return pbm.makePrintBeds();
	}

}
