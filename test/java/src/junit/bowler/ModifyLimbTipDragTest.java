package junit.bowler;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleOperation;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.ModifyLimb;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;

public class ModifyLimbTipDragTest {
	@Test
	public void releaseKeepsLastDragPoseIncludingWhenFirstSampleIsTheStartingPose() throws Exception {
		try (Fixture f = new Fixture()) {
			ModifyLimb drag = f.drag(0, 20, 65);
			f.commit(drag);
			f.assertTarget(65);
			f.file.back();
			f.assertTarget(0);
			f.file.forward();
			f.assertTarget(65);
		}
	}

	@Test
	public void laterDragDoesNotChangeEarlierCommitOrItsUndoSnapshot() throws Exception {
		try (Fixture f = new Fixture()) {
			ModifyLimb first = f.drag(10, 40);
			f.commit(first);
			f.assertTarget(40);
			ModifyLimb second = f.drag(40, 55, -25);
			f.commit(second);
			f.assertTarget(-25);
			assertArrayEquals(new double[]{40}, first.getTip(), 1e-6);
			f.file.back();
			f.assertTarget(40);
			f.file.back();
			f.assertTarget(0);
			f.file.forward();
			f.assertTarget(40);
			f.file.forward();
			f.assertTarget(-25);
		}
	}

	private static class Fixture implements AutoCloseable {
		final CaDoodleFile file = new CaDoodleFile();
		final Path output;
		final DHParameterKinematics limb;
		final MobileBase base = new MobileBase();
		final CSG part = new Cube(1).toCSG().setName("tip-part");

		Fixture() throws Exception {
			Files.createDirectories(new File("build").toPath());
			output = Files.createTempDirectory(new File("build").toPath(), "tip-drag-");
			file.setSelf(output.resolve("history.doodle").toFile());
			Element template = (Element) DocumentBuilderFactory.newInstance().newDocumentBuilder()
					.parse(new File("testStepFiles/limb-redo/robot.xml")).getElementsByTagName("drivable").item(0);
			for (String tag : new String[]{"cadEngine", "kinematics"}) {
				Node engine = template.getElementsByTagName(tag).item(0);
				engine.getParentNode().removeChild(engine);
			}
			limb = new DHParameterKinematics(null, template);
			limb.setScriptingName("tip-limb");
			// The fixture wheel is one rotary joint with a 23 mm radial link
			limb.setInverseSolver(
					(target, joints, chain) -> new double[]{Math.toDegrees(Math.atan2(target.getY(), target.getX()))});
			limb.connect();
			limb.setDesiredJointSpaceVector(new double[]{0}, 0);
			base.setGitSelfSource(new String[]{output.toAbsolutePath().toString(), "robot.xml"});
			base.getFixed().add(limb);
			MobileBaseCadManager renderer = mock(MobileBaseCadManager.class);
			MobileBaseBuilder builder = new MobileBaseBuilder(file.getCsgDBinstance(), base) {
				@Override
				public MobileBaseCadManager getCadManager() {
					return renderer;
				}
			};
			builder.setXmlName("robot.xml");
			file.getRobots().put("robot", builder);
			part.setLimbName(limb.getScriptingName());
			part.setMobileBaseName("robot");
			file.getOperations().add(new CaDoodleOperation() {
				@Override
				public String getType() {
					return "local test limb";
				}

				@Override
				public List<CSG> process(List<CSG> incoming) {
					return Collections.singletonList(part);
				}

				@Override
				public List<String> getNamesAddedInThisOperation() {
					return Collections.singletonList(part.getName());
				}
			});
			file.setCurrentIndex(1);
		}

		ModifyLimb drag(double... samples) throws Exception {
			ModifyLimb operation = new ModifyLimb().setLimb(limb).setNames(Collections.singletonList(part.getName()));
			operation.setUndo(true);
			double[] start = limb.getCurrentJointSpaceTarget().clone();
			for (double angle : samples) {
				TransformNR target = limb.forwardOffset(limb.forwardKinematics(new double[]{angle}));
				limb.setDesiredTaskSpaceTransform(target, 0);
				assertTarget(angle);
				operation.setTip(target);
				assertArrayEquals("The drag anchor must remain at the pre-drag pose", start, operation.getTip(), 1e-6);
			}
			return operation;
		}

		void commit(ModifyLimb operation) {
			operation.setCaDoodleFile(file);
			file.getOperations().add(operation);
			operation.setUndo(false);
			operation.process(Collections.singletonList(part));
			file.setCurrentIndex(file.getCurrentIndex() + 1);
		}

		void assertTarget(double angle) {
			assertArrayEquals(new double[]{angle}, limb.getCurrentJointSpaceTarget(), 1e-6);
		}

		@Override
		public void close() throws Exception {
			limb.disconnect();
			base.disconnect();
			try (Stream<Path> paths = Files.walk(output)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList()))
					Files.deleteIfExists(path);
			}
		}
	}
}
