package com.neuronrobotics.bowlerstudio.threed;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;

import com.neuronrobotics.manifold3d.CSGManifold3d;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ISlice;
import eu.mihosoft.vrl.v3d.Slice;

/** Runs export diagnostics with the application's default boolean backend. */
public abstract class ManifoldExportTestSupport {
	private CSG.OptType previousMode;
	private CSGManifold3d previousBridge;
	private ISlice previousSlice;

	@Before
	public void selectApplicationBooleanBackend() {
		previousMode = CSG.getDefaultOptionType();
		previousBridge = CSG.getManifold();
		previousSlice = Slice.getSliceEngine();
		CSG.setDefaultOptType(CSG.OptType.Manifold3d);
		assertEquals(CSG.OptType.Manifold3d, CSG.getDefaultOptionType());
	}

	@After
	public void restoreBooleanBackend() {
		CSGManifold3d diagnosticBridge = CSG.getManifold();
		CSG.setDefaultOptType(previousMode);
		CSG.setManifold(previousBridge);
		Slice.setSliceEngine(previousSlice);
		if (diagnosticBridge != null && diagnosticBridge != previousBridge)
			diagnosticBridge.close();
	}
}
