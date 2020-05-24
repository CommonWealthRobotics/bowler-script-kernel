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
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.util.FileChangeWatcher;
import com.neuronrobotics.bowlerstudio.util.FileWatchDeviceWrapper;
import com.neuronrobotics.bowlerstudio.util.IFileChangeListener;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.ILinkListener;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.IDeviceConnectionEventListener;
import com.neuronrobotics.sdk.pid.PIDLimitEvent;
import com.neuronrobotics.sdk.util.ThreadUtil;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import javafx.beans.property.*;
import javafx.scene.transform.Affine;

public class MobileBaseCadManager {

	// static
	private static HashMap<MobileBase, MobileBaseCadManager> cadmap = new HashMap<>();
	// static
	private Object cadForBodyEngine;
	private MobileBase base;
	private ArrayList<MobileBaseCadManager> slaves = new ArrayList<MobileBaseCadManager>();
	private File cadScript;

	private HashMap<DHParameterKinematics, Object> dhCadGen = new HashMap<>();
	private HashMap<DHParameterKinematics, ArrayList<CSG>> DHtoCadMap = new HashMap<>();
	private HashMap<LinkConfiguration, ArrayList<CSG>> LinktoCadMap = new HashMap<>();
	private HashMap<MobileBase, ArrayList<CSG>> BasetoCadMap = new HashMap<>();

	private boolean cadGenerating = false;
	private boolean showingStl = false;
	private ArrayList<CSG> allCad;

	private boolean bail = false;
	private IMobileBaseUI ui = null;
	private static ICadGenerator cadEngineConfiguration=null;
	private boolean configMode=false;
	protected void clear() {
		// Cad generator
		dhCadGen.clear();
		//clear the csgs from the list
		for(DHParameterKinematics key: DHtoCadMap.keySet()) {
			ArrayList<CSG> arrayList = DHtoCadMap.get(key);
			if(arrayList!=null)arrayList.clear();
		}
		DHtoCadMap.clear();
		//celat csg from link conf list
		for(LinkConfiguration key: LinktoCadMap.keySet()) {
			ArrayList<CSG> arrayList = LinktoCadMap.get(key);
			if(arrayList!=null)arrayList.clear();
		}
		LinktoCadMap.clear();
		for(MobileBase key: BasetoCadMap.keySet()) {
			ArrayList<CSG> arrayList = BasetoCadMap.get(key);
			if(arrayList!=null)arrayList.clear();
		}
		BasetoCadMap.clear();
		if(allCad!=null)
			allCad.clear();
		Vitamins.clear();
		slaves.clear();
		
	}
	private static class  IMobileBaseUIlocal implements IMobileBaseUI{

		public ArrayList<CSG> list = new ArrayList<>();

