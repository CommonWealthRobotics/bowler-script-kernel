package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.IssueReportingExceptionHandler;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.util.FileChangeWatcher;
import com.neuronrobotics.bowlerstudio.util.FileWatchDeviceWrapper;
import com.neuronrobotics.bowlerstudio.util.IFileChangeListener;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.AbstractKinematicsNR;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.ILinkListener;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.IDeviceConnectionEventListener;
import com.neuronrobotics.sdk.pid.PIDLimitEvent;
import com.neuronrobotics.sdk.util.ThreadUtil;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import javafx.beans.property.*;
import javafx.scene.transform.Affine;
import javafx.application.Platform;

public class MobileBaseCadManager implements Runnable {

	// static
	private static HashMap<MobileBase, MobileBaseCadManager> cadmap = new HashMap<>();
	// static
	// private Object cadForBodyEngine;
	private MobileBase base;
	private ArrayList<MobileBaseCadManager> slaves = new ArrayList<MobileBaseCadManager>();
	// private File cadScript;

	// private HashMap<DHParameterKinematics, Object> dhCadGen = new HashMap<>();
	private HashMap<DHParameterKinematics, ArrayList<CSG>> DHtoCadMap = new HashMap<>();
	private HashMap<LinkConfiguration, ArrayList<CSG>> LinktoCadMap = new HashMap<>();
	private HashMap<MobileBase, ArrayList<CSG>> BasetoCadMap = new HashMap<>();

	private boolean cadGenerating = false;
	private boolean showingStl = false;
	private ArrayList<CSG> allCad;

	private boolean bail = false;
	private IMobileBaseUI ui = null;
	private static ICadGenerator cadEngineConfiguration = null;
	private boolean configMode = false;
	private boolean autoRegen = true;
	private DoubleProperty pi = new SimpleDoubleProperty(0);
	private MobileBaseCadManager master;
	// private boolean rendering = false;
	private Thread renderWrangler = null;
	private HashMap<String, Object> cadScriptCache = new HashMap<>();

	protected void clear() {
		// Cad generator
		cadScriptCache.clear();
		// clear the csgs from the list
		for (DHParameterKinematics key : DHtoCadMap.keySet()) {
			ArrayList<CSG> arrayList = DHtoCadMap.get(key);
			if (arrayList != null)
				arrayList.clear();
		}
		DHtoCadMap.clear();
		// celat csg from link conf list
		for (LinkConfiguration key : LinktoCadMap.keySet()) {
			ArrayList<CSG> arrayList = LinktoCadMap.get(key);
			if (arrayList != null)
				arrayList.clear();
		}
		LinktoCadMap.clear();
		for (MobileBase key : BasetoCadMap.keySet()) {
			ArrayList<CSG> arrayList = BasetoCadMap.get(key);
			if (arrayList != null)
				arrayList.clear();
		}
		BasetoCadMap.clear();
		if (allCad != null)
			allCad.clear();
		Vitamins.clear();
		for (MobileBaseCadManager m : slaves) {
			m.clear();
		}

	}

	private static class IMobileBaseUIlocal implements IMobileBaseUI {

		public ArrayList<CSG> list = new ArrayList<>();

		@Override
		public void highlightException(File fileEngineRunByName, Throwable ex) {
			new Exception("Caught here:").printStackTrace();
			ex.printStackTrace();
		}

		@Override
		public void setAllCSG(Collection<CSG> toAdd, File source) {
			// TODO Auto-generated method stub
			// TODO Auto-generated method stub
			list.clear();
			list.addAll(toAdd);
		}

		@Override
		public void addCSG(Collection<CSG> toAdd, File source) {
			// TODO Auto-generated method stub
			list.addAll(toAdd);

		}

		@Override
		public Set<CSG> getVisibleCSGs() {
			// TODO Auto-generated method stub
			return new HashSet<CSG>(list);
		}

		@Override
		public void setSelectedCsg(Collection<CSG> selectedCsg) {
			// TODO Auto-generated method stub

		}

