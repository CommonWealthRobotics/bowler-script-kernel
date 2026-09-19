package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;
import eu.mihosoft.vrl.v3d.PrepForManufacturing;
import eu.mihosoft.vrl.v3d.parametrics.IParametric;
import javafx.scene.transform.Affine;

public class Xor extends AbstractAddFrom {
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();
	@Expose(serialize = true, deserialize = true)
	public String operationID = null;

	@Override
	public File getFile() throws NoSuchFileException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getType() {
		return "Xor";
	}

	public String getOperationID() {
		if (operationID == null)
			operationID = RandomStringFactory.generateRandomString();
		return operationID;
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		ArrayList<CSG> back = new ArrayList<>();
		ArrayList<CSG> toXor = new ArrayList<>();
		boolean noscale = false;
		Affine manip = null;
		boolean nomove = false;
		PrepForManufacturing mfg = null;
		String mobileBase = null;
		back.addAll(incoming);
		for (String s : names){
			 for (CSG c : incoming) {
				if (c.getName().contentEquals(s)) {
					if (c.isNoScale())
						noscale = true;
					if (c.isMotionLock())
						nomove = true;
					Optional<String> mobileBaseName = c.getMobileBaseName();
					if (mobileBaseName.isPresent()) {
						if (mobileBase == null)
							mobileBase = mobileBaseName.get();
						if (!mobileBase.contentEquals(mobileBaseName.get())) {
							continue;// skip grouping any item that is of a different mobile base;
						}
					}
					if (c.hasManipulator()) {
						try {
							manip = c.getManipulator();
							c = c.transformed(TransformFactory.nrToCSG(TransformFactory.affineToNr(manip)))
									.syncProperties(getCaDoodleFile().getCsgDBinstance(), c)
									.setRegenerate(c.getRegenerate()).setName(s);

						} catch (MissingManipulatorException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					if (c.hasManufacturing()) {
						mfg = c.getManufacturing();
					}
					toXor.add(c);
					back.remove(c);
				} 
			}
		}
		CSG intersection = Group.intersect(toXor);
		for (int i = 0; i < toXor.size(); i++) {
			CSG f = toXor.get(i);
			CSG result = f.difference(intersection);
			if (manip != null) {
				result = result.transformed(TransformFactory.nrToCSG(TransformFactory.affineToNr(manip).inverse()));
				result.setManipulator(manip);
			}
			if (mfg != null)
				result.setManufacturing(mfg);
			if (mobileBase != null)
				result.setMobileBaseName(mobileBase);
			HashMap<String, IParametric> mapOfparametrics = result
					.getMapOfparametrics(getCaDoodleFile().getCsgDBinstance());
			if (mapOfparametrics != null)
				mapOfparametrics.clear();
			result.setName(getOperationID() + "_" + i);
			result.setUserDefinedName("Xor " + i);
			result.setNoScale(noscale);
			result.setIsMotionLock(nomove);
			result.setIsAlwaysShow(false);
			addNameInThisOperation(result.getName());
			back.add(result);
		}

		return back;
	}

	public List<String> getNames() {
		return names;
	}

	public Xor setNames(List<String> names) {
		this.names = names;
		return this;
	}

}
