package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.nio.file.WatchEvent;
import java.util.HashMap;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.util.FileWatchDeviceWrapper;
import com.neuronrobotics.bowlerstudio.util.IFileChangeListener;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.DhInverseSolver;
import com.neuronrobotics.sdk.addons.kinematics.IDriveEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.parallel.ParallelGroup;

public class MobileBaseLoader {
	private static HashMap<MobileBase, MobileBaseLoader> map = new HashMap<>();
	private MobileBase base;
	private IDriveEngine defaultDriveEngine;

	private MobileBaseLoader(MobileBase base) {
		this.setBase(base);

		setDefaultWalkingEngine(base);
		base.initializeParalellGroups();
	}

	public void setGitDhEngine(String gitsId, String file, DHParameterKinematics dh) {
		dh.setGitDhEngine(new String[] { gitsId, file });

		setDefaultDhParameterKinematics(dh);

	}

	public File setDefaultDhParameterKinematics(DHParameterKinematics device) {
		File code = null;
		try {
			code = ScriptingEngine.fileFromGit(device.getGitDhEngine()[0], device.getGitDhEngine()[1]);
			DhInverseSolver defaultDHSolver = (DhInverseSolver) ScriptingEngine.inlineFileScriptRun(code, null);

			File c = code;
			FileWatchDeviceWrapper.watch(device, code,new IFileChangeListener() {
				
				@Override
				public void onFileDelete(File fileThatIsDeleted) {
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public void onFileChange(File fileThatChanged, WatchEvent event)  {

					try {
						System.out.println("D-H Solver changed, updating " + device.getScriptingName());
						DhInverseSolver d = (DhInverseSolver) ScriptingEngine.inlineFileScriptRun(c, null);
						device.setInverseSolver(d);
					} catch (Exception ex) {
						MobileBaseCadManager.get(base).getUi().highlightException(c, ex);
					}
				}
			});

			device.setInverseSolver(defaultDHSolver);
			return code;
		} catch (Exception e1) {
			MobileBaseCadManager.get(base).getUi().highlightException(code, e1);
		}
		return null;

	}

	public void setDefaultWalkingEngine(MobileBase device) {
		if (defaultDriveEngine == null) {
			setGitWalkingEngine(device.getGitWalkingEngine()[0], device.getGitWalkingEngine()[1], device);
		}
		for (DHParameterKinematics dh : device.getAllDHChains()) {
			setDefaultDhParameterKinematics(dh);
		}
	}

	public void setGitWalkingEngine(String git, String file, MobileBase device) {

		device.setGitWalkingEngine(new String[] { git, file });
		File code = null;
		try {
			code = ScriptingEngine.fileFromGit(git, file);
		} catch (Exception ex) {
			ex.printStackTrace();
			ScriptingEngine.deleteRepo(git);
			try {
				code = ScriptingEngine.fileFromGit(git, file);
			} catch (GitAPIException | IOException e) {
				MobileBaseCadManager.get(base).getUi().highlightException(code, e);
				throw new RuntimeException(e);
			}
		}

		File c = code;
		FileWatchDeviceWrapper.watch(device, code, new IFileChangeListener() {
			
			@Override
			public void onFileDelete(File fileThatIsDeleted) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void onFileChange(File fileThatChanged, WatchEvent event){

				try {
					System.out.println("Walking Gait Script changed, updating " + device.getScriptingName());
					defaultDriveEngine = (IDriveEngine) ScriptingEngine.inlineFileScriptRun(c, null);
					device.setWalkingDriveEngine(defaultDriveEngine);
				} catch (Exception ex) {
					MobileBaseCadManager.get(base).getUi().highlightException(c, ex);
				}
			}
		});

		try {
			defaultDriveEngine = (IDriveEngine) ScriptingEngine.inlineFileScriptRun(c, null);
			device.setWalkingDriveEngine(defaultDriveEngine);
		} catch (Exception ex) {
			MobileBaseCadManager.get(base).getUi().highlightException(c, ex);
		}
	}

	public static MobileBase initializeScripts(MobileBase base) {
		if (map.get(base) == null) {

			if (map.get(base) == null)
				map.put(base, new MobileBaseLoader(base));
			for (DHParameterKinematics kin : base.getAllDHChains()) {
				for (int i = 0; i < kin.getNumberOfLinks(); i++) {
					MobileBase m = kin.getDhLink(i).getSlaveMobileBase();
					if (m != null) {
						m.setGitSelfSource(base.getGitSelfSource());
						if (map.get(m) == null)
							map.put(m, new MobileBaseLoader(m));
					}
				}
			}
			
		}
		return base;
	}

	public static MobileBase fromGit(String id, String file) throws Exception {
		String xmlContent = ScriptingEngine.codeFromGit(id, file)[0];
		MobileBase mb = new MobileBase(IOUtils.toInputStream(xmlContent, "UTF-8"));

		mb.setGitSelfSource(new String[] { id, file });
		return initializeScripts(mb);
	}

	public static MobileBaseLoader get(MobileBase base) {
		initializeScripts(base);

		return map.get(base);
	}

	public MobileBase getBase() {
		return base;
	}

	public void setBase(MobileBase base) {
		this.base = base;
	}

}
