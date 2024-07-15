package com.neuronrobotics.video;

public class OSUtil {
	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}

	public static boolean isLinux() {
		return System.getProperty("os.name").toLowerCase().contains("linux");
	}

	public static boolean isOSX() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
	}
	public static String getOsName() {
		return System.getProperty("os.name");
	}
	//getOsArch
	public static String getOsArch() {
		return System.getProperty("os.arch");
	}
	//OSUtil.is64Bit()
	public static boolean is64Bit() {
		String model = System.getProperty("sun.arch.data.model", System.getProperty("com.ibm.vm.bitmode"));
		if (model != null) {
			return "64".equals(model);
		}
		return false;
	}
	public static boolean isArm() {
		return System.getProperty("os.arch").toLowerCase().contains("aarch64")
				|| System.getProperty("os.arch").toLowerCase().contains("arm");
	}
}
