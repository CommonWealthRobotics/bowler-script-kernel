package junit.bowler;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.mockito.Mockito;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.IOnMobileBaseRenderChange;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import javafx.scene.transform.Affine;
import javafx.scene.shape.MeshView;
import javafx.geometry.Point3D;
import javafx.scene.transform.MatrixType;

/** Executes actual render pose sampling and transform calculation without the UI thread. */
final class OfflineLimbRenderProbe {
	private static final Map<Affine, MeshView> cachedMeshes = new IdentityHashMap<>();

	static void clear() {
		cachedMeshes.clear();
	}

	static void assertSharedAffines(DHParameterKinematics oldLimb, DHParameterKinematics live) {
		for (int i = 0; i < oldLimb.getNumberOfLinks(); i++) {
			Object cached = oldLimb.getChain().getLinks().get(i).getListener();
			if (cached != null)
				assertSame("Cached CAD must retain the rendered affine", cached,
						live.getChain().getLinks().get(i).getListener());
		}
	}

	static void assertDetached(MobileBase base, DHParameterKinematics limb) {
		AtomicInteger events = new AtomicInteger();
		Thread caller = Thread.currentThread();
		IOnMobileBaseRenderChange listener = () -> {
			if (Thread.currentThread() == caller)
				events.incrementAndGet();
		};
		base.addIOnMobileBaseRenderChange(listener);
		try {
			limb.firePoseUpdate();
			assertEquals("Detached limb must not trigger rendering", 0, events.get());
		} finally {
			base.removeIOnMobileBaseRenderChange(listener);
		}
	}

	static void inspect(String step, MobileBase base, DHParameterKinematics limb, List<String> errors) {
		try {
			MobileBaseCadManager renderer = Mockito.mock(MobileBaseCadManager.class, Mockito.CALLS_REAL_METHODS);
			HashMap<DHParameterKinematics, double[]> poses = new HashMap<>();
			HashMap<Affine, TransformNR> transforms = new HashMap<>();
			Method sample = MobileBaseCadManager.class.getDeclaredMethod("loadJointPose", MobileBase.class,
					HashMap.class);
			sample.setAccessible(true);
			AtomicInteger events = new AtomicInteger();
			Thread caller = Thread.currentThread();
			IOnMobileBaseRenderChange listener = () -> {
				// Ignore unrelated asynchronous PID pulses; inspect the injected feedback event;
				if (Thread.currentThread() != caller)
					return;
				events.incrementAndGet();
				try {
					sample.invoke(renderer, base, poses);
				} catch (Exception ex) {
					throw new AssertionError(ex);
				}
			};
			base.addIOnMobileBaseRenderChange(listener);
			try {
				limb.firePoseUpdate();
			} finally {
				base.removeIOnMobileBaseRenderChange(listener);
			}
			if (!poses.containsKey(limb)) {
				errors.add(step + " no render pose event from live limb " + limb.getScriptingName());
				return;
			}
			assertEquals("One feedback update must produce one render event", 1, events.get());
			Method update = MobileBaseCadManager.class.getDeclaredMethod("updateMobileBase", MobileBase.class,
					TransformNR.class, HashMap.class, HashMap.class);
			update.setAccessible(true);
			update.invoke(renderer, base, base.getFiducialToGlobalTransform(), transforms, poses);
			ArrayList<TransformNR> expected = limb.getChain().getChain(poses.get(limb));
			for (int i = 0; i < expected.size(); i++) {
				Affine actual = (Affine) limb.getChain().getLinks().get(i).getListener();
				MeshView mesh = cachedMeshes.computeIfAbsent(actual, affine -> {
					MeshView node = new MeshView();
					node.getTransforms().add(affine);
					return node;
				});
				TransformNR wanted = expected.get(i);
				TransformNR rendered = transforms.get(actual);
				double[] matrix = new double[12];
				for (int row = 0; row < 3; row++)
					for (int col = 0; col < 4; col++)
						matrix[row * 4 + col] = rendered.getMatrixTransform().get(row, col);
				actual.setToTransform(matrix, MatrixType.MT_3D_3x4, 0);
				Point3D point = mesh.localToParent(1, 2, 3);
				for (int row = 0; row < 3; row++) {
					double coordinate = wanted.getMatrixTransform().get(row, 3);
					for (int col = 0; col < 3; col++)
						coordinate += wanted.getMatrixTransform().get(row, col) * (col + 1);
					assertEquals("Cached mesh follows rendered joint", coordinate,
							new double[]{point.getX(), point.getY(), point.getZ()}[row], 1e-5);
				}
				for (int row = 0; row < 3; row++)
					for (int col = 0; col < 4; col++)
						if (!Double.isFinite(wanted.getMatrixTransform().get(row, col))
								|| !Double.isFinite(rendered.getMatrixTransform().get(row, col))
								|| Math.abs(wanted.getMatrixTransform().get(row, col)
										- rendered.getMatrixTransform().get(row, col)) > 1e-5) {
							errors.add(step + " rendered " + limb.getScriptingName() + " joint " + i + " expected "
									+ wanted + " actual " + rendered);
							return;
						}
			}
		} catch (Exception ex) {
			throw new AssertionError("Real render computation failed", ex);
		}
	}
}
