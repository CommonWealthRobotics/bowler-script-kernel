package junit.bowler;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleFile;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.CaDoodleOperation;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ICaDoodleStateUpdate;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.ICadoodleOperationUndo;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.image.WritableImage;

public class HistoryUpdateOrderingTest {
	@Test
	public void undoPublishesRestoredStateBeforeSuggestingSave() {
		Fixture f = new Fixture();
		f.file.back();
		assertEquals(Arrays.asList("undo:2", "update:2:10.0:20.0", "save:2:10.0:20.0"), f.events);
	}

	@Test
	public void redoPublishesRestoredStateBeforeSuggestingSave() {
		Fixture f = new Fixture();
		f.file.back();
		f.events.clear();
		f.file.forward();
		assertEquals(Arrays.asList("redo:3", "update:3:30.0:60.0", "save:3:30.0:60.0"), f.events);
	}

	@Test
	public void repeatedUndoAndRedoNeverPublishThePreviousPose() {
		Fixture f = new Fixture();
		f.file.back();
		f.file.back();
		f.file.forward();
		f.file.forward();
		assertEquals(Arrays.asList("undo:2", "update:2:10.0:20.0", "save:2:10.0:20.0", "undo:1", "update:1:0.0:0.0",
				"save:1:0.0:0.0", "redo:2", "update:2:10.0:20.0", "save:2:10.0:20.0", "redo:3", "update:3:30.0:60.0",
				"save:3:30.0:60.0"), f.events);
	}

	@Test
	public void timelineJumpsPublishOnlyTheFinalRestoredState() {
		Fixture f = new Fixture();
		f.file.moveToOpIndex(0);
		f.file.moveToOpIndex(2);
		assertEquals(Arrays.asList("undo:2", "undo:1", "update:1:0.0:0.0", "save:1:0.0:0.0", "redo:2", "redo:3",
				"update:3:30.0:60.0", "save:3:30.0:60.0"), f.events);
	}

	private static class Fixture implements ICaDoodleStateUpdate {
		final CaDoodleFile file = new CaDoodleFile();
		final List<String> events = new ArrayList<>();
		TransformNR base = new TransformNR(30, 0, 0);
		TransformNR tip = new TransformNR(60, 0, 0);

		Fixture() {
			file.getOperations().add(new PoseOperation(0, 0));
			file.getOperations().add(new PoseOperation(0, 10));
			file.getOperations().add(new PoseOperation(10, 30));
			file.setCurrentIndex(3);
			file.addListener(this);
		}

		private class PoseOperation extends CaDoodleOperation implements ICadoodleOperationUndo {
			final double before;
			final double after;

			PoseOperation(double before, double after) {
				this.before = before;
				this.after = after;
			}

			@Override
			public void undo() {
				events.add("undo:" + file.getCurrentIndex());
				restore(before);
			}

			@Override
			public void redo() {
				events.add("redo:" + file.getCurrentIndex());
				restore(after);
			}

			private void restore(double x) {
				base = new TransformNR(x, 0, 0);
				tip = new TransformNR(2 * x, 0, 0);
			}

			@Override
			public String getType() {
				return "test pose";
			}

			@Override
			public List<CSG> process(List<CSG> incoming) {
				return incoming;
			}

			@Override
			public List<String> getNamesAddedInThisOperation() {
				return Collections.emptyList();
			}
		}

		private void record(String event) {
			events.add(event + ":" + file.getCurrentIndex() + ":" + base.getX() + ":" + tip.getX());
		}

		@Override
		public void onUpdate(List<CSG> state, CaDoodleOperation source, CaDoodleFile file) {
			record("update");
		}

		@Override
		public void onSaveSuggestion() {
			record("save");
		}

		@Override
		public void onInitializationDone() {
		}

		@Override
		public void onInitializationStart() {
		}

		@Override
		public void onRegenerateDone() {
		}

		@Override
		public void onRegenerateStart(CaDoodleOperation source) {
		}

		@Override
		public void onWorkplaneChange(TransformNR newWP) {
		}

		@Override
		public void onTimelineUpdate(int numberOfNew, WritableImage image) {
		}
	}
}
