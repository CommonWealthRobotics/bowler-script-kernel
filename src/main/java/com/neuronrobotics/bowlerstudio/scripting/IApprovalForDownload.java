package com.neuronrobotics.bowlerstudio.scripting;

public interface IApprovalForDownload {
	boolean get(String name, String url);
	void onInstallFail(String url);
}
