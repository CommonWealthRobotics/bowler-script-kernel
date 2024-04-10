package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.IssueReportingExceptionHandler;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.printbed.PrintBedManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.util.FileChangeWatcher;
import com.neuronrobotics.bowlerstudio.util.FileWatchDeviceWrapper;
import com.neuronrobotics.bowlerstudio.util.IFileChangeListener;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.bowlerstudio.vitamins.Vitamins;
import com.neuronrobotics.sdk.addons.kinematics.AbstractKinematicsNR;
import com.neuronrobotics.sdk.addons.kinematics.AbstractLink;
import com.neuronrobotics.sdk.addons.kinematics.DHLink;
import com.neuronrobotics.sdk.addons.kinematics.DHParameterKinematics;
import com.neuronrobotics.sdk.addons.kinematics.ILinkConfigurationChangeListener;
import com.neuronrobotics.sdk.addons.kinematics.ILinkListener;
import com.neuronrobotics.sdk.addons.kinematics.IOnMobileBaseRenderChange;
import com.neuronrobotics.sdk.addons.kinematics.IRegistrationListenerNR;
import com.neuronrobotics.sdk.addons.kinematics.ITaskSpaceUpdateListenerNR;
import com.neuronrobotics.sdk.addons.kinematics.IVitaminHolder;
import com.neuronrobotics.sdk.addons.kinematics.LinkConfiguration;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.VitaminFrame;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.addons.kinematics.parallel.ParallelGroup;
import com.neuronrobotics.sdk.common.BowlerAbstractDevice;
import com.neuronrobotics.sdk.common.IDeviceConnectionEventListener;
import com.neuronrobotics.sdk.pid.PIDLimitEvent;
import com.neuronrobotics.sdk.util.ThreadUtil;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
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
	private HashMap<VitaminLocation,CSG> vitaminCad=new HashMap<>();
	private HashMap<VitaminLocation,CSG> vitaminDisplay=new HashMap<>();
	private HashMap<VitaminLocation,Affine> vitaminLocation=new HashMap<>();


	private boolean cadGenerating = false;
	private boolean showingStl = false;
	private ArrayList<CSG> allCad = new ArrayList<>();

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
	private static ArrayList<Runnable> toRun = new ArrayList<Runnable>();
	private ArrayList<IRenderSynchronizationEvent> rendersync=new ArrayList<>();
	private boolean forceChage = true;
	public CSG getVitamin(VitaminLocation vitamin) throws Exception {
		return getVitamin(vitamin,new Affine(),null);
	}
	public CSG getVitamin(VitaminLocation vitamin,Affine manipulator,TransformNR offset)  {
		if(!vitaminCad.containsKey(vitamin)) {
			CSG starting;
			try {
				starting = Vitamins.get(vitamin.getType(), vitamin.getSize());
				if(offset!=null)
					starting=starting.transformed(TransformFactory.nrToCSG(offset));
				starting=starting.transformed(TransformFactory.nrToCSG(vitamin.getLocation()));
				starting.setIsWireFrame(true);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
			vitaminCad.put(vitamin, starting);
			starting.setManipulator(manipulator);
		}
		return vitaminCad.get(vitamin);
	}
	public ArrayList<CSG> getVitamins(IVitaminHolder link,Affine manipulator) {
		ArrayList<VitaminLocation> vitamins = link.getVitamins();
		return toVitaminCad(vitamins,manipulator,null);
	}
	public  ArrayList<CSG>  getOriginVitamins(IVitaminHolder link,Affine manipulator,TransformNR offset){
		ArrayList<VitaminLocation> vitamins = link.getOriginVitamins();
		return toVitaminCad(vitamins,manipulator,offset);
	}
	public  ArrayList<CSG>  getDefaultVitamins(IVitaminHolder link,Affine manipulator){
		ArrayList<VitaminLocation> vitamins = link.getDefaultVitamins();
		return toVitaminCad(vitamins,manipulator,null);
	}
	public ArrayList<CSG>  getPreviousLinkVitamins(IVitaminHolder link,Affine manipulator){
		ArrayList<VitaminLocation> vitamins = link.getPreviousLinkVitamins();
		return toVitaminCad(vitamins,manipulator,null);
	}


	private ArrayList<CSG> toVitaminCad(ArrayList<VitaminLocation> vitamins,Affine manipulator, TransformNR offset) {
		ArrayList<CSG> parts = new ArrayList<CSG>();
		for(VitaminLocation vi:vitamins) {
			CSG vitamin;
			try {
				vitamin = getVitamin(vi,manipulator,offset);
				parts.add(vitamin);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return parts;
	}
	
	
	public ArrayList<CSG> getVitamins(AbstractLink link) {
		LinkConfiguration conf = link.getLinkConfiguration();
		return getVitamins(conf, (Affine) link.getGlobalPositionListener());
	}

	
	public ArrayList<CSG> getVitamins(AbstractLink link,Affine manipulator) {
		LinkConfiguration conf = link.getLinkConfiguration();
		return getVitamins(conf, manipulator);
	}
	public ArrayList<CSG> getVitamins(MobileBase base) {
		Affine rootListener = (Affine) base.getRootListener();
		return getVitamins(base, rootListener);
	}
	
	public CSG getVitaminDisplay(VitaminLocation vitamin,Affine manipulator, TransformNR offset){
		if(!vitaminDisplay.containsKey(vitamin)) {
			CSG starting;
			try {
				starting = Vitamins.get(vitamin.getType(), vitamin.getSize());
				if(offset!=null)
					starting=starting.transformed(TransformFactory.nrToCSG(offset));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return null;
			}
			starting.setManipulator(manipulator);
			Affine frameOffset=new Affine();
			BowlerKernel.runLater(() -> {
				TransformFactory.nrToAffine(vitamin.getLocation(), frameOffset);
			});
			vitaminLocation.put(vitamin, frameOffset);
			vitamin.addChangeListener(()->{
				//System.out.println(vitamin.getName()+" changed to "+vitamin.getLocation().toSimpleString());
				BowlerKernel.runLater(()->{
					TransformFactory.nrToAffine(vitamin.getLocation(),frameOffset);
				});
			});
			starting.getMesh().getTransforms().add(frameOffset);
			vitaminDisplay.put(vitamin, starting);
		}
		return vitaminDisplay.get(vitamin);
	}

	public Affine getVitaminAffine(VitaminLocation vitamin) {
		Affine a = vitaminLocation.get(vitamin);
		if(a!=null)
			return a;
		throw new RuntimeException("Affine not present! "+vitamin.getName());
	}
	
	public  ArrayList<CSG>  getOriginVitaminsDisplay(IVitaminHolder link,Affine manipulator,TransformNR offset){
		ArrayList<VitaminLocation> vitamins = link.getOriginVitamins();
		return vitaminsToDisplay(vitamins,manipulator,offset);
	}
	public  ArrayList<CSG>  getDefaultVitaminsDisplay(IVitaminHolder link,Affine manipulator){
		ArrayList<VitaminLocation> vitamins = link.getDefaultVitamins();
		return vitaminsToDisplay(vitamins,manipulator);
	}
	public ArrayList<CSG>  getPreviousLinkVitaminsDisplay(IVitaminHolder link,Affine manipulator){
		ArrayList<VitaminLocation> vitamins = link.getPreviousLinkVitamins();
		return vitaminsToDisplay(vitamins,manipulator);
	}
	
	public ArrayList<CSG> getVitaminsDisplay(IVitaminHolder link,Affine manipulator) {
		
		return vitaminsToDisplay(link.getVitamins(),manipulator);
	}
	
	public ArrayList<CSG> vitaminsToDisplay(ArrayList<VitaminLocation> l,Affine manipulator){
		return vitaminsToDisplay(l,manipulator,null);
	}
	public ArrayList<CSG> vitaminsToDisplay(ArrayList<VitaminLocation> l,Affine manipulator, TransformNR offset){
		ArrayList<CSG> parts = new ArrayList<CSG>();
		for(VitaminLocation vi:l) {
			CSG vitamin;
			try {
				vitamin = getVitaminDisplay(vi,manipulator,  offset);
				parts.add(vitamin);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return parts;
	}
	
	public ArrayList<CSG> getVitaminsDisplay(AbstractLink link) {
		LinkConfiguration conf = link.getLinkConfiguration();
		return getVitaminsDisplay(conf, (Affine) link.getGlobalPositionListener());
	}
	public ArrayList<CSG> getVitaminsDisplay(AbstractLink link,Affine manipulator) {
		LinkConfiguration conf = link.getLinkConfiguration();
		return getVitaminsDisplay(conf, manipulator);
	}

	
	public ArrayList<CSG> getVitaminsDisplay(MobileBase base) {
		Affine rootListener = (Affine) base.getRootListener();
		return getVitaminsDisplay(base, rootListener);
	}
	
	public void render() {
		run();
		forceChage=true;
		while(forceChage)
			ThreadUtil.wait(1);
	}
	public void addIRenderSynchronizationEvent(IRenderSynchronizationEvent ev) {
		if(rendersync.contains(ev))
			return;
		rendersync.add(ev);
	}
	public void removeIRenderSynchronizationEvent(IRenderSynchronizationEvent ev) {
		if(rendersync.contains(ev))
			rendersync.remove(ev);
	}
	
	private void fireIRenderSynchronizationEvent() {
		for(int i=0;i<rendersync.size();i++)
			try {
				rendersync.get(i).event(this);
			}catch(Exception e) {
				
			}
	}
	/**
	 * get the progress of the cad in an integer percent
	 * @return percent completion of CAD
	 */
	public int getCADProgressPercent() {
		return (int)(Math.round(getProcesIndictor().get()*100));
	}
	
	public boolean isCADFinished() {
		return getCADProgressPercent()==100;
	}
	public Vector3d computeHighestPoint() {
		render();
		MobileBase cat=base;
		MobileBaseCadManager cadMan = MobileBaseCadManager.get(cat);
		Vector3d highest =null;
		Affine l =(Affine) cat.getRootListener();
		TransformNR tmp = TransformFactory.affineToNr(l);
		Transform baseloc = TransformFactory.nrToCSG(tmp);
		for(CSG c:cadMan.getBasetoCadMap().get(cat)) {

			CSG moved = c.transformed(baseloc);
			double z = moved.getMaxZ();
			double y= moved.getMaxY();
			double x=moved.getMaxX();
			Vector3d high = new Vector3d(x, y, z);
			
			if(highest==null)
				highest=high;
			if(high.x>highest.x)
				highest.x=high.x;
			if(high.y>highest.y)
				highest.y=high.y;
			if(high.z>highest.z)
				highest.z=high.z;
		}
		for(DHParameterKinematics k:cat.getAllDHChains()) {
			for(int i=0;i<k.getNumberOfLinks();i++) {
				DHLink dhLink = k.getChain().getLinks().get(i);
				Affine a = (Affine) dhLink.getListener();
				TransformNR tmpl = TransformFactory.affineToNr(a);
				Transform t=TransformFactory.nrToCSG(tmpl);
				LinkConfiguration conf= k.getLinkConfiguration(i);
				for(CSG c:cadMan.getLinktoCadMap().get(conf)) {
					CSG moved = c.transformed(t);
					double z = moved.getMaxZ();
					double y= moved.getMaxY();
					double x=moved.getMaxX();
					Vector3d high = new Vector3d(x, y, z);
					
					if(highest==null)
						highest=high;
					if(high.x>highest.x)
						highest.x=high.x;
					if(high.y>highest.y)
						highest.y=high.y;
					if(high.z>highest.z)
						highest.z=high.z;
				}
			}
		}
		return highest;
	}
	public Vector3d computeLowestPoint() {
		render();
		MobileBase cat=base;
		MobileBaseCadManager cadMan = MobileBaseCadManager.get(cat);
		Vector3d lowest =null;
		Affine l =(Affine) cat.getRootListener();
		TransformNR tmp = TransformFactory.affineToNr(l);
		Transform baseloc = TransformFactory.nrToCSG(tmp);
		for(CSG c:cadMan.getBasetoCadMap().get(cat)) {

			CSG moved = c.transformed(baseloc);
			double z = moved.getMinZ();
			double y= moved.getMinY();
			double x=moved.getMinX();
			Vector3d low = new Vector3d(x, y, z);
			
			if(lowest==null)
				lowest=low;
			if(low.x<lowest.x)
				lowest.x=low.x;
			if(low.y<lowest.y)
				lowest.y=low.y;
			if(low.z<lowest.z)
				lowest.z=low.z;
		}
		for(DHParameterKinematics k:cat.getAllDHChains()) {
			for(int i=0;i<k.getNumberOfLinks();i++) {
				DHLink dhLink = k.getChain().getLinks().get(i);
				Affine a = (Affine) dhLink.getListener();
				TransformNR tmpl = TransformFactory.affineToNr(a);
				Transform t=TransformFactory.nrToCSG(tmpl);
				LinkConfiguration conf= k.getLinkConfiguration(i);
				for(CSG c:cadMan.getLinktoCadMap().get(conf)) {
					CSG moved = c.transformed(t);
					double z = moved.getMinZ();
					double y= moved.getMinY();
					double x=moved.getMinX();
					Vector3d low = new Vector3d(x, y, z);
					
					if(lowest==null)
						lowest=low;
					if(low.x<lowest.x)
						lowest.x=low.x;
					if(low.y<lowest.y)
						lowest.y=low.y;
					if(low.z<lowest.z)
						lowest.z=low.z;
				}
			}
		}
		return lowest;
	}
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
		vitaminCad.clear();
		vitaminDisplay.clear();
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

	public static void runLater(Runnable r) {
		if (r == null)
			throw new NullPointerException();
		toRun.add(r);
	}

	// This is the rendering event
	public void run() {
		// rendering = true;
		// String name = base.getScriptingName();
		if (renderWrangler == null) {
			renderWrangler = new Thread() {
				HashMap<DHParameterKinematics, double[]> jointPoses = new HashMap<>();
				HashMap<Affine, TransformNR> tmp = new HashMap<>();
				boolean rendering = false;
				boolean changed = false;

				IOnMobileBaseRenderChange l = new IOnMobileBaseRenderChange() {
					@Override
					public void onIOnMobileBaseRenderChange() {
						synchronized (jointPoses) {
							loadJointPose(base, jointPoses);
							changed = true;
						}

					}
				};
				IRegistrationListenerNR r = new IRegistrationListenerNR() {
					@Override
					public void onFiducialToGlobalUpdate(AbstractKinematicsNR source, TransformNR regestration) {
						l.onIOnMobileBaseRenderChange();
					}

					@Override
					public void onBaseToFiducialUpdate(AbstractKinematicsNR source, TransformNR regestration) {
						l.onIOnMobileBaseRenderChange();
					}
				};
				ILinkConfigurationChangeListener confL= new ILinkConfigurationChangeListener() {
					@Override
					public void event(LinkConfiguration newConf) {
						l.onIOnMobileBaseRenderChange();
					}
					
				};
				@Override
				public void run() {
					base.addIOnMobileBaseRenderChange(l);
//					base.addIHardwareSyncPulseReciver(() -> {
//						base.removeIOnMobileBaseRenderChange(l);
//						l.onIOnMobileBaseRenderChange();
//					});
					base.addRegistrationListener(r);
					for (DHParameterKinematics kin : base.getAllDHChains()) {
						kin.addRegistrationListener(r);
					}
					// render on any configuration change
					addConfL(base,confL);
					setName("MobileBaseCadManager Render Thread for " + base.getScriptingName());
					while (base.isAvailable()) {
						try {
							do {
								Thread.sleep(5);
								if(forceChage) {
									forceChage=false;
									l.onIOnMobileBaseRenderChange();
								}
							} while (rendering || changed == false);
						} catch (InterruptedException e) {
							getUi().highlightException(null, e);
							break;
						}
						try {
//							DecimalFormat df = new DecimalFormat("000.00");
//							for(DHParameterKinematics kin:jointPoses.keySet().stream()
//									.sorted((o1, o2) -> {
//							               return o1.getScriptingName().compareTo(o2.getScriptingName());
//							        })
//							        .collect(Collectors.toList())) {
//								double[] joints = jointPoses.get(kin);
//								System.err.print("\n"+kin.getScriptingName()+" \t[ ");
//								for(int i=0;i<kin.getNumberOfLinks();i++) {
//									System.err.print(" "+df.format(joints[i])+"\t");
//									
//								}
//								System.err.print("]");
//							}
//							System.err.print("\n\n");
							rendering = true;

							HashMap<DHParameterKinematics, double[]> jointPosesTmp;
							synchronized (jointPoses) {
								jointPosesTmp = jointPoses;
								jointPoses = new HashMap<>();
							}
							changed = false;
							updateMobileBase(base, base.getFiducialToGlobalTransform(), tmp, jointPosesTmp);
							jointPosesTmp.clear();
							jointPosesTmp = null;

							Affine[] iterator = tmp.keySet().stream().toArray(size->new Affine[size]);
							TransformNR[] vals = tmp.values().stream().toArray(size->new TransformNR[size]);
							tmp.clear();
							if (iterator.length > 0) {
								Platform.runLater(() -> {
									try {
										for (int i = 0; i < iterator.length; i++) {
											Affine af = iterator[i];
											TransformFactory.nrToAffine(vals[i], af);
										}
									} catch (Throwable t) {
										t.printStackTrace();
									}
									rendering = false;
									fireIRenderSynchronizationEvent();
								});
								Thread.sleep(32);

							} else {
								rendering = false;
							}

							if (toRun.size() > 0) {
								ArrayList<Runnable> t = toRun;
								toRun = new ArrayList<Runnable>();
								Platform.runLater(() -> {
									try {
										for (int i = 0; i < t.size(); i++) {
											Runnable runnable = t.get(i);
											if (runnable != null)
												runnable.run();
										}
										t.clear();
									} catch (Throwable tr) {
										tr.printStackTrace();
									}
								});
								Thread.sleep(32);
							}
						} catch (Throwable t) {
							// rendering not availible
							System.err.println("Exception for render engine " + base.getScriptingName());
							t.printStackTrace();
							try {
								Thread.sleep(100);
							} catch (InterruptedException e) {
								break;
							}
						}
						
					}
					renderWrangler = null;
				}
				private void addConfL(MobileBase base, ILinkConfigurationChangeListener confL2) {
					if(base==null)
						return;
					for(DHParameterKinematics k:base.getAllDHChains()) {
						for(int i=0;i<k.getNumberOfLinks();i++) {
							k.getAbstractLink(i).addChangeListener(confL2);
							addConfL(k.getSlaveMobileBase(i),confL2);
						}
					}
				}

			};
			renderWrangler.start();
		}

	}

	private void loadJointPose(MobileBase base, HashMap<DHParameterKinematics, double[]> jointPoses) {
		for (DHParameterKinematics k : base.getAllDHChains()) {
//			ParallelGroup p = base.getParallelGroup(k);
//			if(p==null) {
			jointPoses.put(k, k.getCurrentJointSpaceVector());
//			}else
//				jointPoses.put(k, p.getCurrentJointSpaceVector(k));
			for (int i = 0; i < k.getNumberOfLinks(); i++) {
				MobileBase mb = k.getSlaveMobileBase(i);
				if (mb != null) {
					loadJointPose(mb, jointPoses);
				}
			}
		}
	}

	private void updateMobileBase(MobileBase b, TransformNR baseLoc, HashMap<Affine, TransformNR> map2,
			HashMap<DHParameterKinematics, double[]> jointPoses) {
		TransformNR back =updateBase(b, baseLoc, map2);
		for (DHParameterKinematics k : b.getAllDHChains()) {
			updateLimb(k, back, map2, jointPoses);
		}

	}

	private void updateLimb(DHParameterKinematics k, TransformNR baseLoc, HashMap<Affine, TransformNR> map2,
			HashMap<DHParameterKinematics, double[]> jointPoses) {
		//updateBase(k, baseLoc, map2);
		TransformNR previous = k.getFiducialToGlobalTransform();
		k.setGlobalToFiducialTransform(baseLoc, false);
		ArrayList<TransformNR> ll = k.getChain().getChain(jointPoses.get(k));

		for (int i = 0; i < ll.size(); i++) {
			int index = i;
			Affine a;
			DHLink dhLink = k.getChain().getLinks().get(index);
			if (dhLink.getListener() == null) {
				dhLink.setListener(new Affine());
			}
			try {
				a = (Affine) dhLink.getListener();
			} catch (java.lang.ClassCastException ex) {
				a = new Affine();
				dhLink.setListener(a);
			}
			AbstractLink abstractLink = k.getAbstractLink(i);
			if (abstractLink.getGlobalPositionListener() == null) {
				abstractLink.setGlobalPositionListener(a);
			}

			Affine af = a;
			TransformNR nr = ll.get(index);
			if (nr != null && af != null)
				map2.put(af, nr);
			MobileBase slv = k.getSlaveMobileBase(i);
			if (slv!= null) {
				//slv.setRootListener(af);
				updateMobileBase(slv, nr, map2, jointPoses);
			}
		}
		k.setGlobalToFiducialTransform(previous, false);
	}

	private TransformNR updateBase(MobileBase base, TransformNR baseLoc, HashMap<Affine, TransformNR> map2) {
		if (base == null)
			return null;
		TransformNR previous = base.getFiducialToGlobalTransform();
		
		try {
			base.setGlobalToFiducialTransform(baseLoc, false);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("MB " + base.getScriptingName() + ", " + e.getMessage());
		}

		TransformNR forwardOffset = base.forwardOffset(new TransformNR());
		if (base.getRootListener() == null) {
			base.setRootListener(new Affine());
		}
		if (forwardOffset != null )
			map2.put((Affine) base.getRootListener(), forwardOffset);
		for (DHParameterKinematics k : base.getAllDHChains()) {
			if (k.getRootListener() == null) {
				k.setRootListener(new Affine());
			}
			TransformNR fo = k.forwardOffset(new TransformNR());
			if (fo != null )
				map2.put((Affine) k.getRootListener(), fo);
		}
		base.setGlobalToFiducialTransform(previous, false);
		return forwardOffset;
	}

	private MobileBaseCadManager(MobileBase base, IMobileBaseUI myUI) {
		this.setUi(myUI);
		setMobileBase(base);
	}


	private Object scriptFromFileInfo(String name,String[] args, Runnable runner) throws Throwable{
		String key = args[0] + ":" + args[1];
		try {
			File f = ScriptingEngine.fileFromGit(args[0], args[1]);
			if (cadScriptCache.get(key) == null) {
				build(key, f);
				FileChangeWatcher watcher = FileChangeWatcher.watch(f);
				Exception ex = new Exception("CAD script declared here and regenerated");
				IFileChangeListener l = new IFileChangeListener() {

					@Override
					public void onFileChange(File fileThatChanged, WatchEvent event) {
						if(cadGenerating)
							return;
						try {
							System.err.println("Clearing the compiled CAD script for " + key);
							cadScriptCache.remove(key);
							try {
								build(key, f);
							} catch (Throwable e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							//ex.printStackTrace();
							runner.run();
						} catch (Exception e) {
							getUi().highlightException(f, e);
						}
					}

					@Override
					public void onFileDelete(File fileThatIsDeleted) {
						cadScriptCache.remove(key);
					}
				};
				watcher.addIFileChangeListener(l);
				base.addConnectionEventListener(new IDeviceConnectionEventListener() {
					
					@Override
					public void onDisconnect(BowlerAbstractDevice source) {
						watcher.removeIFileChangeListener(l);
					}
					
					@Override
					public void onConnect(BowlerAbstractDevice source) {
						// TODO Auto-generated method stub
						
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
	private void build(String key, File f) throws Throwable {
		try {
			System.err.println(
					"Building the compiled CAD script for " + key + " " + base + " " + base.getScriptingName());
			cadScriptCache.put(key, ScriptingEngine.inlineFileScriptRun(f, null));
		} catch (Throwable e) {
			getUi().highlightException(f, e);
			throw e;
		}
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

	private IgenerateBody getIgenerateBody(MobileBase b) throws Throwable{
		if (isConfigMode())
			return getConfigurationDisplay();
		Object cadForBodyEngine = scriptFromFileInfo(b.getScriptingName(),b.getGitCadEngine(), () -> {
			run();
			generateCad();
		});
		if (IgenerateBody.class.isInstance(cadForBodyEngine)) {
			return (IgenerateBody) cadForBodyEngine;
		}
		return null;
	}

	private IgenerateCad getIgenerateCad(DHParameterKinematics dh) throws Throwable{
		if (isConfigMode())
			return getConfigurationDisplay();
		Object cadForBodyEngine = scriptFromFileInfo(dh.getScriptingName(),dh.getGitCadEngine(), () -> {
			run();
			generateCad();
		});
		if (IgenerateCad.class.isInstance(cadForBodyEngine)) {
			return (IgenerateCad) cadForBodyEngine;
		}
		return null;
	}

	public IgenerateBed getIgenerateBed() throws Throwable{
		Object cadForBodyEngine = scriptFromFileInfo(base.getScriptingName(),base.getGitCadEngine(), () -> {
			run();
		});
		if (IgenerateBed.class.isInstance(cadForBodyEngine)) {
			return (IgenerateBed) cadForBodyEngine;
		}
		return null;
	}

	private ICadGenerator getConfigurationDisplay()throws Throwable {
		if (cadEngineConfiguration == null) {
			String[] args = new String[] {
					"https://github.com/CommonWealthRobotics/DHParametersCadDisplay.git", "dhcad.groovy" };
			Object cadForBodyEngine = scriptFromFileInfo("ConfigDisplay", args, () -> {
				cadEngineConfiguration = null;
				try {
					getConfigurationDisplay();
				} catch (Throwable e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				MobileBaseCadManager mobileBaseCadManager = null;
				try {
					for (MobileBase manager : cadmap.keySet()) {
						mobileBaseCadManager = cadmap.get(manager);
						if (mobileBaseCadManager.autoRegen)
							if (mobileBaseCadManager.isConfigMode())
								mobileBaseCadManager.generateCad();
					}
				} catch (Exception e) {
					if (mobileBaseCadManager != null)
						mobileBaseCadManager.getUi().highlightException(null, e);
				}

			});
			if (ICadGenerator.class.isInstance(cadForBodyEngine))
				cadEngineConfiguration = (ICadGenerator) cadForBodyEngine;
		}
		return cadEngineConfiguration;
	}

	public ArrayList<CSG> generateBody() {
		return generateBody(getMobileBase(), true);
	}

	public ArrayList<CSG> generateBody(MobileBase base, boolean clear) {
		if (!base.isAvailable())
			throw new RuntimeException("Device " + base.getScriptingName() + " is not connected, can not generate cad");

		setProgress(0);
		if (clear) {
			getAllCad().clear();
			setAllCad(new ArrayList<>());
		}
		//System.gc();
		MobileBase device = base;
		if (getBasetoCadMap().get(device) == null) {
			getBasetoCadMap().put(device, new ArrayList<CSG>());
		}

		setProgress(0.1);
		try {

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
				if (clear) {
					arrayList.clear();
					//System.gc();
				}
				for (CSG c : getAllCad()) {
					arrayList.add(c);
				}
				new Thread(() -> {
					Thread.currentThread().setUncaughtExceptionHandler(new IssueReportingExceptionHandler());

					localGetBaseCad(device);// load the cad union in a thread to
											// make it ready for physics
				}).start();
			}
		} catch (Throwable e) {
			getUi().highlightException(getCadScriptFromMobileBase(device), e);
		}
		System.out.println("Displaying Body");
		setProgress(0.35);
		// clears old robot and places base
		getUi().setAllCSG(getBasetoCadMap().get(device), getCadScriptFromMobileBase(device));
		System.out.println("Rendering limbs");
		setProgress(0.4);
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
				if (clear) {
					arrayList.clear();
					//System.gc();
				}
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
			getAllCad().addAll(m.generateBody(m.base, false));
		}
		showingStl = false;
		//setProgress(1);
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
		if (limb >= numLimbs) {
			limb = numLimbs - 1;
		}
		DHParameterKinematics dh = limbs.get(limb);
		double partsTotal = numLimbs * dh.getNumberOfLinks();
		double progress = ((double) ((limb * dh.getNumberOfLinks()) + link)) / partsTotal;
		// System.out.println("Cad progress " + progress + " limb " + limb + " link " +
		// link + " total parts " + partsTotal);
		setProgress(0.333 + (2 * (progress / 3)));
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

	public ArrayList<File> generateStls(MobileBase base, File baseDirForFiles, boolean kinematic) throws Exception {
		File dir = new File(baseDirForFiles.getAbsolutePath() + "/" + base.getScriptingName());
		if (!dir.exists())
			dir.mkdirs();
		IgenerateBed bed=null;
		String baseURL = base.getGitSelfSource()[0];
		File baseWorkspaceFile = ScriptingEngine.getRepositoryCloneDirectory(baseURL);
		bed = getPrintBed(dir, bed, baseWorkspaceFile);
		if (bed == null || kinematic) {
			return _generateStls(base, dir, kinematic);
		}
		
		System.out.println("Found arrangeBed API in CAD engine");
		List<CSG> totalAssembly = bed.arrangeBed(base);
		getUi().setAllCSG(totalAssembly, getCadScriptFromMobileBase(base));


		return new CadFileExporter(getUi()).generateManufacturingParts(totalAssembly, dir);
	}
	public IgenerateBed getPrintBed(File baseDirForFiles, IgenerateBed bed, File baseWorkspaceFile) throws IOException {
		File bomCSV = new File(baseWorkspaceFile.getAbsolutePath()+"/"+VitaminBomManager.MANUFACTURING_BOM_CSV);
		if(bomCSV.exists()) {
			
			File file = new File(baseDirForFiles.getAbsolutePath()+"/bom.csv");
			if(file.exists())
				file.delete();
			Files.copy(bomCSV.toPath(),file.toPath());
		}
		File bom = new File(baseWorkspaceFile.getAbsolutePath()+"/"+VitaminBomManager.MANUFACTURING_BOM_JSON);
		if(bom.exists()) {
			File file = new File(baseDirForFiles.getAbsolutePath()+"/bom.json");
			if(file.exists())
				file.delete();
			Files.copy(bom.toPath(),file.toPath());
		}
		try{
		 bed= getIgenerateBed();
		}catch(Throwable T) {
			throw new RuntimeException(T.getMessage());
		}
		if(bed == null) {
			File printArrangment = new File(baseWorkspaceFile.getAbsolutePath()+"/"+PrintBedManager.file);
			if(printArrangment.exists()) {
				bed = new UserManagedPrintBed(printArrangment,this);
			}
		}
		return bed;
	}

	public ArrayList<File> _generateStls(MobileBase base, File baseDirForFiles, boolean kinematic) throws IOException {
		ArrayList<File> allCadStl = new ArrayList<>();
		ArrayList<DHParameterKinematics> limbs = base.getAllDHChains();
		double numLimbs = limbs.size();
		int i;
		// Start by generating the legs using the DH link based generator
		ArrayList<CSG> totalAssembly = new ArrayList<>();
		double offset = 0;
		for (i = 0; i < limbs.size(); i += 1) {

			double progress = (1.0 - ((numLimbs - i) / numLimbs)) / 2;
			setProgress(progress);

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
						if(conf!=null) {
							String linkNum = conf.getLinkIndex() + "_Link_";
	
							File dir = new File(baseDirForFiles.getAbsolutePath() + "/" + base.getScriptingName() + "/"
									+ l.getScriptingName());
							if (!dir.exists())
								dir.mkdirs();
							
							File stl = new File(
									dir.getAbsolutePath() + "/" + linkNum + name + "_limb_" + i + "_Part_" + j + ".stl");
							System.out.println("Writing STL for " + name+" to "+stl.getAbsolutePath());
							FileUtil.write(Paths.get(stl.getAbsolutePath()), tmp.toStlString());
							allCadStl.add(stl);
							// totalAssembly.add(tmp);
							getUi().setAllCSG(totalAssembly, getCadScriptFromMobileBase(base));
							set(base, i, j);
						}else {
							System.err.println("ERROR "+parts.get(j).getName()+" has no link associated ");
						}
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
		setProgress(1);
		return allCadStl;
	}

	public MobileBase getMobileBase() {
		return base;
	}

	public void setMobileBase(MobileBase b) {
		for (MobileBase mb : cadmap.keySet()) {
			if (mb == b)
				throw new RuntimeException("Can not duplicat mobile base");
			for (DHParameterKinematics dh : mb.getAllDHChains()) {
				for (int i = 0; i < dh.getNumberOfLinks(); i++) {
					if (dh.getSlaveMobileBase(i) == b)
						throw new RuntimeException("Can not duplicat mobile base!!");
				}
			}
		}
		this.base = b;
		cadmap.put(base, this);
		MobileBaseLoader.get(base);// load the dependant scripts
		base.updatePositions();
		base.setRenderWrangler(this);
		for (DHParameterKinematics k : base.getAllDHChains()) {
			k.setRenderWrangler(this);
		}
		base.setConfigurationUpdate(()->{
			generateCad();
		});
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
			if (object != null && !isConfigMode()) {
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
					if (dhl.getSlaveMobileBase() != null) {
						ArrayList<CSG> slParts = generateBody(dhl.getSlaveMobileBase(), false);
						dhLinks.addAll(slParts);
					}
					// ArrayList<CSG> generateBody(MobileBase base)
				}
			}

		} catch (Throwable e) {
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
		generateCadWithEnd((Runnable)null);
	}
	public void generateCadWithEnd(Runnable done) {
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
					setAllCad(generateBody(device, true));
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
				setProgress(1);
				if(done!=null) {
					try {
						done.run();
					}catch(Throwable t) {
						t.printStackTrace();
					}
				}
				System.gc();
			}
		}.start();
	}
	private void setProgress(double val) {
		if(cadGenerating==false) {
			val=1;
		}
		getProcesIndictor().set(val);
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

		}
		MobileBaseCadManager mobileBaseCadManager = cadmap.get(device);
		if (!IMobileBaseUIlocal.class.isInstance(ui)
				&& IMobileBaseUIlocal.class.isInstance(mobileBaseCadManager.getUi()))
			mobileBaseCadManager.setUi(ui);

		return mobileBaseCadManager;
	}



	public static MobileBaseCadManager get(MobileBase device) {
		if (cadmap.get(device) == null) {
			for (MobileBase mb : cadmap.keySet()) {
				for (DHParameterKinematics kin : mb.getAllDHChains()) {
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
		setConfigMode(b);
		for (MobileBaseCadManager m : slaves) {
			m.setConfigurationViewerMode(b);
		}
	}
	public void invalidateModelCache() {
		// TODO Auto-generated method stub
		
	}
	/**
	 * @return the configMode
	 */
	public boolean isConfigMode() {
		return configMode;
	}
	/**
	 * @param configMode the configMode to set
	 */
	public void setConfigMode(boolean configMode) {
		this.configMode = configMode;
	}
	public boolean isCADstarted() {
		// TODO Auto-generated method stub
		return cadGenerating;
	}

	public static MobileBaseCadManager get(IVitaminHolder holder) {
		Set<MobileBase> keySet = cadmap.keySet();
		for(MobileBase b:keySet) {
			MobileBaseCadManager m =checkforBase(holder, b);
			if (m!=null)
				return m;
		}
		return null;
	}
	private static MobileBaseCadManager checkforBase(IVitaminHolder holder, MobileBase b) {
		if(b==holder)
			return get(b);
		for(DHParameterKinematics k:b.getAllDHChains()) {
			for(int i=0;i<k.getNumberOfLinks();i++) {
				AbstractLink abstractLink = k.getAbstractLink(i);
				if(abstractLink==holder|| k.getLinkConfiguration(i)==holder) {
					return get(b);
				}
				MobileBase follower = k.getFollowerMobileBase(abstractLink);
				if(follower!=null) {
					MobileBaseCadManager back = checkforBase(holder,follower);
					if(back!=null)
						return back;
				}
			}
		}
		return null;
	}
	

}
