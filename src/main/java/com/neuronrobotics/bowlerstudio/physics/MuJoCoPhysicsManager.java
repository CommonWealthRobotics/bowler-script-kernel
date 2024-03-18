package com.neuronrobotics.bowlerstudio.physics;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.vecmath.Color3b;
import javax.xml.bind.JAXBException;

import org.mujoco.IMujocoController;
import org.mujoco.MuJoCoModelManager;
import org.mujoco.MuJoCoXML;
import org.mujoco.xml.Mujoco;
import org.mujoco.xml.Mujoco.Actuator.Builder;
import org.mujoco.xml.attributetypes.BuiltinType;
import org.mujoco.xml.attributetypes.FlagSimpleType;
import org.mujoco.xml.attributetypes.GeomtypeType;
import org.mujoco.xml.attributetypes.IntegratorType;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.imu.IMUUpdate;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

@SuppressWarnings("restriction")
public class MuJoCoPhysicsManager implements IMujocoController {
	private static final int Density_OF_PLA = 1250;
	private Mujoco.Builder<Void> builder=null;
	private Mujoco.Worldbody.Builder<?> addWorldbody;
	private Mujoco.Asset.Builder<?> asset;
	private List<MobileBase> bases;
	private List<CSG> freeObjects;
	private List<CSG> fixedObjects;
	private File workingDir;
	private int count=0;
	private String name;
	private double timestep=0.005;
	private int iterations=100;
	private MuJoCoModelManager mRuntime;
	/*
	 * condim
		1 Frictionless contact.
		3 Regular frictional contact, opposing slip in the tangent plane.
		4 Frictional contact, opposing slip in the tangent plane and rotation around the contact normal. This is useful for modeling soft contacts (independent of contact penetration).
		6 Frictional contact, opposing slip in the tangent plane, rotation around the contact normal and rotation around the two axes of the tangent plane. The latter frictional effects are useful for preventing objects from indefinite rolling.
	 */
	private int condim=6;
	private HashMap<String, ArrayList<CSG>> mapNameToCSG = new HashMap<>();
	private HashMap<String, AbstractLink> mapNameToLink=new HashMap<>();
	private CSG floor = new Cube(4000,4000,1000).toCSG().toZMax().setName("floor").setColor(Color.PINK);
	private long timeSinceUIUpdate = 0;
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
	}

	public void generateNewModel() throws IOException, JAXBException {
		mapNameToCSG.clear();
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
		if(mRuntime!=null)
			mRuntime.close();
		setmRuntime(new MuJoCoModelManager(f));

	}
	public double getCurrentSimulationTimeSeconds() {
		return getmRuntime().getCurrentSimulationTimeSeconds();
	}
	public long getTimestepMilliSeconds() {
		return getmRuntime().getTimestepMilliSeconds();
	}
	public boolean stepAndWait() {
		long start = System.currentTimeMillis();
		getmRuntime().step();
		if(start-timeSinceUIUpdate>16) {
			timeSinceUIUpdate=start;
			HashMap<String,TransformNR> poss = new HashMap<>();
			for (Iterator<String> iterator = getmRuntime().getBodyNames().iterator(); iterator.hasNext();) {
				String bodyName = iterator.next();
				poss.put(bodyName,mujocoToTransformNR(getmRuntime().getBodyPose(bodyName)));
			}
			BowlerKernel.runLater(()->{
				for (Iterator<String> iterator = getmRuntime().getBodyNames().iterator(); iterator.hasNext();) {
					String name = iterator.next();
					TransformNR local = poss.get(name);
					ArrayList<CSG> mapNameToCSGParts = getMapNameToCSGParts(name);
					for (int i = 0; i < mapNameToCSGParts.size(); i++) {
						CSG bodyBall = mapNameToCSGParts.get(i);
						TransformFactory.nrToAffine(local, bodyBall.getManipulator());
					}
				}
				poss.clear();
			});
		}
		long time = System.currentTimeMillis()-start;
		long diff = getTimestepMilliSeconds() -time;
		if(diff>0) {
			try {
				Thread.sleep(diff);
				return true;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}else if(diff==0){
			return true;
		}else {
			System.err.println("MuJoCo Real time broken, expected "+getTimestepMilliSeconds()+" took "+time);
		}
		return false;
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
		if(getmRuntime()!=null)
			getmRuntime().close();
		mapNameToCSG.clear();
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
			.withViscosity(BigDecimal.valueOf(0.00002))
			.withDensity(BigDecimal.valueOf(1.204))
			.addFlag()
				.withMulticcd(FlagSimpleType.ENABLE)
		;
		builder.addSize().withMemory("300M");
		builder.addVisual().addMap().withForce(new BigDecimal(0.1)).withZfar(new BigDecimal(30));
		builder.addStatistic().withCenter("0 0 0.7");
		asset = builder.addAsset();
		asset.addTexture().withName("grid").withType("2d").withBuiltin(BuiltinType.CHECKER).withRgb1(".1 .2 .3")
				.withRgb2(".2 .3 .4").withWidth(512).withHeight(512);
		asset.addMaterial().withName("grid").withTexture("grid").withTexrepeat("1 1").withTexuniform(true)
				.withReflectance(BigDecimal.valueOf(0.2));
		addWorldbody = builder.addWorldbody();

		try {
			addPart(floor,false);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private int getIterations() {

		return iterations;
	}
	
	private boolean checkForPhysics(CSG c) {
		return !c.getStorage().getValue("no-physics").isPresent();
	}
	private void loadBase(MobileBase cat, Builder<?> actuators) throws IOException {
		String bodyName = getMujocoName(cat);
		MobileBaseCadManager cadMan = MobileBaseCadManager.get(cat);
		loadCadForMobileBase(cadMan);
		int bodyParts=0;
		ArrayList<CSG> arrayList = cadMan.getBasetoCadMap().get(cat);
		org.mujoco.xml.BodyarchType.Builder<?> addBody = addWorldbody.addBody()
				.withName(bodyName);
		addBody.addFreejoint();
		for (int i = 0; i < arrayList.size(); i++) {
			CSG part = arrayList.get(i).hull();
			if(!checkForPhysics(part))
				continue;
			bodyParts++;
			String nameOfCSG = bodyName+"_CSG_"+bodyParts;
			TransformNR center = cat.getCenterOfMassFromCentroid();
			CSG hull = part.transformed(TransformFactory.nrToCSG(center).inverse());
			hull.setManipulator(new Affine());
			putCSGInAssets(nameOfCSG, hull,true);
			org.mujoco.xml.body.GeomType.Builder<?> geom = addBody.addGeom();
			ArrayList<CSG> parts = getMapNameToCSGParts(nameOfCSG);
			parts.add( hull);
			setCSGMeshToGeom(nameOfCSG, geom);
		}

	}
		
	private void addPart(CSG part, boolean isFree) throws IOException {
		if(!checkForPhysics(part))
			return;;
		String nameOfCSG = part.getName();
		if(nameOfCSG.length()==0) {
			nameOfCSG = "Part-"+(count);
		}
		nameOfCSG+="-"+(isFree?"free":"fixed");
		CSG hull = part.moveToCenter();
		hull.setManipulator(new Affine());
		Vector3d center = part.getCenter();
		putCSGInAssets(nameOfCSG, hull,isFree);
		org.mujoco.xml.body.GeomType.Builder<?> geom ;
		if(isFree) {
			org.mujoco.xml.BodyarchType.Builder<?> addBody = addWorldbody.addBody();
			addBody.addFreejoint();
			setStartLocation(center, addBody);
			addBody.withName(nameOfCSG);
			geom= addBody.addGeom();
			ArrayList<CSG> parts = getMapNameToCSGParts(nameOfCSG);
			parts.add( hull);
		}else {
			geom=addWorldbody.addGeom();
			geom.withPos(center.x/1000.0+" "+
					center.y/1000.0+" "+
					center.z/1000.0+" "
			);
		}

		setCSGMeshToGeom(nameOfCSG, geom);
	}

	private ArrayList<CSG> getMapNameToCSGParts(String nameOfCSG) {
		mapNameToCSG.putIfAbsent(nameOfCSG, new ArrayList<CSG>());
		return mapNameToCSG.get(nameOfCSG);
	}
	
	private void setCSGMeshToGeom(String nameOfCSG, org.mujoco.xml.body.GeomType.Builder<?> geom) {
		geom.withName(nameOfCSG)
		.withType(GeomtypeType.MESH)
		.withMesh(nameOfCSG)
		//.withGroup(isFree?1:2)
		//.withConaffinity(1)
		.withCondim(getCondim())
		.withDensity(BigDecimal.valueOf(Density_OF_PLA/2.0))
		.withMaterial(nameOfCSG)
		;
	}

	private void setStartLocation(Vector3d center, org.mujoco.xml.BodyarchType.Builder<?> addBody) {
		addBody.withPos(center.x/1000.0+" "+
				center.y/1000.0+" "+
				center.z/1000.0+" "
		);
	}
	private void setStartLocation(TransformNR center, org.mujoco.xml.BodyarchType.Builder<?> addBody) {
		addBody.withPos(center.getX()/1000.0+" "+
				center.getY()/1000.0+" "+
				center.getZ()/1000.0+" "
		);
	}
	private String toColorString(Color c) {

		return c.getRed()+" "+c.getGreen()+" "+c.getBlue();
	}
	private void putCSGInAssets(String nameOfCSG, CSG hull, boolean isFree) throws IOException {
		count++;

		File tempFile;
		boolean useCache=false;
		if (workingDir == null) {
			tempFile = File.createTempFile(nameOfCSG+"-", ".obj");
			tempFile.deleteOnExit();
		} else {
			tempFile = new File(workingDir.getAbsolutePath() + "/" + nameOfCSG + ".obj");
			if(tempFile.exists()) {
				//useCache=true;
			}
		}
		if(!useCache) {
			long start = System.currentTimeMillis();
			System.out.print("\nWriting "+tempFile.getName());
			String xml = hull.toObjString();
			Files.write(Paths.get(tempFile.getAbsolutePath()), xml.getBytes());
			System.out.print(" "+(System.currentTimeMillis()-start));
		}else {
			System.out.println("Loading cache "+tempFile.getName());
		}
		if(isFree) {
			asset.addTexture()
				.withRgb1(toColorString(hull.getColor()))
				.withRgb2(toColorString(hull.getColor()))
				.withName(nameOfCSG)
				.withType("2d")
				.withBuiltin(BuiltinType.FLAT)
				.withWidth(100)
				.withHeight(100);
		}else {
			asset.addTexture()
				.withRgb1(toColorString(floor.getColor()))
				.withRgb2(toColorString(hull.getColor()))
				.withName(nameOfCSG)
				.withType("2d")
				.withBuiltin(BuiltinType.GRADIENT)
				.withWidth(100)
				.withHeight(100);
		}
		asset.addMaterial()
			.withName(nameOfCSG)
			.withTexture(nameOfCSG)
			
		;

		asset.addMesh()
			.withFile(tempFile.getName())
			.withName(nameOfCSG)
			.withScale(".001 .001 .001")// convert from mm to meters
		;
	}
	private String getMujocoName(MobileBase cat) {
		return cat.getScriptingName() + "_base";
	}

	private void loadCadForMobileBase(MobileBaseCadManager cadMan) {
		if(!cadMan.isCADstarted()) {
			cadMan.generateCad();
		}
		boolean viewer = cadMan.isConfigMode();
		long start = System.currentTimeMillis();
		waitForCad(cadMan,start);
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
		waitForCad(cadMan,start);
	}

	private void waitForCad(MobileBaseCadManager cadMan, long start) {
		double percent ;

		while ((percent =cadMan.getProcesIndictor().get()) < 0.999) {
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.out.println("Waiting for cad to process " + percent);
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

	/**
	 * @return the condim
	 */
	public int getCondim() {
		return condim;
	}

	/*
	 * condim
		1 Frictionless contact.
		3 Regular frictional contact, opposing slip in the tangent plane.
		4 Frictional contact, opposing slip in the tangent plane and rotation around the contact normal. This is useful for modeling soft contacts (independent of contact penetration).
		6 Frictional contact, opposing slip in the tangent plane, rotation around the contact normal and rotation around the two axes of the tangent plane. The latter frictional effects are useful for preventing objects from indefinite rolling.
	 */
	public void setCondim(int condim) {
		switch (condim) {
		case 1:
		case 3:
		case 4:
		case 6:
			this.condim = condim;
		default:
			throw new RuntimeException("	 * condim\n" + "		1 Frictionless contact.\n"
					+ "		3 Regular frictional contact, opposing slip in the tangent plane.\n"
					+ "		4 Frictional contact, opposing slip in the tangent plane and rotation around the contact normal. This is useful for modeling soft contacts (independent of contact penetration).\n"
					+ "		6 Frictional contact, opposing slip in the tangent plane, rotation around the contact normal and rotation around the two axes of the tangent plane. The latter frictional effects are useful for preventing objects from indefinite rolling.\n"
					+ "");
		}
	}

	/**
	 * @return the mRuntime
	 */
	public MuJoCoModelManager getmRuntime() {
		if(mRuntime==null)
			try {
				generateNewModel();
			} catch (Exception e) {
				throw new RuntimeException(e);
			} 
		return mRuntime;
	}

	/**
	 * @param mRuntime the mRuntime to set
	 */
	public void setmRuntime(MuJoCoModelManager mRuntime) {
		this.mRuntime = mRuntime;
		mRuntime.setController(this);
	}

	@Override
	public void controlStep(MuJoCoModelManager manager) {
		HashMap<String, Double> setEfforts = manager.getControlInstance();
		HashMap<String, Double> positions = manager.getAllJointPositions();
		// this is a simple P controller
		double kp = 0.3;
		for (Iterator<String> iterator = manager.getActuatorNames().iterator(); iterator.hasNext();) {
			String s = iterator.next();
			AbstractLink link = mapNameToLink.get(s);
			if(link==null)
				continue;
			double target =link.getCurrentEngineeringUnits();
			double error = target-positions.get(s);
			double effort = error * kp;
			//System.out.println("Actuator "+s+" position "+positions.get(s)+" effort "+effort);
			setEfforts.put(s,effort);
		}
		manager.setActualtorCtrl(setEfforts);
		for (int i = 0; i < bases.size(); i++) {
			MobileBase b = bases.get(i);
			String bodyName = getMujocoName(b);
			for (Iterator<String> iterator = manager.getBodyNames().iterator(); iterator.hasNext();) {
				String s = iterator.next();
				if(bodyName.contentEquals(s)) {
					TransformNR tf = mujocoToTransformNR(manager.getBodyPose(s));
					Double xAcceleration=Math.toDegrees(tf.getRotation().getRotationTilt());
					Double yAcceleration=Math.toDegrees(tf.getRotation().getRotationAzimuth());
					Double zAcceleration=Math.toDegrees(tf.getRotation().getRotationElevation());

					Double rotxAcceleration=0.0;
					Double rotyAcceleration=0.0;
					Double rotzAcceleration=0.0;
					b.getImu().setVirtualState(new IMUUpdate( xAcceleration, yAcceleration, zAcceleration,
							rotxAcceleration, rotyAcceleration, rotzAcceleration ));
		
				}
				//System.out.println("Body "+s+" pose "+);
			}
		}
	}

	private TransformNR mujocoToTransformNR(double[] bodyPose) {
		//cartesian pose, Xm, Ym, Zm, QuatW, QuatX, QuatY, QuatZ
		double x = bodyPose[0]*1000.0;
		double y = bodyPose[1]*1000.0;
		double z = bodyPose[2]*1000.0;

		//if(print)
		//println "coords "+[x,y,z]+" "+[qw, qx, qy, qz]

		RotationNR local = new RotationNR(bodyPose[3], bodyPose[4], bodyPose[5], bodyPose[6]);

		return new TransformNR(x,y,z,local);
	}
}
