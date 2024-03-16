package com.neuronrobotics.bowlerstudio.physics;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;

import javax.xml.bind.JAXBException;

import org.mujoco.MuJoCoXML;
import org.mujoco.xml.Mujoco;
import org.mujoco.xml.Mujoco.Actuator.Builder;
import org.mujoco.xml.attributetypes.BuiltinType;
import org.mujoco.xml.attributetypes.FlagSimpleType;
import org.mujoco.xml.attributetypes.GeomtypeType;
import org.mujoco.xml.attributetypes.IntegratorType;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Vector3d;

public class MuJoCoPhysicsManager {
	private static final int Density_OF_PLA = 1250;
	private Mujoco.Builder<Void> builder;
	private Mujoco.Worldbody.Builder<?> addWorldbody;
	private Mujoco.Asset.Builder<?> asset;
	private List<MobileBase> bases;
	private List<CSG> freeObjects;
	private List<CSG> fixedObjects;
	private File workingDir;
	private int count=0;
	private String name;
	private double timestep=0.002;
	private int iterations=100;

	public MuJoCoPhysicsManager(String name,List<MobileBase> bases, List<CSG> freeObjects, List<CSG> fixedObjects,
			File workingDir) throws IOException, JAXBException {
		this.name = name;
		this.bases = bases;
		this.freeObjects = freeObjects;
		this.fixedObjects = fixedObjects;
		this.workingDir = workingDir;
		if (workingDir != null) {
			if(!workingDir.exists()) {
				workingDir.mkdirs();
			}
			if (!workingDir.isDirectory())
				throw new RuntimeException("Working Directory must be a directory");
		}
		generateNewModel();
	}

