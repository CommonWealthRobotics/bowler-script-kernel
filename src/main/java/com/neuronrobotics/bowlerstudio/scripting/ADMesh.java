package com.neuronrobotics.bowlerstudio.scripting;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.run;

import java.io.File;
import java.util.ArrayList;

public class ADMesh {
	private static boolean reverseMesh = false;

	public static void fix(File in, File out, boolean reverseMesh) {
		ADMesh.reverseMesh = reverseMesh;
		fix(in, out);
	}

	public static void fix(File in, File out) {
		ArrayList<String> args = new ArrayList<>();

		File configExecutable = DownloadManager.getRunExecutable("admesh", null);
		// configExecutable.setExecutable(true, true);
		args.add(configExecutable.getAbsolutePath());

		args.add("--fill-holes");
		args.add("--nearby");
		args.add("--normal-directions");
		args.add("--normal-values");
		if (isReverseMesh())
			args.add("--reverse-all");
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

	public static boolean isReverseMesh() {
		return reverseMesh;
	}

	public static void setReverseMesh(boolean reverseMesh) {
		ADMesh.reverseMesh = reverseMesh;
	}
}
