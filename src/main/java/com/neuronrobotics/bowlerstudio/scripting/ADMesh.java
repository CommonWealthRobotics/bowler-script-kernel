package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.run;

import java.io.File;
import java.util.ArrayList;

public class ADMesh {

	public static void fix(File in, File out) {
		ArrayList<String> args = new ArrayList<>();

		File configExecutable = DownloadManager.getRunExecutable("admesh", null);
		// configExecutable.setExecutable(true, true);
		args.add(configExecutable.getAbsolutePath());

		args.add("-fudvb");
		args.add("--fill-holes");
		args.add("--fix-normals");
		args.add("--normal-directions");
		args.add("--normal-values");
		args.add("--remove-unconnected-facets ");
		args.add("--write-binary-stl");
		args.add(out.getAbsolutePath());
		args.add(in.getAbsolutePath());
		Thread t = run(null, configExecutable.getParentFile(), System.out, args);
		try {
			t.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