		@Override
		public void setSelected(Affine rootListener) {
			// TODO Auto-generated method stub

		}
	};

//	private IFileChangeListener cadWatcher = new IFileChangeListener() {
//		boolean fileHandeling = false;
//
//		@Override
//		public void onFileChange(File fileThatChanged, WatchEvent event) {
//			if (fileHandeling)
//				return;
//
//			if (cadGenerating || !getAutoRegen()) {
//				System.out.println("No Base reload, building currently");
//				return;
//			}
//			fileHandeling = true;
//			try {
//				new Thread() {
//					public void run() {
//						try {
//
//							System.out.println("Re-loading Cad Base Engine");
//							cadForBodyEngine = ScriptingEngine.inlineFileScriptRun(fileThatChanged, null);
//							
//						} catch (Exception e) {
//							getUi().highlightException(null, e);
//						}
//						generateCad();
//						fileHandeling = false;
//					}
//				}.start();
//			} catch (Exception e) {
//				getUi().highlightException(null, e);
//			}
//		}
//
//		@Override
//		public void onFileDelete(File fileThatIsDeleted) {
//			
//		}
//	};

	// This is the rendering event
	public void run() {
		// rendering = true;
		String name = base.getScriptingName();
		if (renderWrangler == null) {
			renderWrangler = new Thread() {
				HashMap<Affine,TransformNR> m=new HashMap<Affine, TransformNR>();
				boolean rendering = false;
				@Override
				public void run() {
					
					setName("MobileBaseCadManager Render Thread for " + base.getScriptingName());
					while (base.isAvailable()) {
						try {
							do {
								Thread.sleep(16 );
							}while(rendering);
						} catch (InterruptedException e) {
							getUi().highlightException(null, e);
						}

						// System.err.println("Render "+timeSince);
						try {
							
							updateMobileBase(base,base.getFiducialToGlobalTransform(),m);
							rendering=true;
							Platform.runLater(() -> {
								for(Affine af:m.keySet()) {
									TransformFactory.nrToAffine(m.get(af),af);
								}
								m.clear();
								rendering = false;
							});
						} catch (Throwable t) {
							// rendering not availible
							break;
						}

					}
					renderWrangler = null;
				}

				private void updateMobileBase(MobileBase b, TransformNR baseLoc, HashMap<Affine, TransformNR> map2) {
					updateBase(b,baseLoc,map2);
					for (DHParameterKinematics k : b.getAllDHChains()) {
						updateLimb(k,baseLoc,map2);
					}
					for (DHParameterKinematics k : b.getAllParallelGroups()) {
						updateBase(k,baseLoc,map2);
					}

				}

				private void updateLimb(DHParameterKinematics k, TransformNR baseLoc, HashMap<Affine, TransformNR> map2) {
					updateBase(k,baseLoc,map2);
					TransformNR previous = k.getFiducialToGlobalTransform();
					k.setGlobalToFiducialTransform(baseLoc);
					ArrayList<TransformNR> ll = k.getChain().getChain(k.getCurrentJointSpaceVector());

					for (int i = 0; i < ll.size(); i++) {
						ArrayList<TransformNR> linkPos = ll;
						int index = i;
						Affine a;
						if (k.getChain().getLinks().get(index).getListener() == null) {
							k.getChain().getLinks().get(index).setListener(new Affine());
						}
						try {
							a = (Affine) k.getChain().getLinks().get(index).getListener();
						} catch (java.lang.ClassCastException ex) {
							a = new Affine();
							k.getChain().getLinks().get(index).setListener(a);
						}
						if (k.getAbstractLink(i).getGlobalPositionListener() == null) {
							k.getAbstractLink(i).setGlobalPositionListener(a);
						}

						Affine af = a;
						TransformNR nr = linkPos.get(index);
						if (nr != null && af != null)
							map2.put(af, nr);
							//Platform.runLater(() -> {
//								try {
//									TransformFactory.nrToAffine(nr, af);
//								} catch (Exception ex) {
//									getUi().highlightException(null, ex);
//								}
							//});
						if (k.getSlaveMobileBase(i) != null) {
							updateMobileBase(k.getSlaveMobileBase(i),nr,map2);
						}
					}
					k.setGlobalToFiducialTransform(previous);
				}
			};
			renderWrangler.start();
		}

	}

	private void updateBase(AbstractKinematicsNR kin, TransformNR baseLoc, HashMap<Affine, TransformNR> map2) {
		if (kin == null)
			return;
		TransformNR previous = kin.getFiducialToGlobalTransform();
		kin.setGlobalToFiducialTransform(baseLoc);
		TransformNR forwardOffset = kin.forwardOffset(new TransformNR());
		if (kin.getRootListener() == null) {
			kin.setRootListener(new Affine());
		}
		if (forwardOffset != null && kin.getRootListener() != null)
			map2.put((Affine) kin.getRootListener(), forwardOffset);
			//Platform.runLater(() -> {
//				try {
//					TransformFactory.nrToAffine(forwardOffset, (Affine) kin.getRootListener());
//				} catch (Exception ex) {
//					getUi().highlightException(null, ex);
//				}
			//});
		kin.setGlobalToFiducialTransform(previous);
	}

