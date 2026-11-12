package com.neuronrobotics.bowlerstudio.creature;

import com.neuronrobotics.bowlerstudio.scripting.RobotHelper;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleOperation;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotController;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.AddRobotLimb;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.ModifyLimb;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.addons.kinematics.parallel.ParallelGroup;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

import org.apache.commons.io.IOUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobileBaseBuilder {
	// ArrayList<AddRobotController> controllers = new
	// ArrayList<AddRobotController>();
	// ArrayList<AddRobotLimb> limbs = new ArrayList<AddRobotLimb>();
	// ArrayList<ModifyLimb> mods = new ArrayList<ModifyLimb>();
	ArrayList<CaDoodleOperation> operations = new ArrayList<CaDoodleOperation>();
	private MobileBase mobileBase;
	private String gitURL;
	private String xmlName = null;

	// Channel management
	private Map<String, Map<Integer, Boolean>> deviceChannelMap = new HashMap<>();
	private CSGDatabaseInstance db;
	private String name;

	// Constructor for creating a new MobileBase
	public MobileBaseBuilder(CSGDatabaseInstance db, String gitURL, String name) {
		this.db = db;
		this.gitURL = gitURL;
		this.name = name;
		clear(null);
	}

	// Constructor for extending an existing MobileBase
	public MobileBaseBuilder(CSGDatabaseInstance db, MobileBase existingBase) {
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
			Log.error("Failed to copy from source MobileBase:");
			Log.error(e);
		}
		return this;
	}

	// // Constructor for extending an existing MobileBase with new name
	// public MobileBaseBuilder(String gitURL, MobileBase existingBase, String
	// newName) {
	// this.gitURL = gitURL;
	// this.mobileBase = existingBase;
	// this.mobileBase.setScriptingName(newName);
	// initializeChannelMap();
	// scanExistingChannels();
	// }

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
		mobileBase.setGitCadEngine(new String[]{gitURL, filename});
		return this;
	}

	public MobileBaseBuilder setGitWalkingEngine(String gitURL, String filename) {
		mobileBase.setGitWalkingEngine(new String[]{gitURL, filename});
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
			MobileBaseLoader.setDefaultDhParameterKinematics(db, newLeg);

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
			MobileBaseLoader.setDefaultDhParameterKinematics(db, newArm);

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
			MobileBaseLoader.setDefaultDhParameterKinematics(db, newWheel);

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

	public MobileBaseBuilder addFixedWheelFromOptions(CSGDatabaseInstance db, String wheelType) {
		try {
			@SuppressWarnings("unchecked")
			HashMap<String, HashMap<String, Object>> options = (HashMap<String, HashMap<String, Object>>) ScriptingEngine
					.gitScriptRun(CSGDatabase.getInstance(),
							"https://github.com/CommonWealthRobotics/BowlerStudioExampleRobots.git",
							"wheelOptions.json");

			if (options.containsKey(wheelType)) {
				HashMap<String, Object> values = options.get(wheelType);

				if (wheelType.toLowerCase().contains("fixed")) {
					String xmlContent = ScriptingEngine.codeFromGit(values.get("scriptGit").toString(),
							values.get("scriptFile").toString())[0];
					DHParameterKinematics newWheel = new DHParameterKinematics(null,
							IOUtils.toInputStream(xmlContent, "UTF-8"));
					MobileBaseLoader.setDefaultDhParameterKinematics(db, newWheel);

					return addFixedWheel(newWheel);
				} else {
					MobileBase base = RobotHelper.fileToRobot(db, values.get("scriptGit").toString(),
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
		String xml = source.getAppendageXml();
		DHParameterKinematics copy = new DHParameterKinematics(null, IOUtils.toInputStream(xml, "UTF-8"));

		// Copy git engines
		if (source.getGitCadEngine() != null) {
			copy.setGitCadEngine(copyGitFile(source.getGitCadEngine(), gitURL));
		}
		if (source.getGitDhEngine() != null) {
			copy.setGitDhEngine(copyGitFile(source.getGitDhEngine(), gitURL));
		}
		MobileBaseLoader.setDefaultDhParameterKinematics(db, copy);

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
		this.db = db;
		String filename = (xmlName != null) ? xmlName : mobileBase.getScriptingName();
		mobileBase.setGitSelfSource(new String[]{gitURL, filename});
		ArrayList<ModifyLimb> toRemove = new ArrayList<ModifyLimb>();
		// undo/redo churn reorders the operations list, so apply them in
		// timeline order: the limb must exist before its modifications run
		ArrayList<CaDoodleOperation> ordered = new ArrayList<CaDoodleOperation>(operations);
		ordered.sort((a, b) -> Integer.compare(getOperationIndex(a), getOperationIndex(b)));
		for (int i = 0; i < ordered.size(); i++) {
			CaDoodleOperation op = ordered.get(i);
			if (AddRobotController.class.isInstance(op)) {
				AddRobotController con = (AddRobotController) op;
				for (VitaminLocation l : con.getVitamins(con.getName() + "_" + i)) {
					try {
						if (!mobileBase.hasVitamin(l))
							mobileBase.addVitamin(l);
					} catch (Exception ex) {
						com.neuronrobotics.sdk.common.Log.error(ex);

					}
				}
			}
			if (AddRobotLimb.class.isInstance(op)) {
				AddRobotLimb limb = (AddRobotLimb) op;
				if (mobileBase.getLimbByName(limb.getName()) == null) {
					TransformNR location = limb.getLocation();
					DHParameterKinematics kin = limb.getLimb().getLimb(db, limb.getName());
					TransformNR existing = kin.getRobotToFiducialTransform();
					kin.setRobotToFiducialTransform(location.copy().times(existing));
					// TODO add the channel mapping here
					kin.connect();
					kin.zero();
					// a redo after an undo re-creates the limb object while
					// the timeline cache still welds its CAD to the destroyed
					// object's render Affines; hand the Affines over or every
					// later undo/redo moves a mesh nothing displays
					transferRenderAffines(limb.getKinematics(), kin);
					limb.setKinematics(kin);
					switch (limb.getLimb().getType()) {
						case arm :
						case flap :
						case hand :
						case head :
							mobileBase.getAppendages().add(kin);
							break;
						case leg :
							mobileBase.getLegs().add(kin);
							break;
						case steerable :
							mobileBase.getSteerable().add(kin);
							break;
						case wheel :
							mobileBase.getFixed().add(kin);
							break;
						default :
							throw new RuntimeException("Unknown limb type in builder! " + limb.getLimb().getType());
					}
				}
			}
			if (ModifyLimb.class.isInstance(op))
				applyModification((ModifyLimb) op, ordered);
		}
		operations.removeAll(toRemove);
		getCadManager().render();

		// Push to git
		ScriptingEngine.pushCodeToGit(gitURL, null, filename, mobileBase.getXml(), "Builder Write XML", true);
		return mobileBase;
	}

	// Moves the render Affines of a destroyed limb onto its replacement so
	// cached CSGs welded to them keep tracking the live limb
	private void transferRenderAffines(DHParameterKinematics from, DHParameterKinematics to) {
		if (from == null || to == null)
			return;
		if (from.getRootListener() != null && to.getRootListener() == null)
			to.setRootListener(from.getRootListener());
		if (from.getNumberOfLinks() != to.getNumberOfLinks())
			return;
		for (int i = 0; i < to.getNumberOfLinks(); i++) {
			DHLink oldLink = from.getChain().getLinks().get(i);
			DHLink newLink = to.getChain().getLinks().get(i);
			if (oldLink.getListener() != null && newLink.getListener() == null)
				newLink.setListener(oldLink.getListener());
			AbstractLink oldAbstract = from.getAbstractLink(i);
			AbstractLink newAbstract = to.getAbstractLink(i);
			if (oldAbstract.getGlobalPositionListener() != null && newAbstract.getGlobalPositionListener() == null)
				newAbstract.setGlobalPositionListener(oldAbstract.getGlobalPositionListener());
		}
	}

	// Rebuilds the limb state from the modification timeline. The cursor
	// decides what each entry carries: entries inside it apply their
	// committed values, the first entry at or after it restores its stored
	// pre-commit snapshot, and later undone entries stay silent.
	// Position-based, because the limb-handle UI re-commits the same op
	// object and one object can sit at two timeline indices; the stored
	// snapshot carries the as-added state when nothing was committed before
	private void applyModification(ModifyLimb first, ArrayList<CaDoodleOperation> ordered) {
		String limbName = first.getLimb() == null ? null : first.getLimb().getScriptingName();
		if (limbName == null)
			return;
		DHParameterKinematics kin = null;
		boolean undoneRestored = false;
		int cursor = -1;
		if (first.getCaDoodleFile() != null)
			cursor = first.getCaDoodleFile().getCurrentIndex();
		for (int i = 0; i < ordered.size(); i++) {
			CaDoodleOperation op = ordered.get(i);
			if (!ModifyLimb.class.isInstance(op))
				continue;
			ModifyLimb mod = (ModifyLimb) op;
			if (mod.getLimb() == null || !mod.getLimb().getScriptingName().contentEquals(limbName))
				continue;
			if (kin == null)
				kin = mod.getLimb();
			int opIndex = getOperationIndex(op);
			// undone means past the cursor, or sitting exactly on it with the
			// undo flag raised (back() steps the cursor down before undo()
			// runs). An entry on the cursor with the flag clear is the op
			// being committed right now: process() applies it before the
			// cursor increments
			boolean committed = opIndex != Integer.MAX_VALUE
					&& (opIndex < cursor || (opIndex == cursor && !mod.isUndo()));
			if (committed) {
				applyValues(kin, mod, mod.getBase(), mod.getTip());
				undoneRestored = false;
			} else if (!undoneRestored) {
				// first undone entry: hand the limb back to where the last
				// committed entry left it: the entry's stored snapshot was
				// captured at its commit and holds exactly that state, which
				// the committed values of earlier entries cannot reproduce
				// (an entry may move only the tip while its predecessors
				// moved only the base)
				applyValues(kin, mod, mod.getBase(), mod.getTip());
				undoneRestored = true;
			}
		}
	}

	private void applyValues(DHParameterKinematics kin, ModifyLimb mod, TransformNR base, double[] tip) {
		if (base != null)
			kin.setRobotToFiducialTransform(base);
		if (tip != null) {
			try {
				double best = kin.getBestTime(tip);
				kin.setDesiredJointSpaceVector(tip, best);
			} catch (Exception ex) {
				com.neuronrobotics.sdk.common.Log.error("Failed to apply set point on " + kin.getScriptingName());
				com.neuronrobotics.sdk.common.Log.error(ex);
			}
		}
	}

	// timeline position of an operation, unknown operations sort last so limbs
	// are always created before any modification that references them
	private int getOperationIndex(CaDoodleOperation op) {
		try {
			return op.getCaDoodleFile().opToIndex(op);
		} catch (Exception e) {
			return Integer.MAX_VALUE;
		}
	}

	public void addController(AddRobotController controller) {
		if (!operations.contains(controller))
			operations.add(controller);
	}

	public void removeController(AddRobotController controller) {
		if (operations.contains(controller)) {
			for (int i = 0; i < operations.size(); i++) {
				CaDoodleOperation op = operations.get(i);
				if (AddRobotController.class.isInstance(op)) {
					AddRobotController con = (AddRobotController) op;
					for (VitaminLocation l : con.getVitamins(con.getName() + "_" + i)) {
						try {
							mobileBase.removeVitamin(l);
						} catch (Exception ex) {
							com.neuronrobotics.sdk.common.Log.error(ex);;
						}
					}
				}
			}
			operations.remove(controller);
		}
	}

	public ArrayList<AddRobotController> getControllers() {
		ArrayList<AddRobotController> controllers = new ArrayList<AddRobotController>();
		for (int i = 0; i < operations.size(); i++) {
			CaDoodleOperation op = operations.get(i);
			if (AddRobotController.class.isInstance(op)) {
				AddRobotController c = (AddRobotController) op;
				controllers.add(c);
			}
		}
		return controllers;
	}

	public ControllerFeatures getCapibilities() {
		ControllerFeatures test = new ControllerFeatures();
		for (int i = 0; i < operations.size(); i++) {
			CaDoodleOperation op = operations.get(i);
			if (AddRobotController.class.isInstance(op)) {
				AddRobotController c = (AddRobotController) op;

				test.add(c.getController().getProvides());
				test.subtract(c.getController().getConsumes());
			}
			if (AddRobotLimb.class.isInstance(op)) {
				AddRobotLimb c = (AddRobotLimb) op;

				test.add(c.getLimb().getProvides());
				test.subtract(c.getLimb().getConsumes());
			}
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
		if (operations.contains(controller)) {
			DHParameterKinematics kin = mobileBase.getLimbByName(controller.getName());
			switch (controller.getLimb().getType()) {
				case arm :
				case flap :
				case hand :
				case head :
					mobileBase.getAppendages().remove(kin);
					break;
				case leg :
					mobileBase.getLegs().remove(kin);
					break;
				case steerable :
					mobileBase.getSteerable().remove(kin);
					break;
				case wheel :
					mobileBase.getFixed().remove(kin);
					break;
				default :
					throw new RuntimeException("Unknown limb type in builder! " + controller.getLimb().getType());
			}
		}
		operations.add(controller);
	}

	public boolean checkOptionSupported(LimbOption consumes) {
		return getCapibilities().check(consumes.consumes);
	}

	public void addModification(ModifyLimb modifyLimb) {
		if (!operations.contains(modifyLimb))
			operations.add(modifyLimb);
	}

	public void removeModification(ModifyLimb modifyLimb) {
		if (operations.contains(modifyLimb))
			operations.remove(modifyLimb);
	}

	public void removeLimb(AddRobotLimb controller) {
		if (operations.contains(controller))
			operations.remove(controller);
		mobileBase.deleteLimbByName(controller.getName());
	}

	public ArrayList<AddRobotLimb> getLimbs() {
		ArrayList<AddRobotLimb> limbs = new ArrayList<AddRobotLimb>();
		for (int i = 0; i < operations.size(); i++) {
			CaDoodleOperation op = operations.get(i);
			if (AddRobotLimb.class.isInstance(op)) {
				AddRobotLimb c = (AddRobotLimb) op;
				limbs.add(c);
			}
		}
		return limbs;

	}

	public MobileBaseCadManager getCadManager() {
		MobileBaseCadManager mobileBaseCadManager = MobileBaseCadManager.get(db, mobileBase);
		mobileBaseCadManager.setAutoRegen(false);
		mobileBaseCadManager.setConfigurationViewerMode(false);
		return mobileBaseCadManager;
	}

	public void clear(CaDoodleOperation from) {
		if (from == null) {
			if (mobileBase != null) {
				mobileBase.disconnect();
				try {
					MobileBaseCadManager cm = MobileBaseCadManager.get(db, mobileBase);
					cm.clear();
				} catch (Exception ex) {
					Log.error(ex);
				}
			}
			this.mobileBase = new MobileBase();
			operations.clear();
		} else {
			while (operations.size() > 0) {

				CaDoodleOperation op = operations.remove(operations.size() - 1);
				if (AddRobotLimb.class.isInstance(op)) {
					AddRobotLimb c = (AddRobotLimb) op;
					DHParameterKinematics kin = c.getKinematics();
					switch (c.getLimb().getType()) {
						case arm :
						case flap :
						case hand :
						case head :
							mobileBase.getAppendages().remove(kin);
							break;
						case leg :
							mobileBase.getLegs().remove(kin);
							break;
						case steerable :
							mobileBase.getSteerable().remove(kin);
							break;
						case wheel :
							mobileBase.getFixed().remove(kin);
							break;
						default :
							throw new RuntimeException("Unknown limb type in builder! " + c.getLimb().getType());
					}
				}
				if (from == op) {
					break;
				}
			}
		}
		this.mobileBase.setScriptingName(name);
		initializeChannelMap();
		if (MobileBaseCadManager.exists(mobileBase)) {
			MobileBaseCadManager.get(db, mobileBase).clear();
		}
	}

}
