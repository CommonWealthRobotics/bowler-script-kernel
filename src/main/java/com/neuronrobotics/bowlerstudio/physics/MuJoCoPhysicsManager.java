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
import org.mujoco.xml.attributetypes.JointtypeType;
import org.mujoco.xml.body.JointType;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.imu.IMUUpdate;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.addons.kinematics.time.ITimeProvider;
import com.neuronrobotics.sdk.util.ThreadUtil;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;

@SuppressWarnings("restriction")
public class MuJoCoPhysicsManager implements IMujocoController,ITimeProvider {
	public  static final int Density_OF_PLA = 1250;
	public  Mujoco.Builder<Void> builder=null;
	public  Mujoco.Worldbody.Builder<?> addWorldbody;
	public  Mujoco.Asset.Builder<?> asset;
	public  List<MobileBase> bases;
	public  List<CSG> freeObjects;
	public  List<CSG> fixedObjects;
	public  File workingDir;
	public  int count=0;
	public  String name;
	public  double timestep=0.005;
	public  int iterations=100;
	public  MuJoCoModelManager mRuntime;
	/*
	 * condim
		1 Frictionless contact.
		3 Regular frictional contact, opposing slip in the tangent plane.
		4 Frictional contact, opposing slip in the tangent plane and rotation around the contact normal. This is useful for modeling soft contacts (independent of contact penetration).
		6 Frictional contact, opposing slip in the tangent plane, rotation around the contact normal and rotation around the two axes of the tangent plane. The latter frictional effects are useful for preventing objects from indefinite rolling.
	 */
	public  int condim=6;
	public  HashMap<String, ArrayList<CSG>> mapNameToCSG = new HashMap<>();
	public HashMap<Affine,String> affineNameMap =new HashMap<>();