	private MobileBaseCadManager(MobileBase base, IMobileBaseUI myUI) {
		this.setUi(myUI);

		setMobileBase(base);
		cadmap.put(base, this);

	}

//	public File getCadScript() {
//		return cadScript;
//	}

//	public void setCadScript(File cadScript) {
//		if (cadScript == null)
//			return;
//		FileWatchDeviceWrapper.watch(base, cadScript, cadWatcher);
//
//		this.cadScript = cadScript;
//	}
	private Object scriptFromFileInfo(String[] args, Runnable r) {
		String key = args[0] + ":" + args[1];
		try {
			File f = ScriptingEngine.fileFromGit(args[0], args[1]);
			if (cadScriptCache.get(key) == null) {
				try {
					System.err.println(
							"Building the compiled CAD script for " + key + " " + base + " " + base.getScriptingName());
					cadScriptCache.put(key, ScriptingEngine.inlineFileScriptRun(f, null));
				} catch (Exception e) {
					getUi().highlightException(f, e);
				}
				FileChangeWatcher watcher = FileChangeWatcher.watch(f);
				watcher.addIFileChangeListener(new IFileChangeListener() {

					@Override
					public void onFileChange(File fileThatChanged, WatchEvent event) {
						try {
							System.err.println("Clearing the compiled CAD script for " + key);
							cadScriptCache.remove(key);
							r.run();
						} catch (Exception e) {
							getUi().highlightException(f, e);
						}
					}

					@Override
					public void onFileDelete(File fileThatIsDeleted) {
						cadScriptCache.remove(key);
					}
				});
			}
			return cadScriptCache.get(key);
		} catch (GitAPIException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		throw new RuntimeException("File Missing!");

	}

	private void closeScriptFromFileInfo(String[] args) {
		String key = args[0] + ":" + args[1];
		cadScriptCache.remove(key);
		try {
			File f = ScriptingEngine.fileFromGit(args[0], args[1]);
			FileChangeWatcher.close(f);

		} catch (InvalidRemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private IgenerateBody getIgenerateBody(MobileBase b) {
		if (configMode)
			return getConfigurationDisplay();
		Object cadForBodyEngine = scriptFromFileInfo(b.getGitCadEngine(), () -> {
			run();
		});
		if (IgenerateBody.class.isInstance(cadForBodyEngine)) {
			return (IgenerateBody) cadForBodyEngine;
		}
		return null;
	}

	private IgenerateCad getIgenerateCad(DHParameterKinematics dh) {
		if (configMode)
			return getConfigurationDisplay();
		Object cadForBodyEngine = scriptFromFileInfo(dh.getGitCadEngine(), () -> {
			generateCad(dh);
		});
		if (IgenerateCad.class.isInstance(cadForBodyEngine)) {
			return (IgenerateCad) cadForBodyEngine;
		}
		return null;
	}

	private IgenerateBed getIgenerateBed() {
		Object cadForBodyEngine = scriptFromFileInfo(base.getGitCadEngine(), () -> {
			run();
		});
		if (IgenerateBed.class.isInstance(cadForBodyEngine)) {
			return (IgenerateBed) cadForBodyEngine;
		}
		return null;
	}

	private static ICadGenerator getConfigurationDisplay() {
		if (cadEngineConfiguration == null) {
			try {
				File confFile = resetConfigurationScript();
				FileChangeWatcher watcher = FileChangeWatcher.watch(confFile);
				watcher.addIFileChangeListener(new IFileChangeListener() {

					@Override
					public void onFileChange(File fileThatChanged, WatchEvent event) {
						MobileBaseCadManager mobileBaseCadManager = null;
						try {
							resetConfigurationScript();

							for (MobileBase manager : cadmap.keySet()) {
								mobileBaseCadManager = cadmap.get(manager);
								if (mobileBaseCadManager.autoRegen)
									if (mobileBaseCadManager.configMode)
										mobileBaseCadManager.generateCad();
							}
						} catch (Exception e) {
							if (mobileBaseCadManager != null)
								mobileBaseCadManager.getUi().highlightException(null, e);
						}
					}

					@Override
					public void onFileDelete(File fileThatIsDeleted) {
						// TODO Auto-generated method stub

					}
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return cadEngineConfiguration;
	}

	private static File resetConfigurationScript()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, Exception {
		File confFile = ScriptingEngine
				.fileFromGit("https://github.com/CommonWealthRobotics/DHParametersCadDisplay.git", "dhcad.groovy");
		cadEngineConfiguration = (ICadGenerator) ScriptingEngine.inlineFileScriptRun(confFile, null);
		return confFile;
	}

	public ArrayList<CSG> generateBody() {
		return generateBody(getMobileBase());
	}

	public ArrayList<CSG> generateBody(MobileBase base) {
		if (!base.isAvailable())
			throw new RuntimeException("Device " + base.getScriptingName() + " is not connected, can not generate cad");

		getProcesIndictor().set(0);
		setAllCad(new ArrayList<>());

		MobileBase device = base;
		if (getBasetoCadMap().get(device) == null) {
			getBasetoCadMap().put(device, new ArrayList<CSG>());
		}

		getProcesIndictor().set(0.1);
		try {
			getAllCad().clear();
			if (showingStl) {
				// skip the regen
				for (CSG c : getBasetoCadMap().get(device)) {
					getAllCad().add(c);
				}
			} else {
				if (!bail) {

					ArrayList<CSG> newcad = null;
					try {
						newcad = getIgenerateBody(device).generateBody(device);
					} catch (Throwable t) {
						getUi().highlightException(null, t);
					}
					if (newcad == null) {
						newcad = new ArrayList<CSG>();
					}
					if (newcad.size() == 0) {
						newcad = getConfigurationDisplay().generateBody(device);
					}
					if (device.isAvailable()) {
						for (CSG c : newcad) {
							getAllCad().add(c);
						}
						ui.addCSG(newcad, getCadScriptFromMobileBase(device));
					}
				} else
					getUi().highlightException(null, new Exception());
				ArrayList<CSG> arrayList = getBasetoCadMap().get(device);
				arrayList.clear();
				for (CSG c : getAllCad()) {
					arrayList.add(c);
				}
				new Thread(() -> {
					Thread.currentThread().setUncaughtExceptionHandler(new IssueReportingExceptionHandler());

					localGetBaseCad(device);// load the cad union in a thread to
											// make it ready for physics
				}).start();
			}
		} catch (Exception e) {
			getUi().highlightException(getCadScriptFromMobileBase(device), e);
		}
		System.out.println("Displaying Body");
		getProcesIndictor().set(0.35);
		// clears old robot and places base
		getUi().setAllCSG(getBasetoCadMap().get(device), getCadScriptFromMobileBase(device));
		System.out.println("Rendering limbs");
		getProcesIndictor().set(0.4);
		ArrayList<DHParameterKinematics> limbs = base.getAllDHChains();
		double numLimbs = limbs.size();
		int i = 0;
		for (DHParameterKinematics l : limbs) {
			if (getDHtoCadMap().get(l) == null) {
				getDHtoCadMap().put(l, new ArrayList<CSG>());
			}
			ArrayList<CSG> arrayList = getDHtoCadMap().get(l);
			int j = 0;
			boolean isAvailible = device.isAvailable();
			if (showingStl || !isAvailible) {
				for (CSG csg : arrayList) {
					getAllCad().add(csg);
					getUi().addCsg(csg, getCadScriptFromLimnb(l));
					set(base, (int) i, (int) j);
					j += 1;
				}
			} else {

				arrayList.clear();
				ArrayList<CSG> linksCad = generateCad(l);

				for (CSG csg : linksCad) {

					getAllCad().add(csg);
					arrayList.add(csg);
					getUi().addCsg(csg, getCadScriptFromLimnb(l));
					j += 1;

				}

			}

			i += 1;

		}
		for (MobileBaseCadManager m : slaves) {
			getAllCad().addAll(m.generateBody(m.base));
		}
		showingStl = false;
		getProcesIndictor().set(1);
		// PhysicsEngine.clear();
		// MobileBasePhysicsManager m = new MobileBasePhysicsManager(base,
		// baseCad, getSimplecad());
		// PhysicsEngine.startPhysicsThread(50);
		// return PhysicsEngine.getCsgFromEngine();
		return getAllCad();
	}

	public File getCadScriptFromLimnb(DHParameterKinematics l) {
		try {
			return ScriptingEngine.fileFromGit(l.getGitCadEngine()[0], l.getGitCadEngine()[1]);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public File getCadScriptFromMobileBase(MobileBase device) {
		try {
			return ScriptingEngine.fileFromGit(device.getGitCadEngine()[0], device.getGitCadEngine()[1]);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	private void set(MobileBase base, int limb, int link) {
		ArrayList<DHParameterKinematics> limbs = base.getAllDHChains();
		int numLimbs = limbs.size();
		if(limb>=numLimbs) {
			limb=numLimbs-1;
		}
		DHParameterKinematics dh = limbs.get(limb);
		double partsTotal = numLimbs * dh.getNumberOfLinks();
		double progress = ((double) ((limb * dh.getNumberOfLinks()) + link)) / partsTotal;
		// System.out.println("Cad progress " + progress + " limb " + limb + " link " +
		// link + " total parts " + partsTotal);
		getProcesIndictor().set(0.333 + (2 * (progress / 3)));
	}

	public LinkConfiguration getLinkConfiguration(CSG cad) {
		LinkConfiguration conf = null;
		for (LinkConfiguration c : LinktoCadMap.keySet()) {
			for (CSG cadTest : LinktoCadMap.get(c)) {
				if (cadTest == cad) {
					conf = c;
				}
			}
		}
		return conf;
	}

	public ArrayList<File> generateStls(MobileBase base, File baseDirForFiles, boolean kinematic) throws IOException {
		IgenerateBed bed = getIgenerateBed();
		if (bed == null || kinematic) {
			return _generateStls(base, baseDirForFiles, kinematic);
		}
		System.out.println("Found arrangeBed API in CAD engine");
		List<CSG> totalAssembly = bed.arrangeBed(base);
		getUi().setAllCSG(totalAssembly, getCadScriptFromMobileBase(base));
		File dir = new File(baseDirForFiles.getAbsolutePath() + "/" + base.getScriptingName());
		if (!dir.exists())
			dir.mkdirs();

		return new CadFileExporter(getUi()).generateManufacturingParts(totalAssembly, baseDirForFiles);
	}

	private ArrayList<File> _generateStls(MobileBase base, File baseDirForFiles, boolean kinematic) throws IOException {
		ArrayList<File> allCadStl = new ArrayList<>();
		ArrayList<DHParameterKinematics> limbs = base.getAllDHChains();
		double numLimbs = limbs.size();
		int i;
		// Start by generating the legs using the DH link based generator
		ArrayList<CSG> totalAssembly = new ArrayList<>();
		double offset = 0;
		for (i = 0; i < limbs.size(); i += 1) {

			double progress = (1.0 - ((numLimbs - i) / numLimbs)) / 2;
			getProcesIndictor().set(progress);

			DHParameterKinematics l = limbs.get(i);
			ArrayList<CSG> parts = getDHtoCadMap().get(l);
			for (int j = 0; j < parts.size(); j++) {
				CSG csg = parts.get(j);
				String name = csg.getName();
				try {
					CSG tmp;
					if (!kinematic)
						csg = csg.prepForManufacturing();
					if (csg != null) {
						if (!kinematic) {
							tmp = csg.toXMax().toYMax();
						} else {
							tmp = csg;
						}
						if (totalAssembly.size() > 0 && !kinematic)
							totalAssembly.add(tmp.movey(.5 + totalAssembly.get(totalAssembly.size() - 1).getMaxY()
									+ Math.abs(csg.getMinY())));
						else
							totalAssembly.add(tmp);
						LinkConfiguration conf = getLinkConfiguration(parts.get(j));

						String linkNum = conf.getLinkIndex() + "_Link_";

						File dir = new File(baseDirForFiles.getAbsolutePath() + "/" + base.getScriptingName() + "/"
								+ l.getScriptingName());
						if (!dir.exists())
							dir.mkdirs();
						System.out.println("Making STL for " + name);
						File stl = new File(
								dir.getAbsolutePath() + "/" + linkNum + name + "_limb_" + i + "_Part_" + j + ".stl");
						FileUtil.write(Paths.get(stl.getAbsolutePath()), tmp.toStlString());
						allCadStl.add(stl);
						// totalAssembly.add(tmp);
						getUi().setAllCSG(totalAssembly, getCadScriptFromMobileBase(base));
						set(base, i, j);
					}
				} catch (Exception ex) {
					getUi().highlightException(getCadScriptFromMobileBase(base), ex);
				}
				// legAssembly.setManufactuing(new PrepForManufacturing() {
				// public CSG prep(CSG arg0) {
				// return null;
				// }
				// });
			}
			// offset =
			// -2-((legAssembly.get(legAssembly.size()-1).getMaxX()+legAssembly.get(legAssembly.size()-1).getMinX())*i);
			// legAssembly=legAssembly.movex(offset);

		}

		int link = 0;
		// now we genrate the base pieces
		for (CSG csg : getBasetoCadMap().get(base)) {
			String name = csg.getName();
			try {
				if (!kinematic)
					csg = csg.prepForManufacturing();
				if (csg != null) {
					if (!kinematic) {
						csg = csg.toYMin().movex(-2 - csg.getMaxX() + offset);
					}
					File dir = new File(baseDirForFiles.getAbsolutePath() + "/" + base.getScriptingName() + "/");
					if (!dir.exists())
						dir.mkdirs();
					File stl = new File(dir.getAbsolutePath() + "/" + name + "_Body_part_" + link + ".stl");
					FileUtil.write(Paths.get(stl.getAbsolutePath()), csg.toStlString());
					allCadStl.add(stl);
					totalAssembly.add(csg);
					getUi().setAllCSG(totalAssembly, getCadScriptFromMobileBase(base));
					link++;
				}
			} catch (Exception ex) {
				getUi().highlightException(getCadScriptFromMobileBase(base), ex);
			}
		}
		// ui.setCsg(BasetoCadMap.get(base),getCadScript());
		// for(CSG c: DHtoCadMap.get(base.getAllDHChains().get(0))){
		// ui.addCsg(c,getCadScript());
		// }
		showingStl = true;
		getProcesIndictor().set(1);
		return allCadStl;
	}

	public MobileBase getMobileBase() {
		return base;
	}

	public void setMobileBase(MobileBase b) {
		this.base = b;
		cadmap.put(base, this);
		MobileBaseLoader.get(base);// load the dependant scripts
		base.updatePositions();
		base.setRenderWrangler(this);
		for (DHParameterKinematics k : base.getAllDHChains()) {
			k.setRenderWrangler(this);
		}
		run();
		// new Exception("Adding the mysteryListener
		// "+b.getScriptingName()).printStackTrace();

		base.addConnectionEventListener(new IDeviceConnectionEventListener() {

			@Override
			public void onDisconnect(BowlerAbstractDevice arg0) {
				if (arg0 != base) {
					new Exception("This listener called from the wrong device!! " + arg0.getScriptingName())
							.printStackTrace();
					return;
				}
				base.setRenderWrangler(null);
				for (DHParameterKinematics k : base.getAllDHChains()) {
					k.setRenderWrangler(null);
				}
				bail = true;
				clear();
				cadmap.remove(base);
				slaves.clear();
				master = null;
			}

			@Override
			public void onConnect(BowlerAbstractDevice arg0) {
				// TODO Auto-generated method stub

			}
		});
	}

	/**
	 * This function iterates through the links generating them
	 * 
	 * @param dh
	 * @return
	 */
	public ArrayList<CSG> generateCad(DHParameterKinematics dh) {
		ArrayList<CSG> dhLinks = new ArrayList<>();

		try {
			IgenerateCad generatorToUse = getConfigurationDisplay();
			Object object = getIgenerateCad(dh);
			if (object != null && !configMode) {
				if (IgenerateCad.class.isInstance(object))
					generatorToUse = (IgenerateCad) object;
			}
			int j = 0;
			for (DHParameterKinematics dhtest : getMobileBase().getAllDHChains()) {
				if (dhtest == dh)
					break;
				j++;
			}
			for (int i = 0; i < dh.getNumberOfLinks(); i++) {
				set(base, (int) j, (int) i);

				if (!bail) {
					ArrayList<CSG> newcad = null;
					try {
						newcad = generatorToUse.generateCad(dh, i);
					} catch (Throwable t) {
						getUi().highlightException(null, t);
					}
					if (newcad == null) {
						newcad = new ArrayList<CSG>();
					}
					if (newcad.size() == 0) {
						newcad = getConfigurationDisplay().generateCad(dh, i);
					}
					getUi().addCSG(newcad, getCadScriptFromLimnb(dh));
					LinkConfiguration configuration = dh.getLinkConfiguration(i);
					if (getLinktoCadMap().get(configuration) == null) {
						getLinktoCadMap().put(configuration, new ArrayList<>());
					} else
						getLinktoCadMap().get(configuration).clear();
					for (CSG c : newcad) {
						dhLinks.add(c);
						getLinktoCadMap().get(configuration).add(c);// add to
																	// the
																	// regestration
																	// storage
					}
					AbstractLink link = dh.getFactory().getLink(configuration);
					link.addLinkListener(new ILinkListener() {

						@Override
						public void onLinkPositionUpdate(AbstractLink arg0, double arg1) {
							// TODO Auto-generated method stub

						}

						@Override
						public void onLinkLimit(AbstractLink arg0, PIDLimitEvent arg1) {
							if (getAutoRegen())
								selectCsgByLimb(base, dh);

						}
					});
					DHLink dhl = dh.getDhLink(i);
					if(dhl.getSlaveMobileBase()!=null) {
						ArrayList<CSG> slParts =generateBody(dhl.getSlaveMobileBase());
						dhLinks.addAll(slParts);
					}
					//ArrayList<CSG> generateBody(MobileBase base)
				}
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			getUi().highlightException(getCadScriptFromLimnb(dh), e);
		}
		return dhLinks;
	}

	public void selectCsgByMobileBase(MobileBase base) {
		try {

			ArrayList<CSG> csg = MobileBaseCadManager.get(base).getBasetoCadMap().get(base);
			getUi().setSelectedCsg(csg);
		} catch (Exception ex) {
			// getUi().highlightException(null, ex);
			System.err.println("Base not loaded yet");
		}

	}

	public void selectCsgByLimb(MobileBase base, DHParameterKinematics limb) {
		try {

//			ArrayList<CSG> limCad = MobileBaseCadManager.get(base).getDHtoCadMap().get(limb);
//			getUi().setSelectedCsg(limCad);
			getUi().setSelected((Affine) limb.getRootListener());
		} catch (Exception ex) {
			// getUi().highlightException(null, ex);
			System.err.println("Limb not loaded yet");
		}
	}

	public void selectCsgByLink(MobileBase base, LinkConfiguration limb) {
		try {

			ArrayList<CSG> limCad = MobileBaseCadManager.get(base).getLinktoCadMap().get(limb);
			getUi().setSelectedCsg(limCad);
		} catch (Exception ex) {
			System.err.println("Limb not loaded yet");
		}
	}

	public void generateCad() {
		if (cadGenerating || !getAutoRegen())
			return;
		cadGenerating = true;
		// new RuntimeException().printStackTrace();
		// new Exception().printStackTrace();
		new Thread() {
			@Override
			public void run() {
				// Thread.currentThread().setUncaughtExceptionHandler(new
				// IssueReportingExceptionHandler());

				System.out.print("\r\nGenerating CAD...\r\n");
				setName("MobileBaseCadManager Generating cad Thread ");
				// new Exception().printStackTrace();
				if (master != null) {
					for (int i = 0; i < allCad.size(); i++)
						master.allCad.remove(allCad.get(i));
				}
				MobileBase device = base;
				MobileBaseCadManager.get(base).clear();

				try {
					setAllCad(generateBody(device));
				} catch (Exception e) {
					getUi().highlightException(getCadScriptFromMobileBase(device), e);
				}

				if (master != null) {
					for (int i = 0; i < allCad.size(); i++)
						master.allCad.add(allCad.get(i));
					getUi().setCsg(master, getCadScriptFromMobileBase(device));
				} else
					getUi().setCsg(MobileBaseCadManager.get(base), getCadScriptFromMobileBase(device));
				cadGenerating = false;
				System.out.print("\r\nDone Generating CAD! num parts: " + allCad.size() + "\r\n");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				getProcesIndictor().set(1);
				// System.gc();
			}
		}.start();
	}

	public void onTabClosing() {

	}

	public void setGitCadEngine(String gitsId, String file, DHParameterKinematics dh)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		closeScriptFromFileInfo(dh.getGitCadEngine());
		dh.setGitCadEngine(new String[] { gitsId, file });
	}

	public void setGitCadEngine(String gitsId, String file, MobileBase device)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		closeScriptFromFileInfo(device.getGitCadEngine());
		device.setGitCadEngine(new String[] { gitsId, file });
	}

	public ArrayList<CSG> getAllCad() {
		return allCad;
	}

	public void setAllCad(ArrayList<CSG> allCad) {
		for (CSG part : allCad)
			for (String p : part.getParameters()) {
				CSGDatabase.addParameterListener(p, (arg0, arg1) -> {
					// generateCad(); //TODO Undo this after debugging
				});
			}

		if (this.allCad != null && this.allCad != allCad)
			this.allCad.clear();
		this.allCad = allCad;
	}

	public static MobileBaseCadManager get(MobileBase device, IMobileBaseUI ui) {
		if (cadmap.get(device) == null) {
			// new RuntimeException("No Mobile Base Cad Manager UI
			// specified").printStackTrace();
			MobileBaseCadManager mbcm = new MobileBaseCadManager(device, ui);

//			for (DHParameterKinematics kin : device.getAllDHChains()) {
//				for (int i = 0; i < kin.getNumberOfLinks(); i++) {
//					MobileBase m = kin.getDhLink(i).getSlaveMobileBase();
//					if (m != null) {
//						m.setGitSelfSource(device.getGitSelfSource());
//						MobileBaseCadManager e = new MobileBaseCadManager(m, ui);
//						e.setMaster(mbcm);
//						mbcm.slaves.add(e);
//					}
//				}
//			}
		}
		MobileBaseCadManager mobileBaseCadManager = cadmap.get(device);
		if (!IMobileBaseUIlocal.class.isInstance(ui)
				&& IMobileBaseUIlocal.class.isInstance(mobileBaseCadManager.getUi()))
			mobileBaseCadManager.setUi(ui);

		return mobileBaseCadManager;
	}

	private void setMaster(MobileBaseCadManager master) {
		this.master = master;
	}

	public static MobileBaseCadManager get(MobileBase device) {
		if (cadmap.get(device) == null) {
			for(MobileBase mb:cadmap.keySet()) {
				for(DHParameterKinematics kin:mb.getAllDHChains()) {
					for (int i = 0; i < kin.getNumberOfLinks(); i++) {
						MobileBase m = kin.getDhLink(i).getSlaveMobileBase();
						if (m == device) {
							return get(mb);
						}
					}
				}
			}
			IMobileBaseUIlocal ui2 = new IMobileBaseUIlocal();
			device.addConnectionEventListener(new IDeviceConnectionEventListener() {

				@Override
				public void onDisconnect(BowlerAbstractDevice source) {
					// TODO Auto-generated method stub
					ui2.list.clear();

				}

				@Override
				public void onConnect(BowlerAbstractDevice source) {
					// TODO Auto-generated method stub

				}
			});
			
			return get(device, ui2);
		}
		return cadmap.get(device);
	}

	public static HashMap<LinkConfiguration, ArrayList<CSG>> getSimplecad(MobileBase device) {
		return get(device).LinktoCadMap;
	}

	private ArrayList<CSG> localGetBaseCad(MobileBase device) {

		return BasetoCadMap.get(device);
	}

	public static ArrayList<CSG> getBaseCad(MobileBase device) {
		return get(device).localGetBaseCad(device);
	}

	public DoubleProperty getProcesIndictor() {
		return pi;
	}

	public void setProcesIndictor(DoubleProperty pi) {
		this.pi = pi;
	}

	public HashMap<MobileBase, ArrayList<CSG>> getBasetoCadMap() {
		return BasetoCadMap;
	}

	public void setBasetoCadMap(HashMap<MobileBase, ArrayList<CSG>> basetoCadMap) {
		BasetoCadMap = basetoCadMap;
	}

	public HashMap<DHParameterKinematics, ArrayList<CSG>> getDHtoCadMap() {
		return DHtoCadMap;
	}

	public void setDHtoCadMap(HashMap<DHParameterKinematics, ArrayList<CSG>> dHtoCadMap) {
		DHtoCadMap = dHtoCadMap;
	}

	public HashMap<LinkConfiguration, ArrayList<CSG>> getLinktoCadMap() {
		return LinktoCadMap;
	}

	public void setLinktoCadMap(HashMap<LinkConfiguration, ArrayList<CSG>> linktoCadMap) {
		LinktoCadMap = linktoCadMap;
	}

	public boolean getAutoRegen() {
		return autoRegen;
	}

	public void setAutoRegen(boolean autoRegen) {
		this.autoRegen = autoRegen;
		for (MobileBaseCadManager m : slaves) {
			m.setAutoRegen(autoRegen);
		}
	}

	public IMobileBaseUI getUi() {
		return ui;
	}

	public void setUi(IMobileBaseUI ui) {
		this.ui = ui;
	}

	public void setConfigurationViewerMode(boolean b) {
		System.out.println("Setting config mode " + b);
		configMode = b;
		for (MobileBaseCadManager m : slaves) {
			m.setConfigurationViewerMode(b);
		}
	}

}
