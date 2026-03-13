package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.nio.file.WatchEvent;
import java.util.HashMap;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.util.FileWatchDeviceWrapper;
import com.neuronrobotics.bowlerstudio.util.IFileChangeListener;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.DhInverseSolver;
import com.neuronrobotics.sdk.addons.kinematics.IDriveEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.common.Log;

import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;

public class MobileBaseLoader {
	private static HashMap<MobileBase, MobileBaseLoader> map = new HashMap<>();
	private MobileBase base;
	private IDriveEngine defaultDriveEngine;
	private CSGDatabaseInstance db;
	public CSGDatabaseInstance getDb() {
		return db;
	}

	public void setDb(CSGDatabaseInstance db) {
		if (db == null)
			throw new RuntimeException("DB can not be null!");
		this.db = db;
	}
	private MobileBaseLoader(CSGDatabaseInstance dbIn, MobileBase base) {
		this.setBase(base);
		setDb(dbIn);
		setDefaultWalkingEngine(base);
		base.initializeParalellGroups();
	}

	public void setGitDhEngine(String gitsId, String file, DHParameterKinematics dh) {
		dh.setGitDhEngine(new String[]{gitsId, file});

		setDefaultDhParameterKinematics(db, dh);

	}

	public static File setDefaultDhParameterKinematics(CSGDatabaseInstance db, DHParameterKinematics device) {
		File code = null;
		try {
			String remoteURI = device.getGitDhEngine()[0];
			String fileInRepo = device.getGitDhEngine()[1];
			code = ScriptingEngine.fileFromGit(remoteURI, fileInRepo);
			DhInverseSolver defaultDHSolver = (DhInverseSolver) ScriptingEngine.inlineFileScriptRun(db, code, null);

			File c = code;
			FileWatchDeviceWrapper.watch(device, code, new IFileChangeListener() {

				@Override
				public void onFileDelete(File fileThatIsDeleted) {
					// Auto-generated method stub

				}

				@Override
				public void onFileChange(File fileThatChanged, WatchEvent event) {

					try {
						com.neuronrobotics.sdk.common.Log
								.error("D-H Solver changed, updating " + device.getScriptingName());
						DhInverseSolver d = (DhInverseSolver) ScriptingEngine.inlineFileScriptRun(db, c, null);
						device.setInverseSolver(d);
					} catch (Exception ex) {
						Log.error(ex);
					}
				}
			});

			device.setInverseSolver(defaultDHSolver);
			return code;
		} catch (Exception e1) {
			Log.error(e1);
		}
		return null;

	}

	public void setDefaultWalkingEngine(MobileBase device) {
		if (defaultDriveEngine == null) {
			setGitWalkingEngine(device.getGitWalkingEngine()[0], device.getGitWalkingEngine()[1], device);
		}
		for (DHParameterKinematics dh : device.getAllDHChains()) {
			setDefaultDhParameterKinematics(db, dh);
		}
	}

	public void setGitWalkingEngine(String git, String file, MobileBase device) {

		device.setGitWalkingEngine(new String[]{git, file});
		File code = null;
		try {
			code = ScriptingEngine.fileFromGit(git, file);
		} catch (Exception ex) {
			com.neuronrobotics.sdk.common.Log.error(ex);;
			ScriptingEngine.deleteRepo(git);
			try {
				code = ScriptingEngine.fileFromGit(git, file);
			} catch (GitAPIException | IOException e) {
				MobileBaseCadManager.get(db, base).getUi().highlightException(code, e);
				throw new RuntimeException(e);
			}
		}

		File c = code;
		FileWatchDeviceWrapper.watch(device, code, new IFileChangeListener() {

			@Override
			public void onFileDelete(File fileThatIsDeleted) {
				// Auto-generated method stub

			}

			@Override
			public void onFileChange(File fileThatChanged, WatchEvent event) {

				try {
					com.neuronrobotics.sdk.common.Log
							.error("Walking Gait Script changed, updating " + device.getScriptingName());
					defaultDriveEngine = (IDriveEngine) ScriptingEngine.inlineFileScriptRun(getDb(), c, null);
					device.setWalkingDriveEngine(defaultDriveEngine);
				} catch (Exception ex) {
					MobileBaseCadManager.get(db, base).getUi().highlightException(c, ex);
				}
			}
		});

		try {
			defaultDriveEngine = (IDriveEngine) ScriptingEngine.inlineFileScriptRun(getDb(), c, null);
			device.setWalkingDriveEngine(defaultDriveEngine);
		} catch (Exception ex) {
			MobileBaseCadManager.get(db, base).getUi().highlightException(c, ex);
		}
	}

	public static MobileBase initializeScripts(CSGDatabaseInstance db, MobileBase base) {
		if (map.get(base) == null) {

			if (map.get(base) == null)
				map.put(base, new MobileBaseLoader(db, base));
			// for (DHParameterKinematics kin : base.getAllDHChains()) {
			// for (int i = 0; i < kin.getNumberOfLinks(); i++) {
			// MobileBase m = kin.getDhLink(i).getSlaveMobileBase();
			// if (m != null) {
			// m.setGitSelfSource(base.getGitSelfSource());
			// if (map.get(m) == null)
			// map.put(m, new MobileBaseLoader(m));
			// }
			// }
			// }

		}
		return base;
	}

	public static MobileBase fromGit(CSGDatabaseInstance db, String id, String file) throws Exception {
		String xmlContent = ScriptingEngine.codeFromGit(id, file)[0];
		MobileBase mb = new MobileBase(IOUtils.toInputStream(xmlContent, "UTF-8"));

		mb.setGitSelfSource(new String[]{id, file});
		return initializeScripts(db, mb);
	}

	public static MobileBaseLoader get(CSGDatabaseInstance db, MobileBase base) {
		initializeScripts(db, base);

		return map.get(base);
	}

	public MobileBase getBase() {
		return base;
	}

	public void setBase(MobileBase base) {
		this.base = base;
		try {
			String[] self = base.getGitSelfSource();
			File selfFile = ScriptingEngine.fileFromGit(self);
			File parent = selfFile.getParentFile();
			File database = new File(parent.getAbsolutePath() + DownloadManager.delim() + "csgDatabase.json");
			setDb(new CSGDatabaseInstance(database));
		} catch (InvalidRemoteException e) {
			// TODO Auto-generated catch block
			Log.error(e);
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			Log.error(e);
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			Log.error(e);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.error(e);
		}
	}

}
