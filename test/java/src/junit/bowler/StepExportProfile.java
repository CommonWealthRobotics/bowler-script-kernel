package junit.bowler;

import java.nio.file.*;
import java.util.List;
import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import com.neuronrobotics.sdk.common.TickToc;
import eu.mihosoft.vrl.v3d.CSG;

/** Run with the kernel test runtime classpath; arguments: fixture, output directory, repetitions. */
public final class StepExportProfile {
	public static void main(String[] args) throws Throwable {
		CSG.setDefaultOptType(CSG.OptType.Manifold3d);
		List<CSG> parts = CSG.fromThreeMF(Path.of(args[0]));
		Path output = Files.createDirectories(Path.of(args[1]));
		for (int run = 0; run < Integer.parseInt(args[2]); run++) {
			TickToc.setEnabled(true);
			TickToc.tic("run " + run + "; construct start");
			STEPFile step = new STEPFile(parts);
			TickToc.tic("construct complete; serialize start");
			String text = step.toStep();
			TickToc.tic("serialize complete");
			Files.writeString(output.resolve("profile.step"), text);
			TickToc.tic("disk complete; bytes=" + Files.size(output.resolve("profile.step")));
			TickToc.toc();
			TickToc.setEnabled(false);
		}
	}
}
