package com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.AbstractAddFrom;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ICadoodleOperationUndo;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;

public class ModifyLimb extends AbstractAddFrom implements ICadoodleOperationUndo {
	@Expose(serialize = true, deserialize = true)
	String limbName;
	@Expose(serialize = true, deserialize = true)
	private TransformNR base = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR tip = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR elbow = null;

	@Expose(serialize = true, deserialize = true)
	boolean undo = false;

	@Expose(serialize = true, deserialize = true)
	private TransformNR basePrevious = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR tipPrevious = null;
	@Expose(serialize = true, deserialize = true)
	private TransformNR elbowPrevious = null;
	@Expose(serialize = true, deserialize = true)
	private List<String> names;

	private String builderName;
	private DHParameterKinematics newLimb;

	@Override
	public void pruneCleanup() {
		if (getBuilderName() != null) {
			MobileBaseBuilder builder = getRobots().get(getBuilderName());
			undo();
			builder.removeModification(this);
		}
	}

	public String getBuilderName() {
		return builderName;
	}

	public void setBuilderName(String builderName) {
		this.builderName = builderName;
	}

	@Override
	public String getType() {
		return "ModifyLimb";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		if (names == null)
			throw new RuntimeException("Names can not be null");
		nameIndex = 0;
		if (builderName == null)
			setBuilderName(getBuilder(names, incoming));
		limbName = getLimbName(names, incoming);

		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		if (getBuilderName() != null && limbName != null) {
			MobileBaseBuilder builder = getRobots().get(getBuilderName());
			builder.addModification(this);
			if (getLimb() == null)
				setLimb(builder.getMobileBase().getLimbByName(limbName));
			if (getLimb() == null)
				throw new RuntimeException("Failed to create a limb!");
			redo();
			MobileBaseCadManager manager = builder.getCadManager();
			if (elbow != null) {
				ArrayList<CSG> limbCad = manager.generateCad(getCaDoodleFile().getCsgDBinstance(), getLimb());
				for (CSG c : incoming) {
					Optional<String> limbName2 = c.getLimbName();
					if (limbName2.isPresent())
						if (limbName2.get().contains(limbName)) {
							back.remove(c);
						}
				}
				for (CSG c : limbCad) {
					c.setName(getOrderedName());
					c.setLimbName(limbName);
					c.setMobileBaseName(getBuilderName());
					c.setNoScale(true);
					c.setIsMotionLock(true);
					back.add(c);
				}
			}
			manager.render();
		} else {
			throw new RuntimeException("Failed to find limb: " + limbName + " or builder: " + builderName);
		}
		return back;
	}

	@Override
	public File getFile() throws NoSuchFileException {
		throw new NoSuchFileException("");
	}

	/**
	 * @return the base
	 */
	public TransformNR getBase() {
		return !isUndo() ? base : basePrevious;
	}

	/**
	 * @param base
	 *            the base to set
	 */
	public ModifyLimb setBase(TransformNR base) {
		this.base = base;
		return this;
	}

	/**
	 * @return the tip
	 */
	public TransformNR getTip() {
		return !isUndo() ? tip : tipPrevious;
	}

	/**
	 * @param tip
	 *            the tip to set
	 */
	public ModifyLimb setTip(TransformNR tip) {
		this.tip = tip;
		return this;
	}

	/**
	 * @return the elbow
	 */
	public TransformNR getElbow() {
		return !isUndo() ? elbow : elbowPrevious;
	}

	/**
	 * @param elbow
	 *            the elbow to set
	 */
	public ModifyLimb setElbow(TransformNR elbow) {
		this.elbow = elbow;
		return this;
	}

	public ModifyLimb setNames(List<String> names) {
		this.names = names;
		return this;
	}

	/**
	 * @return the newLimb
	 */
	public DHParameterKinematics getLimb() {
		return newLimb;
	}

	/**
	 * @param newLimb
	 *            the newLimb to set
	 */
	public ModifyLimb setLimb(DHParameterKinematics newLimb) {
		this.newLimb = newLimb;
		basePrevious = newLimb.getRobotToFiducialTransform().copy();
		tipPrevious = newLimb.getCurrentTaskSpaceTransform().copy();
		return this;
	}

	@Override
	public void undo() {
		MobileBaseBuilder builder = getRobots().get(getBuilderName());
		setUndo(true);
		// com.neuronrobotics.sdk.common.Log.debug("Undo ModifyLimb");
		try {
			builder.build(getDb());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
	}

	@Override
	public void redo() {
		MobileBaseBuilder builder = getRobots().get(getBuilderName());
		setUndo(false);
		// com.neuronrobotics.sdk.common.Log.debug("Redo ModifyLimb");
		try {
			builder.build(getDb());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			com.neuronrobotics.sdk.common.Log.error(e);
		}
	}

	/**
	 * @return the undo
	 */
	public boolean isUndo() {
		return undo;
	}

	/**
	 * @param undo
	 *            the undo to set
	 */
	public void setUndo(boolean undo) {
		this.undo = undo;
	}

}