	public  HashMap<String, AbstractLink> mapNameToLink=new HashMap<>();
	public  CSG floor = new Cube(4000,4000,1000).toCSG().toZMax().setName("floor").setColor(Color.PINK);
	public  long timeSinceUIUpdate = 0;
	public Mujoco.Contact.Builder<? > contacts;
	private IntegratorType integratorType=IntegratorType.RK_4;
	public HashMap<AbstractLink, Double> gearRatios = new HashMap<>();
	public long currentTimeMillis() {
		return (long)(getmRuntime().getCurrentSimulationTimeSeconds()*1000.0);
	}
	public void sleep(long time) throws InterruptedException {
		sleep(time,0);
	}
	public void sleep(long ms,int ns) throws InterruptedException {
		double seconds = (((double)ms)/1000.0)+(((double)ns)/10000000000.0);
		double now = getmRuntime().getCurrentSimulationTimeSeconds();
		double future=now+seconds;
		while(getmRuntime().getCurrentSimulationTimeSeconds()<future) {
			if(getmRuntime().getCurrentSimulationTimeSeconds()<now)
				throw new InterruptedException("Simulation reset!");
			Thread.sleep(getmRuntime().getTimestepMilliSeconds());
		}
	}
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
			if(link.getLinkConfiguration().isPassive())
				continue;
			double target =Math.toRadians( link.getCurrentEngineeringUnits())*gearRatios.get(link);
//			double error = target-positions.get(s);
//			double effort = error * kp;
			//System.out.println("Actuator "+s+" position "+positions.get(s)+" effort "+effort);
			setEfforts.put(s,target);
		}
		manager.setActualtorCtrl(setEfforts);
		for (int i = 0; i < bases.size(); i++) {
			MobileBase b = bases.get(i);
			if(b==null)
				continue;
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
					try {
						b.getImu().setVirtualState(new IMUUpdate( xAcceleration, yAcceleration, zAcceleration,
								rotxAcceleration, rotyAcceleration, rotzAcceleration,currentTimeMillis() ));
					}catch(NullPointerException e) {
						// startup sync problems, ignore
						System.out.println(e.getMessage());
					}
		
				}
				//System.out.println("Body "+s+" pose "+);
			}
		}
	}

	public void close() {
		if (bases != null) {
			for(MobileBase b:bases) {
				b.setTimeProvider(new ITimeProvider() {});
			}
			bases.clear();
		}
		if (freeObjects != null)
			freeObjects.clear();
		if (fixedObjects != null)
			fixedObjects.clear();
		builder = null;
		addWorldbody = null;
		asset = null;
		if (getmRuntime() != null)
			getmRuntime().close();
		mRuntime = null;
		mapNameToCSG.clear();
		gearRatios.clear();
	}
	public int getLinkIndex(AbstractLink l, DHParameterKinematics k) {
		for (int i=0;i<k.getNumberOfLinks();i++) {
			if(k.getAbstractLink(i)==l)
				return i;
		}
		return -1;
	}

	public DHParameterKinematics getLimb(AbstractLink l, MobileBase m) {
		for(DHParameterKinematics k:m.getAllDHChains()) {
			if(getLinkIndex(l, k)>=0)
				return k;
		}
		return null;
	}
	
	public ArrayList<CSG> getAllCSG(){
		ArrayList<CSG> parts = new ArrayList<>();
		for(String name:mapNameToCSG.keySet()) {
			parts.addAll(mapNameToCSG.get(name));
		}
		if(fixedObjects!=null)
			parts.addAll(fixedObjects);
		return parts;
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
		if(bases!=null)
			for(MobileBase b:bases)
				b.setTimeProvider(this);

	}
	public double getCurrentSimulationTimeSeconds() {
		return getmRuntime().getCurrentSimulationTimeSeconds();
	}
	public long getTimestepMilliSeconds() {
		return getmRuntime().getTimestepMilliSeconds();
	}
	public long stepAndWait() {
		long start = System.currentTimeMillis();
		step();
		long time = System.currentTimeMillis()-start;
		long diff = getTimestepMilliSeconds() -time;
		if(diff>0) {
			try {
				Thread.sleep(diff);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				throw new RuntimeException(e);
			}
		}else if(diff==0){
			if(Thread.interrupted())
				throw new RuntimeException("Interrupted exception!");
		}else {
			//System.err.println("MuJoCo Real time broken, expected "+getTimestepMilliSeconds()+" took "+time);
		}
		if(Thread.interrupted())
			throw new RuntimeException("Interrupted exception!");
		return time;
	}
	private void step() {
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

	public  void initializeModel(String name) {
		builder = Mujoco.builder().withModel(name);

		builder.addOption()
			.withTimestep(new BigDecimal(getTimestep()))
			.withIterations(getIterations())
			.withIntegrator(getIntegratorType())
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
		contacts= builder.addContact();
		if(fixedObjects==null) {
			fixedObjects=new ArrayList<>();
		}
		fixedObjects.add(floor);
	}

	public  int getIterations() {

		return iterations;
	}
	
	public  boolean checkForPhysics(CSG c) {
		return !c.getStorage().getValue("no-physics").isPresent();
	}
	public boolean checkLinkPhysics(MobileBaseCadManager cadMan,LinkConfiguration conf) {
		ArrayList<CSG>  parts=cadMan.getLinktoCadMap().get(conf);
		for(CSG c:parts) {
			if(checkForPhysics(c))
				return true;
		}
		return false;
	}
	
	public double computeLowestPoint(MobileBase cat) {
		MobileBaseCadManager cadMan = MobileBaseCadManager.get(cat);
	
		return cadMan.computeLowestPoint().z;
	}
	public void loadBase(MobileBase cat, Builder<?> actuators) throws IOException {
		if(contacts==null)
			contacts= builder.addContact();
		boolean freeBase=cat.getSteerable().size()>0||
				cat.getDrivable().size()>0||
				cat.getLegs().size()>0;
		
		//println "\n\nLowest point "+lowestPoint+" \n\n";
		String bodyName = getMujocoName(cat);
		MobileBaseCadManager cadMan = MobileBaseCadManager.get(cat);
		loadCadForMobileBase(cadMan);
		double lowestPoint = (-computeLowestPoint(cat)+2)/1000.0;

		int bodyParts=0;
		ArrayList<CSG> arrayList = cadMan.getBasetoCadMap().get(cat);
		TransformNR center = cat.getCenterOfMassFromCentroid();
		String centerString = center.getX()/1000.0+" "+
				center.getY()/1000.0+" "+
				center.getZ()/1000.0+" ";
		org.mujoco.xml.BodyarchType.Builder<?> addBody = addWorldbody.addBody()
				.withName(bodyName);
		//		addBody.addInertial()
		//				.withMass(BigDecimal.valueOf(cat.getMassKg()/1000.0))
		//				.withPos(centerString)
		//				.withDiaginertia("1 1 1" );
		if(freeBase) {
			addBody.addFreejoint();
			addBody.withPos("0 0 "+lowestPoint);// move the base to 1mm above the z=0 surface
		}
		for (int i = 0; i < arrayList.size(); i++) {
			CSG part = arrayList.get(i);

			if(!checkForPhysics(part))
				continue;
			bodyParts++;
			String nameOfCSG = bodyName+"_CSG_"+bodyParts;
			CSG transformed = part.transformed(TransformFactory.nrToCSG(center).inverse());
			CSG hull ;
			try {
					hull=transformed.hull();
			}catch(Exception ex) {
				hull=transformed;
			}
			transformed=part.clone();
			transformed.setManipulator(new Affine());
			putCSGInAssets(nameOfCSG, hull,true);
			org.mujoco.xml.body.GeomType.Builder<?> geom = addBody.addGeom();
			ArrayList<CSG> parts = getMapNameToCSGParts(bodyName);
			parts.add( transformed);
			setCSGMeshToGeom(nameOfCSG, geom);
			geom.withPos(centerString);
		}
		for(DHParameterKinematics l:cat.getAllDHChains()) {
			if(l.getScriptingName().contains("Dummy"))
				continue;
			String lastName = bodyName;
			for(int i=0;i<l.getNumberOfLinks();i++) {

				AbstractLink link = l.getAbstractLink(i);
				LinkConfiguration conf = link.getLinkConfiguration();
				if(!checkLinkPhysics(cadMan,conf))
					continue;
				String name = conf.getName()+"_"+l.getScriptingName();
				//println "Loading link "+name
				mapNameToLink.put(name, link);
				Affine a = (Affine)link.getGlobalPositionListener();
				//println "Link listener "+s+" is "+a
				affineNameMap.put(a, name);
				contacts.addExclude()
						.withBody1(bodyName)
						.withBody2(name);
				contacts.addExclude()
						.withBody1(lastName)
						.withBody2(name);
				lastName=name;
			}
		}
		for(DHParameterKinematics k:cat.getAllDHChains()) {
			if(k.getScriptingName().contains("Dummy"))
				continue;
			org.mujoco.xml.BodyarchType.Builder<?> linkBody =addBody;
			HashMap<AbstractLink,org.mujoco.xml.BodyarchType.Builder<?>> linkToBulder = new HashMap<>();

			for(int i=0;i<k.getNumberOfLinks();i++) {
				AbstractLink link = k.getAbstractLink(i);
				LinkConfiguration conf = link.getLinkConfiguration();
				ArrayList<CSG>  parts=cadMan.getLinktoCadMap().get(conf);
				if(checkLinkPhysics(cadMan,conf))
					linkBody=loadLink(k,i,parts,linkBody,actuators,linkToBulder);
			}
		}
	}

	public org.mujoco.xml.BodyarchType.Builder<?> loadLink(
			DHParameterKinematics l,
			int index,ArrayList<CSG>  cad,
			org.mujoco.xml.BodyarchType.Builder<?> addBody, 
			Builder<?> actuators,
			HashMap<AbstractLink,org.mujoco.xml.BodyarchType.Builder<?>> linkToBulderMap) {
		AbstractLink link = l.getAbstractLink(index);
		LinkConfiguration conf = link.getLinkConfiguration();

		String name=null;
		for(String s:mapNameToLink.keySet()) {
			if(mapNameToLink.get(s)==link) {
				name=s;
				break;
			}
		}
		if(name ==null)
			throw new RuntimeException("Link name missing from the map!");

		TransformNR location;
		if(index==0) {
			location=l.getRobotToFiducialTransform().copy();
		}else {
			location=new TransformNR(l.getDhLink(index-1).DhStep(0));
		}
		TransformNR local = new TransformNR(l.getDhLink(index).DhStep(0));
		Transform step = TransformFactory.nrToCSG(local);
		double x = location.getX()/1000.0;

		double y = location.getY()/1000.0;

		double z = location.getZ()/1000.0;
		String quat =location.getRotation().getRotationMatrix2QuaturnionW()+" "+
				location.getRotation().getRotationMatrix2QuaturnionX()+" "+
				location.getRotation().getRotationMatrix2QuaturnionY()+" "+
				location.getRotation().getRotationMatrix2QuaturnionZ();
		TransformNR center = conf.getCenterOfMassFromCentroid();
		String centerString = center.getX()/1000.0+" "+
				center.getY()/1000.0+" "+
				center.getZ()/1000.0+" ";
		org.mujoco.xml.BodyarchType.Builder<?> linkBody = addBody.addBody()
				.withName(name)
				.withPos(x+" "+y+" "+z)
				.withQuat(quat);
		//		linkBody.addInertial()
		//				.withMass(conf.getMassKg())
		//				.withPos(centerString)
		//				.withDiaginertia("1 1 1" )
		linkToBulderMap.put(link, linkBody);
//		TransformNR axis;
//		if(index==0) {
//			axis=l.getRobotToFiducialTransform().copy();
//		}else {
//			axis=l.forwardOffset(l.getLinkTip(index));
//		}
		double gear =260;
		gearRatios.put(link,1.0d*gear);
		double upper = Math.toRadians(link.getMaxEngineeringUnits());
		double lower = Math.toRadians(link.getMinEngineeringUnits());
		String range=lower+" "+upper;
		String ctrlRange=(lower*gear)+" "+(upper*gear);
		double rangeVal=upper-lower;
		JointType.Builder<?> jointBuilder = linkBody.addJoint();
		String axisJoint ="0 0 1";
		double position = Math.toRadians(link.getCurrentEngineeringUnits())*gearRatios.get(link);
		//position=0;
		jointBuilder
				.withPos("0 0 0")// the kinematic center
				.withAxis(axisJoint) // rotate about the z axis per dh convention
				.withRange(ctrlRange) // engineering units range
				.withRef(BigDecimal.valueOf(position)) // set the reference position on loading as the links 0 degrees value
				.withType(JointtypeType.HINGE) // hinge type
				.withFrictionloss(BigDecimal.valueOf(0.0001))// experementally determined
				//.withLimited(true)
				//.withDamping(BigDecimal.valueOf(0.00001))
				//.withStiffness(BigDecimal.valueOf(1))
				.withName(name)
		;
		if (!conf.isPassive()) {
			actuators.addPosition()// A position controller to model a servo
					.withKp(BigDecimal.valueOf(0.00004)) // experementally determined value
					//.withForcelimited(true)
					//.withForcerange(range)
					.withCtrlrange(ctrlRange)
					.withName(name)// name the motor
					.withJoint(name)// which joint this motor powers
					.withGear(""+gear) // gear ratio between virtual motor and output
					.withKv(BigDecimal.valueOf(0.0000008)); // damping term experementally determenied
					//.withInheritrange(BigDecimal.valueOf(rangeVal));// sets the range of the control signal to match the limits
		}
		for (int i = 0; i < cad.size(); i++) {
			CSG part = cad.get(i);
			if(!checkForPhysics(part))
				continue;
			Affine cGetManipulator = part.getManipulator();
			if(cGetManipulator!=null) {
				String affineNameMapGet = affineNameMap.get(cGetManipulator);
				if(affineNameMapGet!=null) {

					AbstractLink myLink = mapNameToLink.get(affineNameMapGet);
					double myposition = link.getCurrentEngineeringUnits();
					DHParameterKinematics k = l;
					CSG transformed = part
							.transformed(
							TransformFactory
							.nrToCSG(
							new TransformNR(
							k.getDhLink(myLink).DhStep(0)
							)
							).rotZ(position));
					CSG hull = transformed.hull();
					//					if(myLink!=link)
					//						hull = part.hull();
					transformed.setManipulator(new Affine());
					String geomname=name+" "+i;

					try {
						putCSGInAssets(geomname, hull,true);
						org.mujoco.xml.body.GeomType.Builder<?> geom = linkToBulderMap.get(myLink).addGeom();

						ArrayList<CSG> parts = getMapNameToCSGParts(affineNameMapGet);
						parts.add( transformed);
						setCSGMeshToGeom(geomname, geom);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else {
					//println "ERROR! "+name+" for part "+part.getName()+" produced no matching affine"
				}
			}
		}
		return linkBody;
	}

	public  double sig(double x) {
		if(x>0.999)
			return 1;
		if(x<-0.999)
			return -1;
		if(x<0.001 && x>-0.001)
			return 0;
		return x;
	}
	public  void addPart(CSG part, boolean isFree) throws IOException {
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

	public  ArrayList<CSG> getMapNameToCSGParts(String nameOfCSG) {
		mapNameToCSG.putIfAbsent(nameOfCSG, new ArrayList<CSG>());
		return mapNameToCSG.get(nameOfCSG);
	}
	
	public  void setCSGMeshToGeom(String nameOfCSG, org.mujoco.xml.body.GeomType.Builder<?> geom) {
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

	public  void setStartLocation(Vector3d center, org.mujoco.xml.BodyarchType.Builder<?> addBody) {
		addBody.withPos(center.x/1000.0+" "+
				center.y/1000.0+" "+
				center.z/1000.0+" "
		);
	}
	public  void setStartLocation(TransformNR center, org.mujoco.xml.BodyarchType.Builder<?> addBody) {
		addBody.withPos(center.getX()/1000.0+" "+
				center.getY()/1000.0+" "+
				center.getZ()/1000.0+" "
		);
	}
	public  String toColorString(Color c) {

		return c.getRed()+" "+c.getGreen()+" "+c.getBlue();
	}
	public  void putCSGInAssets(String nameOfCSG, CSG hull, boolean isFree) throws IOException {
		count++;

		File tempFile;
		boolean useCache=false;
		if (workingDir == null) {
			tempFile = File.createTempFile(nameOfCSG+"-", ".obj");
			tempFile.deleteOnExit();
		} else {
			tempFile = new File(workingDir.getAbsolutePath() + "/" + nameOfCSG + ".obj");
			if(tempFile.exists()) {
				// this should be enabled using a hash of the configuration to determine if the files are stale
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
	public  String getMujocoName(MobileBase cat) {
		return cat.getScriptingName() + "_base";
	}

	public  void loadCadForMobileBase(MobileBaseCadManager cadMan) {
		cadMan.run();
		if(!cadMan.isCADstarted() && cadMan.getProcesIndictor().get()<0.1) {
			cadMan.generateCad();
		}
		boolean viewer = cadMan.isConfigMode();
		long start = System.currentTimeMillis();
		waitForCad(cadMan,start);
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException(e);
		}
		if (viewer) {
			cadMan.setConfigurationViewerMode(false);
			cadMan.generateCad();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				throw new RuntimeException(e);
			}
		}
		waitForCad(cadMan,start);
		cadMan.render();
		ThreadUtil.wait(100);

	}

	public  void waitForCad(MobileBaseCadManager cadMan, long start) {
		double percent ;

		while ((percent =cadMan.getProcesIndictor().get()) < 0.999) {
			
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new RuntimeException(e);
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
			return;
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


	public  TransformNR mujocoToTransformNR(double[] bodyPose) {
		//cartesian pose, Xm, Ym, Zm, QuatW, QuatX, QuatY, QuatZ
		double x = bodyPose[0]*1000.0;
		double y = bodyPose[1]*1000.0;
		double z = bodyPose[2]*1000.0;

		//if(print)
		//println "coords "+[x,y,z]+" "+[qw, qx, qy, qz]

		RotationNR local = new RotationNR(bodyPose[3], bodyPose[4], bodyPose[5], bodyPose[6]);

		return new TransformNR(x,y,z,local);
	}
	/**
	 * @return the integratorType
	 */
	public IntegratorType getIntegratorType() {
		return integratorType;
	}
	/**
	 * @param integratorType the integratorType to set
	 */
	public void setIntegratorType(IntegratorType integratorType) {
		this.integratorType = integratorType;
	}
}
