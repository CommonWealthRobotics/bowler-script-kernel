package com.neuronrobotics.bowlerstudio.physics;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;

import org.mujoco.xml.Mujoco;
import org.mujoco.xml.Mujoco.Actuator.Builder;
import org.mujoco.xml.attributetypes.BuiltinType;
import org.mujoco.xml.attributetypes.GeomtypeType;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.CSG;

public class MuJoCoPhysicsManager {
	private Mujoco.Builder<Void> builder;
	private Mujoco.Worldbody.Builder<?> addWorldbody;
	private List<MobileBase> bases;
	private List<CSG> freeObjects;
	private List<CSG> fixedObjects;
	
	public MuJoCoPhysicsManager(List<MobileBase> bases, List<CSG> freeObjects, List<CSG> fixedObjects) {
		this.bases = bases;
		this.freeObjects = freeObjects;
		this.fixedObjects = fixedObjects;
		generateNewModel();
	}
	
	public void generateNewModel() {
		String name="";
		for (MobileBase cat : bases)
			name+=cat.getScriptingName()+" ";
		name=name.trim();
		initializeModel(name);
		if(bases.size()>0) {
			Builder<?> actuators = builder.addActuator();
			for (MobileBase cat : bases) {
				loadBase(cat,actuators);
			}
		}
		for (CSG part : freeObjects) {
			addPart(part, true);
		}
		for (CSG part : fixedObjects) {
			addPart(part, false);
		}
	}
	
	public void close() {
		bases.clear();
		freeObjects.clear();
		fixedObjects.clear();
		
	}

	private void initializeModel(String name) {
		builder = Mujoco.builder()
				.withModel(name);

		builder.addOption()
		.withTimestep(new BigDecimal(0.005));
		builder.addVisual()
		.addMap()
		.withForce(new BigDecimal(0.1))
		.withZfar(new BigDecimal(30))
		;
		builder.addStatistic()
		.withCenter("0 0 0.7");
		Mujoco.Asset.Builder<?> asset = builder.addAsset();
		asset.addTexture()
		.withName("grid")
		.withType("2d")
		.withBuiltin(BuiltinType.CHECKER)
		.withRgb1(".1 .2 .3")
		.withRgb2(".2 .3 .4")
		.withWidth(512)
		.withHeight(512)
		;
		asset.addMaterial()
		.withName("grid")
		.withTexture("grid")
		.withTexrepeat("1 1")
		.withTexuniform(true)
		.withReflectance(BigDecimal.valueOf(0.2))
		;
		addWorldbody = builder.addWorldbody();
		addWorldbody.addGeom()
		.withName("floor")
		.withType(GeomtypeType.PLANE)
		.withCondim(3)
		.withSize("0 0 .05")
		.withMaterial("grid");
	}

	private void addPart(CSG part, boolean isFree) {

	}

	private void loadBase(MobileBase cat, Builder<?> actuators) {
		String bodyName = cat.getScriptingName()+"_base";
		MobileBaseCadManager cadMan = MobileBaseCadManager.get(cat);
		HashMap<String,AbstractLink> linkNameMap = new HashMap<>();
		loadCadForMobileBase(cadMan);
		
	}

	private void loadCadForMobileBase(MobileBaseCadManager cadMan) {
		boolean viewer = cadMan.isConfigMode();
		while(cadMan.getProcesIndictor().get()<0.999) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println( "Waiting for cad to process "+cadMan.getProcesIndictor().get());
		}
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if(viewer) {
			cadMan.setConfigurationViewerMode(false);
			cadMan.generateCad();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		while(cadMan.getProcesIndictor().get()<0.999) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println( "Waiting for cad to process "+cadMan.getProcesIndictor().get());
		}
	}
}