		@Override
		public void highlightException(File fileEngineRunByName, Exception ex) {
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
	private IFileChangeListener cadWatcher = new IFileChangeListener() {

		@Override
		public void onFileChange(File fileThatChanged, WatchEvent event) {

			if (cadGenerating || !getAutoRegen()) {
				System.out.println("No Base reload, building currently");
				return;
			}
			try {
				new Thread() {
					public void run() {
						clear();
						ThreadUtil.wait((int) ((50 * Math.random()) + 50));
						try {

							System.out.println("Re-loading Cad Base Engine");
							cadForBodyEngine = ScriptingEngine.inlineFileScriptRun(fileThatChanged, null);
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						generateCad();
					}
				}.start();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	};
	private boolean autoRegen = true;
	private DoubleProperty pi = new SimpleDoubleProperty(0);

	private MobileBaseCadManager(MobileBase base, IMobileBaseUI myUI) {
		this.setUi(myUI);
		base.addConnectionEventListener(new IDeviceConnectionEventListener() {

			@Override
			public void onDisconnect(BowlerAbstractDevice arg0) {
				
				if (arg0 !=base)
					return;
				bail = true;
				clear();
				cadmap.remove(base);
			}

			@Override
			public void onConnect(BowlerAbstractDevice arg0) {
				// TODO Auto-generated method stub

			}
		});
		setMobileBase(base);
		for(DHParameterKinematics kin:base.getAllDHChains()) {
	    	for(int i=0;i<kin.getNumberOfLinks();i++) {
	    		MobileBase m = kin.getDhLink(i).getSlaveMobileBase();
	    		if(m!=null) {
	    			m.setGitSelfSource(base.getGitSelfSource());
	    			slaves.add(new MobileBaseCadManager(m, myUI));
	    		}
	    	}
	    }
	}

	public File getCadScript() {
		return cadScript;
	}

	public void setCadScript(File cadScript) {
		if (cadScript == null)
			return;
		FileWatchDeviceWrapper.watch(base, cadScript, cadWatcher);

		this.cadScript = cadScript;
	}

	private IgenerateBody getIgenerateBody() {
		if(configMode)
			return getConfigurationDisplay();
		if (IgenerateBody.class.isInstance(cadForBodyEngine)) {
			return (IgenerateBody) cadForBodyEngine;
		}
		return null;
	}

	private IgenerateCad getIgenerateCad() {
		if(configMode)
			return getConfigurationDisplay();
		if (IgenerateCad.class.isInstance(cadForBodyEngine)) {
			return (IgenerateCad) cadForBodyEngine;
		}
		return null;
	}

	private IgenerateBed getIgenerateBed() {
		if (IgenerateBed.class.isInstance(cadForBodyEngine)) {
			return (IgenerateBed) cadForBodyEngine;
		}
		return null;
	}
	
	private static ICadGenerator getConfigurationDisplay() {
		if(cadEngineConfiguration==null) {
			try {
				File confFile = resetConfigurationScript();
				FileChangeWatcher watcher = FileChangeWatcher.watch(confFile);
				watcher.addIFileChangeListener(new IFileChangeListener() {
					
					@Override
					public void onFileChange(File fileThatChanged, WatchEvent event) {
						// TODO Auto-generated method stub
						try {
							resetConfigurationScript();
							for(MobileBase manager : cadmap.keySet()) {
								MobileBaseCadManager mobileBaseCadManager = cadmap.get(manager);
								if(mobileBaseCadManager.autoRegen)
									if(mobileBaseCadManager.configMode)
										mobileBaseCadManager.generateCad() ;
							}
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				});
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return cadEngineConfiguration;
	}

	private static File resetConfigurationScript()
			throws InvalidRemoteException, TransportException, GitAPIException, IOException, Exception {
		File confFile = ScriptingEngine.fileFromGit("https://github.com/CommonWealthRobotics/DHParametersCadDisplay.git", "dhcad.groovy");
		cadEngineConfiguration=(ICadGenerator) ScriptingEngine.inlineFileScriptRun(confFile, null);
		return confFile;
	}

	public ArrayList<CSG> generateBody(MobileBase base) {

		getProcesIndictor().set(0);
		setAllCad(new ArrayList<>());
		// DHtoCadMap = new HashMap<>();
		// private HashMap<MobileBase, ArrayList<CSG>> BasetoCadMap = new
		// HashMap<>();

		MobileBase device = base;
		if (getBasetoCadMap().get(device) == null) {
			getBasetoCadMap().put(device, new ArrayList<CSG>());
		}

		if (cadForBodyEngine == null) {
			try {
				setDefaultLinkLevelCadEngine();
			} catch (Exception e) {
				getUi().highlightException(null, e);
			}
			if (getCadScript() != null) {
				try {
					cadForBodyEngine = ScriptingEngine.inlineFileScriptRun(getCadScript(), null);
				} catch (Exception e) {
					getUi().highlightException(getCadScript(), e);
				}
			}
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
					ArrayList<CSG> newcad = getIgenerateBody().generateBody(device);
					for (CSG c : newcad) {
						getAllCad().add(c);
					}
					ui.addCSG(newcad, getCadScript());
				} else
					new Exception().printStackTrace();
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
			getUi().highlightException(getCadScript(), e);
		}
		System.out.println("Displaying Body");
		getProcesIndictor().set(0.35);
		// clears old robot and places base
		getUi().setAllCSG(getBasetoCadMap().get(device), getCadScript());
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
			if (showingStl || !device.isAvailable()) {
				for (CSG csg : arrayList) {
					getAllCad().add(csg);
					getUi().addCsg(csg, getCadScript());
					set(base, (int) i, (int) j);
					j += 1;
				}
			} else {

				arrayList.clear();
				ArrayList<CSG> linksCad = generateCad(l);

				for (CSG csg : linksCad) {

					getAllCad().add(csg);
					arrayList.add(csg);
					getUi().addCsg(csg, getCadScript());
					j += 1;

				}

			}

			i += 1;

		}
		for(MobileBaseCadManager m:slaves) {
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

	private void set(MobileBase base, int limb, int link) {
		ArrayList<DHParameterKinematics> limbs = base.getAllDHChains();
		double numLimbs = limbs.size();
		DHParameterKinematics dh = limbs.get(limb);
		double partsTotal = numLimbs * dh.getNumberOfLinks();
		double progress = ((double) ((limb * dh.getNumberOfLinks()) + link)) / partsTotal;
		//System.out.println("Cad progress " + progress + " limb " + limb + " link " + link + " total parts " + partsTotal);
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
		getUi().setAllCSG(totalAssembly, getCadScript());
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
						getUi().setAllCSG(totalAssembly, getCadScript());
						set(base, i, j);
					}
				} catch (Exception ex) {
					getUi().highlightException(getCadScript(), ex);
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
					getUi().setAllCSG(totalAssembly, getCadScript());
					link++;
				}
			} catch (Exception ex) {
				getUi().highlightException(getCadScript(), ex);
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

	public void setMobileBase(MobileBase base) {
		this.base = base;
		cadmap.put(base, this);
		MobileBaseLoader.get(base);// load the dependant scripts
		base.updatePositions();

	}

	/**
	 * This function iterates through the links generating them
	 * 
	 * @param dh
	 * @return
	 */
	public ArrayList<CSG> generateCad(DHParameterKinematics dh) {
		ArrayList<CSG> dhLinks = new ArrayList<>();

		if (dhCadGen.get(dh) == null) {
			try {
				setDefaultLinkLevelCadEngine();
			} catch (Exception e) {
				getUi().highlightException(getCadScript(), e);
			}
		}

		try {
			IgenerateCad generatorToUse = getConfigurationDisplay();
			Object object = dhCadGen.get(dh);
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
					ArrayList<CSG> tmp = generatorToUse.generateCad(dh, i);
					getUi().addCSG(tmp, getCadScript());
					LinkConfiguration configuration = dh.getLinkConfiguration(i);
					if (getLinktoCadMap().get(configuration) == null) {
						getLinktoCadMap().put(configuration, new ArrayList<>());
					} else
						getLinktoCadMap().get(configuration).clear();
					for (CSG c : tmp) {
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

				}
			}
			return dhLinks;
		} catch (Exception e) {
			getUi().highlightException(getCadScript(), e);
		}
		return null;

	}

	public void selectCsgByMobileBase(MobileBase base) {
		try {

			ArrayList<CSG> csg = MobileBaseCadManager.get(base).getBasetoCadMap().get(base);
			getUi().setSelectedCsg(csg);
		} catch (Exception ex) {
			System.err.println("Base not loaded yet");
		}

	}

	public void selectCsgByLimb(MobileBase base, DHParameterKinematics limb) {
		try {

//			ArrayList<CSG> limCad = MobileBaseCadManager.get(base).getDHtoCadMap().get(limb);
//			getUi().setSelectedCsg(limCad);
			getUi().setSelected(limb.getRootListener());
		} catch (Exception ex) {
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
				//Thread.currentThread().setUncaughtExceptionHandler(new IssueReportingExceptionHandler());

				System.out.print("\r\nGenerating CAD...\r\n");
				setName("MobileBaseCadManager Generating cad Thread ");
				// new Exception().printStackTrace();
				MobileBase device = base;
				MobileBaseCadManager.get(base).clear();
				
				try {
					setAllCad(generateBody(device));
				} catch (Exception e) {
					getUi().highlightException(getCadScript(), e);
				}
				// System.out.print("\r\nDone Generating CAD!\r\n");
				getUi().setCsg(MobileBaseCadManager.get(base), getCadScript());
				cadGenerating = false;
				System.out.print("\r\nDone Generating CAD!\r\n");
				getProcesIndictor().set(1);
				System.gc();
			}
		}.start();
	}



	private void setDefaultLinkLevelCadEngine() throws Exception {
		String[] cad;
		cad = base.getGitCadEngine();

		if (cadForBodyEngine == null) {
			setGitCadEngine(cad[0], cad[1], base);
		}
		for (DHParameterKinematics kin : base.getAllDHChains()) {
			String[] kinEng = kin.getGitCadEngine();
			if (!cad[0].contentEquals(kinEng[0]) || !cad[1].contentEquals(kinEng[1])) {
				setGitCadEngine(kinEng[0], kinEng[1], kin);
			}else {
				dhCadGen.put(kin, cadForBodyEngine);
			}
		}
	}

	public void onTabClosing() {

	}

	public void setGitCadEngine(String gitsId, String file, DHParameterKinematics dh)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		dh.setGitCadEngine(new String[] { gitsId, file });
		File code = ScriptingEngine.fileFromGit(gitsId, file);
		try {
			Object defaultDHSolver = ScriptingEngine.inlineFileScriptRun(code, null);
			dhCadGen.put(dh, defaultDHSolver);
		} catch (Exception e) {
			getUi().highlightException(code, e);
		}

		FileWatchDeviceWrapper.watch(dh, code, (fileThatChanged, event) -> {
			System.out.println("Re-loading Cad Limb Engine");

			try {
				Object d = ScriptingEngine.inlineFileScriptRun(code, null);
				dhCadGen.put(dh, d);
				generateCad();
			} catch (Exception ex) {
				getUi().highlightException(code, ex);
			}
		});
	}

	public void setGitCadEngine(String gitsId, String file, MobileBase device)
			throws InvalidRemoteException, TransportException, GitAPIException, IOException {
		setCadScript(ScriptingEngine.fileFromGit(gitsId, file));
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

		if(this.allCad!=null && this.allCad!=allCad)
			this.allCad.clear();
		this.allCad = allCad;
	}
	public static MobileBaseCadManager get(MobileBase device,IMobileBaseUI ui) {
		if (cadmap.get(device) == null) {
			// new RuntimeException("No Mobile Base Cad Manager UI
			// specified").printStackTrace();
			MobileBaseCadManager mbcm = new MobileBaseCadManager(device,ui );
			cadmap.put(device, mbcm);
		}
		MobileBaseCadManager mobileBaseCadManager = cadmap.get(device);
		if(!IMobileBaseUIlocal.class.isInstance(ui)&&
			IMobileBaseUIlocal.class.isInstance(mobileBaseCadManager.getUi())	) 
			mobileBaseCadManager.setUi(ui);

		return mobileBaseCadManager;
	}
	public static MobileBaseCadManager get(MobileBase device) {
		if (cadmap.get(device) == null) {
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
			return get(device,ui2);
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
	}

	public IMobileBaseUI getUi() {
		return ui;
	}

	public void setUi(IMobileBaseUI ui) {
		this.ui = ui;
	}

	public void setConfigurationViewerMode(boolean b) {
		System.out.println("Setting config mode "+b);
		configMode=b;
	}

}