	public void generateNewModel() throws IOException, JAXBException {
		initializeModel(name);
		if (bases != null)
			if (bases.size() > 0) {
				Builder<?> actuators = builder.addActuator();
				for (MobileBase cat : bases) {
					loadBase(cat, actuators);
				}
			}
		if (freeObjects != null)
			for (CSG part : freeObjects) {
				try {
					addPart(part, true);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		if (fixedObjects != null)
			for (CSG part : fixedObjects) {
				try {
					addPart(part, false);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		File f= getXMLFile();
		
	}

	public void close() {
		if(bases!=null)
		bases.clear();
		if(freeObjects!=null)
		freeObjects.clear();
		if(fixedObjects!=null)
		fixedObjects.clear();
		builder = null;
		addWorldbody = null;
		asset = null;
	}
	
	public String getXML() throws JAXBException {
		Mujoco m =builder.build();
		
		return MuJoCoXML.marshal(m);
	}
	
	public File getXMLFile() throws IOException, JAXBException {
		String xml = getXML();
		File tempFile;
		if (workingDir == null) {
			tempFile = File.createTempFile(name+"-", ".xml");
			tempFile.deleteOnExit();
		} else {
			tempFile = new File(workingDir.getAbsolutePath() + "/" + name + ".xml");
		}
		System.out.println("Writing "+tempFile.getAbsolutePath());
		Files.write(Paths.get(tempFile.getAbsolutePath()), xml.getBytes());
		return tempFile;
	}

	private void initializeModel(String name) {
		builder = Mujoco.builder().withModel(name);

		builder.addOption()
			.withTimestep(new BigDecimal(getTimestep()))
			.withIterations(getIterations())
			.withIntegrator(IntegratorType.RK_4)
			.withViscosity(BigDecimal.valueOf(0.002))
			.withDensity(BigDecimal.valueOf(1.204))
			.addFlag()
				.withMulticcd(FlagSimpleType.ENABLE)
		;
		builder.addSize().withNjmax(8000).withNconmax(4000);
		builder.addVisual().addMap().withForce(new BigDecimal(0.1)).withZfar(new BigDecimal(30));
		builder.addStatistic().withCenter("0 0 0.7");
		asset = builder.addAsset();
		asset.addTexture().withName("grid").withType("2d").withBuiltin(BuiltinType.CHECKER).withRgb1(".1 .2 .3")
				.withRgb2(".2 .3 .4").withWidth(512).withHeight(512);
		asset.addMaterial().withName("grid").withTexture("grid").withTexrepeat("1 1").withTexuniform(true)
				.withReflectance(BigDecimal.valueOf(0.2));
		addWorldbody = builder.addWorldbody();
		addWorldbody.addGeom().withName("floor").withType(GeomtypeType.PLANE).withCondim(3).withSize("0 0 .05")
				.withMaterial("grid");
	}

	private int getIterations() {

		return iterations;
	}

	private void addPart(CSG part, boolean isFree) throws IOException {
		long start = System.currentTimeMillis();
		CSG hull = part.moveToCenter();
		Vector3d center = part.getCenter();
		
		String nameOfCSG = part.getName();
		if(nameOfCSG.length()==0) {
			nameOfCSG = "Part-"+(count);
		}
		nameOfCSG+="-"+(isFree?"free":"fixed");
		count++;
		String xml = hull.toObjString();
		File tempFile;
		if (workingDir == null) {
			tempFile = File.createTempFile(nameOfCSG+"-", ".obj");
			tempFile.deleteOnExit();
		} else {
			tempFile = new File(workingDir.getAbsolutePath() + "/" + nameOfCSG + ".obj");
		}
		
		System.out.print("\nWriting "+tempFile.getName());
		Files.write(Paths.get(tempFile.getAbsolutePath()), xml.getBytes());
		System.out.print(" "+(System.currentTimeMillis()-start));
		asset.addMesh()
			.withFile(tempFile.getName())
			.withName(nameOfCSG)
			.withScale(".001 .001 .001")// convert from mm to meters
		;
		org.mujoco.xml.body.GeomType.Builder<?> geom ;
		if(isFree) {
			org.mujoco.xml.BodyarchType.Builder<?> addBody = addWorldbody.addBody();
			addBody.addFreejoint();
			addBody.withPos(center.x/1000.0+" "+
					center.y/1000.0+" "+
					center.z/1000.0+" "
			);
			addBody.withName(nameOfCSG);
			geom= addBody.addGeom();
		}else {
			geom=addWorldbody.addGeom();
			geom.withPos(center.x/1000.0+" "+
					center.y/1000.0+" "+
					center.z/1000.0+" "
			);
		}
		/*
		 * condim
			1 Frictionless contact.
			3 Regular frictional contact, opposing slip in the tangent plane.
			4 Frictional contact, opposing slip in the tangent plane and rotation around the contact normal. This is useful for modeling soft contacts (independent of contact penetration).
			6 Frictional contact, opposing slip in the tangent plane, rotation around the contact normal and rotation around the two axes of the tangent plane. The latter frictional effects are useful for preventing objects from indefinite rolling.
		 */
		geom.withName(nameOfCSG)
		.withType(GeomtypeType.MESH)
		.withMesh(nameOfCSG)
		//.withGroup(isFree?1:2)
		//.withConaffinity(1)
		.withCondim(6)
		.withDensity(BigDecimal.valueOf(Density_OF_PLA/2.0))
		;
	}

	private void loadBase(MobileBase cat, Builder<?> actuators) {
		String bodyName = cat.getScriptingName() + "_base";
		MobileBaseCadManager cadMan = MobileBaseCadManager.get(cat);
		HashMap<String, AbstractLink> linkNameMap = new HashMap<>();
		loadCadForMobileBase(cadMan);

	}

	private void loadCadForMobileBase(MobileBaseCadManager cadMan) {
		boolean viewer = cadMan.isConfigMode();
		while (cadMan.getProcesIndictor().get() < 0.999) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("Waiting for cad to process " + cadMan.getProcesIndictor().get());
		}
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (viewer) {
			cadMan.setConfigurationViewerMode(false);
			cadMan.generateCad();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		while (cadMan.getProcesIndictor().get() < 0.999) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("Waiting for cad to process " + cadMan.getProcesIndictor().get());
		}
	}

	/**
	 * @return the timestep
	 */
	public double getTimestep() {
		return timestep;
	}

	/**
	 * @param timestep the timestep to set
	 */
	public void setTimestep(double timestep) {
		this.timestep = timestep;
	}

	/**
	 * @param iterations the iterations to set
	 */
	public void setIterations(int iterations) {
		this.iterations = iterations;
	}
}
