package com.neuronrobotics.bowlerstudio.creature;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.scripting.RobotHelper;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotController;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.ModifyLimb;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.addons.kinematics.parallel.ParallelGroup;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobileBaseBuilder {
	ArrayList<AddRobotController> controllers = new ArrayList<AddRobotController>();
	ArrayList<AddRobotLimb> limbs = new ArrayList<AddRobotLimb>();
	ArrayList<ModifyLimb> mods = new ArrayList<ModifyLimb>();
	private MobileBase mobileBase;
	private String gitURL;
	private String xmlName = null;

	// Channel management
	private Map<String, Map<Integer, Boolean>> deviceChannelMap = new HashMap<>();
	private CSGDatabaseInstance db;

	// Constructor for creating a new MobileBase
	public MobileBaseBuilder(CSGDatabaseInstance db,String gitURL, String name) {
		this.db=db;
		this.gitURL = gitURL;
		this.mobileBase = new MobileBase();
		this.mobileBase.setScriptingName(name);
		initializeChannelMap();
	}

	// Constructor for extending an existing MobileBase
	public MobileBaseBuilder(CSGDatabaseInstance db,MobileBase existingBase) {
		this.db = db;
		this.gitURL = existingBase.getGitSelfSource()[0];
		this.mobileBase = existingBase;
		initializeChannelMap();
		scanExistingChannels();
	}

	// Copy functionality from the menu factory
	public MobileBaseBuilder copyFrom(MobileBase source, String newName) {
		try {
			mobileBase.setScriptingName(newName);

			// Copy engines
			if (source.getGitCadEngine() != null) {
				mobileBase.setGitCadEngine(copyGitFile(source.getGitCadEngine(), gitURL));
			}
			if (source.getGitWalkingEngine() != null) {
				mobileBase.setGitWalkingEngine(copyGitFile(source.getGitWalkingEngine(), gitURL));
			}

			// Copy appendages
			for (DHParameterKinematics leg : source.getLegs()) {
				DHParameterKinematics copiedLeg = copyDHParameterKinematics(leg);
				mobileBase.getLegs().add(copiedLeg);
			}

			for (DHParameterKinematics arm : source.getAppendages()) {
				DHParameterKinematics copiedArm = copyDHParameterKinematics(arm);
				mobileBase.getAppendages().add(copiedArm);
			}

			for (DHParameterKinematics wheel : source.getSteerable()) {
				DHParameterKinematics copiedWheel = copyDHParameterKinematics(wheel);
				mobileBase.getSteerable().add(copiedWheel);
			}

			for (DHParameterKinematics wheel : source.getDrivable()) {
				DHParameterKinematics copiedWheel = copyDHParameterKinematics(wheel);
				mobileBase.getDrivable().add(copiedWheel);
			}

			// Copy transforms
			mobileBase.setFiducialToGlobalTransform(source.getRobotToFiducialTransform());
			mobileBase.setIMUFromCentroid(source.getIMUFromCentroid());
		} catch (Exception e) {
			Log.error("Failed to copy from source MobileBase: " + e.getMessage());
		}
		return this;
	}

//	// Constructor for extending an existing MobileBase with new name
//	public MobileBaseBuilder(String gitURL, MobileBase existingBase, String newName) {
//		this.gitURL = gitURL;
//		this.mobileBase = existingBase;
//		this.mobileBase.setScriptingName(newName);
//		initializeChannelMap();
//		scanExistingChannels();
//	}

	public MobileBase cloneMobileBase(MobileBase source) {
		try {
			// Create a deep copy by serializing and deserializing XML
			String xml = source.getXml();
			MobileBase clone = new MobileBase();
			clone.setScriptingName(source.getScriptingName());

			// Copy all appendages
			for (DHParameterKinematics leg : source.getLegs()) {
				clone.getLegs().add(copyDHParameterKinematics(leg));
			}
			for (DHParameterKinematics arm : source.getAppendages()) {
				clone.getAppendages().add(copyDHParameterKinematics(arm));
			}
			for (DHParameterKinematics wheel : source.getSteerable()) {
				clone.getSteerable().add(copyDHParameterKinematics(wheel));
			}
			for (DHParameterKinematics wheel : source.getDrivable()) {
				clone.getDrivable().add(copyDHParameterKinematics(wheel));
			}

			// Copy transforms
			if (source.getRobotToFiducialTransform() != null) {
				clone.setRobotToFiducialTransform(source.getRobotToFiducialTransform());
			}
			if (source.getIMUFromCentroid() != null) {
				clone.setIMUFromCentroid(source.getIMUFromCentroid());
			}

			// Copy engines
			if (source.getGitCadEngine() != null) {
				clone.setGitCadEngine(source.getGitCadEngine());
			}
			if (source.getGitWalkingEngine() != null) {
				clone.setGitWalkingEngine(source.getGitWalkingEngine());
			}

			return clone;
		} catch (Exception e) {
			Log.error("Failed to clone MobileBase: " + e.getMessage());
			return new MobileBase();
		}
	}

	private void initializeChannelMap() {
		// Initialize with empty channel maps - will be populated as needed
	}

	private void scanExistingChannels() {
		// Scan all existing appendages to build current channel usage map
		scanAppendageChannels(mobileBase.getLegs());
		scanAppendageChannels(mobileBase.getAppendages());
		scanAppendageChannels(mobileBase.getSteerable());
		scanAppendageChannels(mobileBase.getDrivable());
	}

	private void scanAppendageChannels(List<DHParameterKinematics> appendages) {
		for (DHParameterKinematics appendage : appendages) {
			for (LinkConfiguration conf : appendage.getLinkConfigurations()) {
				String deviceName = conf.getDeviceScriptingName();
				int channel = conf.getHardwareIndex();
				if (deviceName != null) {
					reserveDeviceChannel(deviceName, channel);
				}
			}
		}
	}

	// Getter for the current MobileBase instance
	public MobileBase getMobileBase() {
		return mobileBase;
	}

	public MobileBaseBuilder setXmlName(String xmlName) {
		this.xmlName = xmlName;
		return this;
	}

	public MobileBaseBuilder setGitCadEngine(String gitURL, String filename) {
		mobileBase.setGitCadEngine(new String[] { gitURL, filename });
		return this;
	}

	public MobileBaseBuilder setGitWalkingEngine(String gitURL, String filename) {
		mobileBase.setGitWalkingEngine(new String[] { gitURL, filename });
		return this;
	}

	public MobileBaseBuilder setRobotToFiducialTransform(TransformNR transform) {
		mobileBase.setRobotToFiducialTransform(transform);
		return this;
	}

	public MobileBaseBuilder setIMUFromCentroid(TransformNR transform) {
		mobileBase.setIMUFromCentroid(transform);
		return this;
	}

	public MobileBaseBuilder setBodyMass(double mass) {
		// Note: MobileBase doesn't seem to have a setBodyMass method
		// You may need to add this functionality to MobileBase or handle it differently
		return this;
	}

	// Leg management
	public MobileBaseBuilder addLeg(DHParameterKinematics leg) {
		if (leg != null) {
			configureAppendage(leg);
			mobileBase.getLegs().add(leg);
		}
		return this;
	}

	public MobileBaseBuilder addDefaultLeg(String legName) {
		try {
			String xmlContent = ScriptingEngine.codeFromGit(
					"https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git", "defaultleg.xml")[0];
			DHParameterKinematics newLeg = new DHParameterKinematics(null, IOUtils.toInputStream(xmlContent, "UTF-8"));
			newLeg.setScriptingName(legName);
			return addLeg(newLeg);
		} catch (Exception e) {
			Log.error("Failed to add default leg: " + e.getMessage());
			return this;
		}
	}

	// Arm management
	public MobileBaseBuilder addArm(DHParameterKinematics arm) {
		if (arm != null) {
			configureAppendage(arm);
			mobileBase.getAppendages().add(arm);
		}
		return this;
	}

	public MobileBaseBuilder addDefaultArm(String armName) {
		try {
			String xmlContent = ScriptingEngine.codeFromGit(
					"https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git", "defaultarm.xml")[0];
			DHParameterKinematics newArm = new DHParameterKinematics(null, IOUtils.toInputStream(xmlContent, "UTF-8"));
			newArm.setScriptingName(armName);
			return addArm(newArm);
		} catch (Exception e) {
			Log.error("Failed to add default arm: " + e.getMessage());
			return this;
		}
	}

	// Wheel management
	public MobileBaseBuilder addSteerableWheel(DHParameterKinematics wheel) {
		if (wheel != null) {
			configureAppendage(wheel);
			mobileBase.getSteerable().add(wheel);
		}
		return this;
	}

	public MobileBaseBuilder addDefaultSteerableWheel(String wheelName) {
		try {
			String xmlContent = ScriptingEngine.codeFromGit(
					"https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git", "defaultSteerable.xml")[0];
			DHParameterKinematics newWheel = new DHParameterKinematics(null,
					IOUtils.toInputStream(xmlContent, "UTF-8"));
			newWheel.setScriptingName(wheelName);
			return addSteerableWheel(newWheel);
		} catch (Exception e) {
			Log.error("Failed to add default steerable wheel: " + e.getMessage());
			return this;
		}
	}

	public MobileBaseBuilder addFixedWheel(DHParameterKinematics wheel) {
		if (wheel != null) {
			configureAppendage(wheel);
			mobileBase.getDrivable().add(wheel);
		}
		return this;
	}

	public MobileBaseBuilder addFixedWheelFromOptions(CSGDatabaseInstance db,String wheelType) {
		try {
			@SuppressWarnings("unchecked")
			HashMap<String, HashMap<String, Object>> options = (HashMap<String, HashMap<String, Object>>) ScriptingEngine
					.gitScriptRun(CSGDatabase.getInstance(),"https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git",
							"wheelOptions.json");

			if (options.containsKey(wheelType)) {
				HashMap<String, Object> values = options.get(wheelType);

				if (wheelType.toLowerCase().contains("fixed")) {
					String xmlContent = ScriptingEngine.codeFromGit(values.get("scriptGit").toString(),
							values.get("scriptFile").toString())[0];
					DHParameterKinematics newWheel = new DHParameterKinematics(null,
							IOUtils.toInputStream(xmlContent, "UTF-8"));
					return addFixedWheel(newWheel);
				} else {
					MobileBase base = RobotHelper.fileToRobot(db,values.get("scriptGit").toString(),
							values.get("scriptFile").toString());
					DHParameterKinematics newWheel = base.getDrivable().get(0);
					return addFixedWheel(newWheel);
				}
			}
		} catch (Exception e) {
			Log.error("Failed to add wheel from options: " + e.getMessage());
		}
		return this;
	}

	// Parallel group management
	public MobileBaseBuilder addParallelGroup(ParallelGroup group) {
		if (group != null) {
			// Note: Need to add parallel group support to MobileBase if not already present
			// mobileBase.getParallelGroups().add(group);
		}
		return this;
	}

	// Channel management methods
	public MobileBaseBuilder reserveDeviceChannel(String deviceName, int channel) {
		deviceChannelMap.computeIfAbsent(deviceName, k -> new HashMap<>()).put(channel, true);
		return this;
	}

	private void configureAppendage(DHParameterKinematics appendage) {
		// Set CAD engine if available
		String[] cadEngine = mobileBase.getGitCadEngine();
		if (cadEngine != null) {
			appendage.setGitCadEngine(cadEngine);
		}

		// Configure channels for all links
		for (LinkConfiguration conf : appendage.getLinkConfigurations()) {
			assignNextAvailableChannel(conf);
		}
	}

	private void assignNextAvailableChannel(LinkConfiguration conf) {
		// Try to find an available channel
		for (Map.Entry<String, Map<Integer, Boolean>> deviceEntry : deviceChannelMap.entrySet()) {
			String deviceName = deviceEntry.getKey();
			Map<Integer, Boolean> channels = deviceEntry.getValue();

			for (int i = 0; i < 48; i++) {
				if (!channels.containsKey(i)) {
					conf.setDeviceScriptingName(deviceName);
					conf.setHardwareIndex(i);
					channels.put(i, true);
					return;
				}
			}
		}

		// If no channels available, create a new device
		String newDeviceName = conf.getDeviceScriptingName() + "_new";
		conf.setDeviceScriptingName(newDeviceName);
		conf.setHardwareIndex(0);
		reserveDeviceChannel(newDeviceName, 0);
	}

	private DHParameterKinematics copyDHParameterKinematics(DHParameterKinematics source) throws Exception {
		// Create a copy by serializing and deserializing XML
		String xml = source.getXml();
		DHParameterKinematics copy = new DHParameterKinematics(null, IOUtils.toInputStream(xml, "UTF-8"));

		// Copy git engines
		if (source.getGitCadEngine() != null) {
			copy.setGitCadEngine(copyGitFile(source.getGitCadEngine(), gitURL));
		}
		if (source.getGitDhEngine() != null) {
			copy.setGitDhEngine(copyGitFile(source.getGitDhEngine(), gitURL));
		}

		return copy;
	}

	private String[] copyGitFile(String[] sourceGit, String targetGit) {
		return ScriptingEngine.copyGitFile(sourceGit[0], targetGit, sourceGit[1]);
	}

	public File getFile() throws Exception {
		return ScriptingEngine.fileFromGit(gitURL, mobileBase.getScriptingName() + ".xml");
	}

	public MobileBase build(CSGDatabaseInstance db) throws Exception {
		if (!mobileBase.isAvailable())
			mobileBase.connect();
		String filename = (xmlName != null) ? xmlName : mobileBase.getScriptingName();
		mobileBase.setGitSelfSource(new String[] { gitURL, filename });
		for (int i = 0; i < controllers.size(); i++) {
			AddRobotController con = controllers.get(i);
			for (VitaminLocation l : con.getVitamins(con.getName() + "_" + i)) {
				try {
					if (!mobileBase.hasVitamin(l))
						mobileBase.addVitamin(l);
				} catch (Exception ex) {
					com.neuronrobotics.sdk.common.Log.error(ex);;
				}
			}
		}
		for (int i = 0; i < limbs.size(); i++) {
			AddRobotLimb limb = limbs.get(i);
			if (mobileBase.getLimbByName(limb.getName()) == null) {
				TransformNR location = limb.getLocation();
				DHParameterKinematics kin = limb.getLimb().getLimb(db,limb.getName());
				kin.setRobotToFiducialTransform(location.copy());
				// TODO add the channel mapping here
				kin.connect();
				kin.zero();
				switch (limb.getLimb().getType()) {
				case arm:
				case flap:
				case hand:
				case head:
					mobileBase.getAppendages().add(kin);
					break;
				case leg:
					mobileBase.getLegs().add(kin);
					break;
				case steerable:
					mobileBase.getSteerable().add(kin);
					break;
				case wheel:
					mobileBase.getFixed().add(kin);
					break;
				default:
					throw new RuntimeException("Unknown limb type in builder! " + limb.getLimb().getType());
				}
			}
		}
		ArrayList<ModifyLimb > toRemove = new ArrayList<ModifyLimb>();
		for (int i = 0; i < mods.size(); i++) {
			ModifyLimb mod = mods.get(i);
			DHParameterKinematics kin = mod.getLimb();
			if (kin == null)
				continue;

			TransformNR base = mod.getBase();
			if (base != null) {
				//com.neuronrobotics.sdk.common.Log.debug("Base set to " + base);
				kin.setRobotToFiducialTransform(base);
			}
			if (mod.getTip() != null) {
				try {
					kin.setDesiredTaskSpaceTransform(mod.getTip(), 0);
				}catch(Exception ex) {
					com.neuronrobotics.sdk.common.Log.error(ex);;
					toRemove.add(mod);
				}
			}
		}
		mods.removeAll(toRemove);
		getCadManager().render();
		// Push to git
		ScriptingEngine.pushCodeToGit(gitURL, null, filename, mobileBase.getXml(), "Builder Write XML", true);
		return mobileBase;
	}

	public void addController(AddRobotController controller) {
		if (!controllers.contains(controller))
			getControllers().add(controller);
	}

	public void removeController(AddRobotController controller) {
		if (controllers.contains(controller))
			getControllers().remove(controller);
		for (int i = 0; i < controllers.size(); i++) {
			AddRobotController con = controllers.get(i);
			for (VitaminLocation l : con.getVitamins(con.getName() + "_" + i)) {
				try {
					mobileBase.removeVitamin(l);
				} catch (Exception ex) {
					com.neuronrobotics.sdk.common.Log.error(ex);;
				}
			}
		}
	}

	public ArrayList<AddRobotController> getControllers() {
		return controllers;
	}

	public ControllerFeatures getCapibilities() {
		ControllerFeatures test = new ControllerFeatures();
		for (AddRobotController c : controllers) {
			test.add(c.getController().getProvides());
			test.subtract(c.getController().getConsumes());
		}
		for (AddRobotLimb c : limbs) {
			test.add(c.getLimb().getProvides());
			test.subtract(c.getLimb().getConsumes());
		}
		return test;
	}

	public void addLimb(AddRobotLimb controller) {
		addLimb(controller, false);
	}

	public void addLimb(AddRobotLimb controller, boolean forceLoad) {
		LimbOption consumes = controller.getLimb();
		if (!checkOptionSupported(consumes) && !forceLoad) {
			throw new RuntimeException("Robot doesnt have enough resources to support " + controller.getLimb());
		}
		if (!getLimmbs().contains(controller))
			getLimmbs().add(controller);
	}

	public boolean checkOptionSupported(LimbOption consumes) {
		return getCapibilities().check(consumes.consumes);
	}

	public void addModification(ModifyLimb modifyLimb) {
		if (!mods.contains(modifyLimb))
			mods.add(modifyLimb);
	}

	public void removeModification(ModifyLimb modifyLimb) {
		if (mods.contains(modifyLimb))
			mods.remove(modifyLimb);
	}

	public void removeLimb(AddRobotLimb controller) {
		if (getLimmbs().contains(controller))
			getLimmbs().remove(controller);
		mobileBase.deleteLimbByName(controller.getName());
	}

	public ArrayList<AddRobotLimb> getLimmbs() {
		return limbs;
	}

	public MobileBaseCadManager getCadManager() {
		MobileBaseCadManager mobileBaseCadManager = MobileBaseCadManager.get(db,mobileBase);
		mobileBaseCadManager.setAutoRegen(false);
		mobileBaseCadManager.setConfigurationViewerMode(false);
		return mobileBaseCadManager;
	}

}