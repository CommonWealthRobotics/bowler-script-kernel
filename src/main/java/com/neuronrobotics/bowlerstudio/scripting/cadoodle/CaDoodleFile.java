package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javafx.scene.image.WritableImage;
import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.Operations;
import org.python.google.common.io.Files;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.ThumbnailImage;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.TickToc;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.IParametric;
import eu.mihosoft.vrl.v3d.parametrics.Parameter;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;
import javafx.embed.swing.SwingFXUtils;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

public class CaDoodleFile {
	public static final String NO_NAME = "NoName";
	@Expose(serialize = true, deserialize = true)
	private ArrayList<ICaDoodleOpperation> opperations = new ArrayList<ICaDoodleOpperation>();
	@Expose(serialize = true, deserialize = true)
	private int currentIndex = 0;
	@Expose(serialize = true, deserialize = true)
	private long timeCreated = -1;
	@Expose(serialize = true, deserialize = true)
	private String projectName = NO_NAME;
	@Expose(serialize = true, deserialize = true)
	private TransformNR rulerLocation = new TransformNR();
	@Expose(serialize = true, deserialize = true)
	private TransformNR workplane = new TransformNR();
	@Expose(serialize = false, deserialize = false)
	private File selfInternal;
//	@Expose (serialize = false, deserialize = false)
//	private List<CSG> currentState = new ArrayList<CSG>();
	@Expose(serialize = false, deserialize = false)
	private double percentInitialized = 0;
	@Expose(serialize = false, deserialize = false)
	private HashMap<ICaDoodleOpperation, List<CSG>> cache = new HashMap<ICaDoodleOpperation, List<CSG>>();
	private static Type TT_CaDoodleFile = new TypeToken<CaDoodleFile>() {
	}.getType();
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
			.excludeFieldsWithoutExposeAnnotation().registerTypeAdapterFactory(new ICaDoodleOperationAdapterFactory())
			.create();
	private ArrayList<ICaDoodleStateUpdate> listeners = new ArrayList<ICaDoodleStateUpdate>();
	private final ArrayList<Thread> opperationRunner = new ArrayList<Thread>();
	private boolean regenerating;
	private CopyOnWriteArrayList<ICaDoodleOpperation> toProcess = new CopyOnWriteArrayList<ICaDoodleOpperation>();
	private javafx.scene.image.WritableImage img;
	private boolean initializing;
	private static HashMap<String, VitaminBomManager> bomManagers = new HashMap<>();
	private VitaminBomManager bom;
	private IAcceptPruneForward accept = null;
	private long timeOfLastUpdate = 0;
	private OperationResult result = OperationResult.APPEND;
	private ICadoodleSaveStatusUpdate defaultSaver=new ICadoodleSaveStatusUpdate() {
		@Override
		public void renderSplashFrame(int percent, String message) {
			System.out.println(percent+"% "+message);
		}
	};
	private ICadoodleSaveStatusUpdate saveUpdate =null;
	public void close() {
		for (ICaDoodleOpperation op : cache.keySet()) {
			cache.get(op).clear();
		}
		cache.clear();
		cache = null;
		clearListeners();
		listeners = null;
		toProcess.clear();
		toProcess = null;
		img = null;
		for (Thread t : opperationRunner)
			t.interrupt();
	}

	public CaDoodleFile clearListeners() {
		listeners.clear();
		return this;
	}

	public CaDoodleFile removeListener(ICaDoodleStateUpdate l) {
		if (listeners.contains(l))
			listeners.remove(l);
		return this;
	}

	public CaDoodleFile addListener(ICaDoodleStateUpdate l) {
		if (!listeners.contains(l))
			listeners.add(l);
		return this;
	}

	public void initialize() {
//		if (initializing)
//			throw new RuntimeException("Can not initialize while initializing.");
		fireInitializationStart();
		initializing = true;
		if (selfInternal != null) {
			File parent = selfInternal.getAbsoluteFile().getParentFile();
			File imageCacheDir = new File(parent.getAbsolutePath() + delim() + "timeline");
			if (!imageCacheDir.exists())
				imageCacheDir.mkdir();
			File db = new File(selfInternal.getAbsoluteFile().getParent() + delim() + "CSGdatabase.json");
			try {
				// set a temp file for the database to clear
				// this ensures that parameters are not cleared from another project :/
				File createTempFile = File.createTempFile(projectName, ".json");
				CSGDatabase.setDbFile(createTempFile);
				CSGDatabase.clear();
				createTempFile.delete();
			} catch (IOException e) {
				// Auto-generated catch block
				e.printStackTrace();
			}
			CSGDatabase.setDbFile(db);
			StringParameter loc = new StringParameter("CaDoodle_File_Location", selfInternal.getAbsolutePath(),
					new ArrayList<String>());
			loc.setStrValue(selfInternal.getAbsolutePath());
			bom = CaDoodleFile.getBillOfMaterials();
			bom.clear();
			bom.save();

		}
		int indexStarting = getCurrentIndex();
		setCurrentIndex(0);
		setPercentInitialized(0);
		opperations = opperations.stream()
		        .filter(Objects::nonNull)
		        .collect(Collectors.toCollection(ArrayList::new));
		if(indexStarting>opperations.size())
			indexStarting=opperations.size();
		for (int i = 0; i < getOpperations().size(); i++) {
			ICaDoodleOpperation op = getOpperations().get(i);
			if(op==null)
				continue;
			setPercentInitialized(((double) i) / (double) getOpperations().size());
			try {
				process(op);
			} catch (Throwable t) {
				t.printStackTrace();
				indexStarting = i + 1;
				break;
				// opperations.remove(op);
			}
		}
		setCurrentIndex(indexStarting);
		updateCurrentFromCache();
		loadImageFromFile();
		setPercentInitialized(1);
		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onInitializationDone();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
		updateBoM();
		initializing = false;
	}

	private void updateBoM() {
		if (bom == null)
			return;
		bom.clear();
		bom.save();
		for (CSG c : getCurrentState()) {
			String type = null;
			String size = null;
			for (String param : c.getParameters()) {
				if (!param.contains(c.getName()))
					continue;
				if (param.contains("_CaDoodle_Vitamin_Type")) {
					Parameter p = CSGDatabase.get(param);
					type = p.getStrValue();
				}
				if (param.contains("_CaDoodle_Vitamin_Size")) {
					Parameter p = CSGDatabase.get(param);
					size = p.getStrValue();
				}
				if (type != null && size != null) {
					bom.addVitamin(new VitaminLocation(false, c.getName(), type, size, new TransformNR()));
					break;
				}
			}
		}
		bom.save();
	}

	public static VitaminBomManager getBillOfMaterials() {

		String strValue = getCadoodleFileLocation();
		File file = new File(strValue).getParentFile();
		if (bomManagers.get(strValue) == null) {
			bomManagers.put(strValue, new VitaminBomManager(file));
		}
		return bomManagers.get(strValue);
	}

	private static String getCadoodleFileLocation() {
		StringParameter loc = new StringParameter("CaDoodle_File_Location", "", new ArrayList<String>());
		String strValue = loc.getStrValue();
		return strValue;
	}

	public Thread regenerateFrom(ICaDoodleOpperation source) {
		if (initializing)
			return null;
		if (isRegenerating() || isOperationRunning()||source==null) {
			new Exception("Operation Running, bailing").printStackTrace();
			return null;
		}
		fireRegenerateStart();
		int endIndex = getCurrentIndex();
		double size = getOpperations().size();
		if (endIndex != size) {
//			new Exception("Regenerationg from a position back in time " + endIndex + " but have " + size)
//					.printStackTrace();
		}
		Thread t = null;
		t = new Thread() {
			public void run() {
				this.setName("Regeneration Threads");
				try {
					timeOfLastUpdate = System.currentTimeMillis();
					setRegenerating(true);
					// com.neuronrobotics.sdk.common.Log.error("Regenerating Object from
					// "+source.getType());
					int opIndex = 0;
					for (int i = 0; i < size; i++) {
						ICaDoodleOpperation op = getOpperations().get(i);
						if (source == op) {
							opIndex = i;
							break;
						}
					}
					setCurrentIndex(opIndex);
					try {
						for (; getCurrentIndex() < size;) {
							int percent =(int)( ((double )getCurrentIndex())/((double)getOpperations().size())*100.0);
							setCurrentIndex(getCurrentIndex() + 1);
							setPercentInitialized(((double) getCurrentIndex()) / size);
							// com.neuronrobotics.sdk.common.Log.error("Regenerating "+currentIndex);
							int currentIndex2 = getCurrentIndex() - 1;
							ICaDoodleOpperation op = getOpperations().get(currentIndex2);
							getSaveUpdate().renderSplashFrame(percent, "Regenerating "+op.getType()+" "+currentIndex2);
							getTimelineImageFile(op).delete();
							try {
								List<CSG> process = op.process(getPreviouState());
								storeResultInCache(op, process);
								setCurrentState(op, process);
							} catch (Throwable tr) {
								tr.printStackTrace();
							}
						}
						if (getCurrentIndex() != endIndex) {
							setCurrentIndex(endIndex);
							updateCurrentFromCache();
						}
					} catch (Exception ex) {
						ex.printStackTrace();
					}
					setPercentInitialized(1);
					updateBoM();
					setRegenerating(false);
					fireSaveSuggestion();
					fireRegenerateDone();
				} catch (Throwable th) {
					th.printStackTrace();
				}
				opperationRunner.remove(this);
			}
		};
		opperationRunner.add(t);
		t.start();
		return t;
	}

	public Thread regenerateCurrent() {
		if (isOperationRunning()) {
			new Exception("Operation Running, bailing").printStackTrace();

			return opperationRunner.get(0);
		}
		if (initializing) {
			Thread t = new Thread();
			t.start();
			return t;
		}
		fireRegenerateStart();
		Thread t = null;
		t = new Thread() {
			public void run() {
				timeOfLastUpdate = System.currentTimeMillis();

				// TickToc.setEnabled(true);

				this.setName("regenerateCurrent Thread");

				ICaDoodleOpperation op = getCurrentOpperation();
				TickToc.tic("Start regenerate");
				List<CSG> process = op.process(getPreviouState());
				TickToc.tic("Finish regenerate");
				int currentIndex2 = getCurrentIndex();
				getTimelineImageFile(currentIndex2).delete();
				TickToc.tic("Get timeline file");
				storeResultInCache(op, process);
				TickToc.tic("Stored results in cache");
				setCurrentState(op, process);
				TickToc.tic("set current state");
				fireSaveSuggestion();
				TickToc.tic("Fired save suggestion");
				fireRegenerateDone();
				TickToc.tic("Fired regeneration Done");
				opperationRunner.remove(this);
			}
		};
		opperationRunner.add(t);
		t.start();
		return t;

	}

	private void process(ICaDoodleOpperation op) {
		List<CSG> process = op.process(getCurrentState());
		int currentIndex2 = getCurrentIndex();
		storeResultInCache(op, process);
		setCurrentIndex(currentIndex2 + 1);
		setCurrentState(op, process);

	}

	public boolean isOperationRunning() {
		for (int i = 0; i < opperationRunner.size(); i++) {
			Thread t = opperationRunner.get(i);
			if (t != null) {
				if (!t.isAlive()) {
					opperationRunner.remove(t);
					// new Exception("Thread failed to remove itself
					// "+t.getName()).printStackTrace();
					continue;
				}
				if (Thread.currentThread().getId() == t.getId())
					return false;
				return true;
			}
		}
		return false;
	}

	public Thread addOpperation(ICaDoodleOpperation o) throws CadoodleConcurrencyException {
		if(o==null)
			throw new NullPointerException();
		toProcess.add(o);
		if (isOperationRunning()) {
			new Exception("Operation Running, bailing").printStackTrace();
			return opperationRunner.get(0);
		}
		Thread t = null;
		t = new Thread() {
			public void run() {
				
				timeOfLastUpdate = System.currentTimeMillis();
				while (toProcess.size() > 0) {
					result = OperationResult.APPEND;
					this.setName("addOpperation Thread " + toProcess.size());
					ICaDoodleOpperation op = toProcess.remove(0);
					if (getCurrentIndex() != getOpperations().size()) {
						try {
							fireRegenerateStart();
							setResult(pruneForward(op));
						} catch (Exception e) {
							e.printStackTrace();
							break;
						}
					}
					if (getResult() == OperationResult.APPEND || getResult() == OperationResult.PRUNE) {
						try {
							getOpperations().add(op);
							process(op);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
					if (getResult() == OperationResult.INSERT) {
						getOpperations().add(getCurrentIndex(), op);
						process(op);
						try {
							regenerateFrom(op).join();
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						updateCurrentFromCache();
					}
					if(getResult()==OperationResult.ABORT) {
						setCurrentState(getCurrentOpperation(), getCurrentState());
					}
				}
				updateBoM();
				fireSaveSuggestion();
				fireRegenerateDone();
				opperationRunner.remove(this);
			}
		};
		opperationRunner.add(t);
		t.start();
		return t;
	}

	public Thread deleteOperation(ICaDoodleOpperation op) {
		if(op==null)
			throw new NullPointerException();
		if (isOperationRunning()) {
			new Exception("Operation Running, bailing").printStackTrace();
			return opperationRunner.get(0);
		}
		Thread t = null;
		t = new Thread() {
			public void run() {
				timeOfLastUpdate = System.currentTimeMillis();
				this.setName("addOpperation Thread " + toProcess.size());
				int index = 0;
				for (int i = 0; i < getOpperations().size(); i++)
					if (getOpperations().get(i) == op)
						index = i;
				getOpperations().remove(op);
//				if (index == getOpperations().size())
//					index -= 1;
				if (index < 1)
					index = 1;
				ICaDoodleOpperation newTar = getOpperations().get(index - 1);
				setCurrentIndex(index );
				try {
					regenerateFrom(newTar).join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				updateCurrentFromCache();
				updateBoM();
				fireSaveSuggestion();
				opperationRunner.remove(this);
			}
		};
		opperationRunner.add(t);
		t.start();
		return t;
	}

	public static CSG getByName(List<CSG> back, String name) {
		for (CSG c : back) {
			if (c.getName().contentEquals(name))
				return c;
		}
		throw new RuntimeException("Fail! there was no object named " + name);
	}

	public static int applyToAllConstituantElements(boolean addRet, List<String> targetNames, ArrayList<CSG> back,
			ICadoodleRecursiveEvent p, int depth) {
		for (int i = 0; i < targetNames.size(); i++) {
			String s = targetNames.get(i);
			try {
				CSG c = getByName(back, s);
				if (c.isInGroup())
					continue;
			}catch(Exception ex) {
				ex.printStackTrace();
			}
			applyToAllConstituantElements(addRet, s, back, p, depth);
		}
		return back.size();
	}

	public static int applyToAllConstituantElements(boolean addRet, String targetName, ArrayList<CSG> back,
			ICadoodleRecursiveEvent p, int depth) {
		ArrayList<CSG> immutable = new ArrayList<>();
		immutable.addAll(back);
		for (int i = 0; i < immutable.size(); i++) {
			CSG csg = immutable.get(i);
			if (csg.isLock())
				continue;
			// boolean inGroup = csg.isInGroup();
			boolean thisCSGIsInGroupNamedAfterTarget = csg.checkGroupMembership(targetName);
			String thisCSGName = csg.getName();
			boolean thisCSGIsTheTarget = thisCSGName.contentEquals(targetName);
			boolean groupResult = csg.isGroupResult();

			if (thisCSGIsTheTarget) {
				// move it
				ArrayList<CSG> tmpToAdd = p.process(csg, depth);
				if (addRet) {
					back.addAll(tmpToAdd);
				} else {
					for (int j = 0; j < back.size(); j++) {
						if (back.get(j).getName().contentEquals(csg.getName())) {
							back.remove(j);
							break;
						}
					}
					back.addAll(tmpToAdd);
				}
				continue;
			}
			if (thisCSGIsInGroupNamedAfterTarget) {
				// composite group
				applyToAllConstituantElements(addRet, thisCSGName, back, p, depth + 1);
			}
		}
		back.removeAll(Collections.singleton(null));
		return back.size();
	}

	public File getTimelineImageFile(ICaDoodleOpperation test) {
		for (int i = 0; i < getOpperations().size(); i++) {
			ICaDoodleOpperation key = getOpperations().get(i);
			if (key == test) {
				File file = getTimelineImageFile(i);
				return file;
			}
		}
		throw new RuntimeException("File not found!");
	}

	public File getTimelineImageFile(int i) {
		File parent = selfInternal.getAbsoluteFile().getParentFile();
		File file = new File(parent.getAbsolutePath() + delim() + "timeline" + delim() + (i + 1) + ".png");
		return file;
	}

	private OperationResult pruneForward(ICaDoodleOpperation op) throws Exception {
		if(op==null)
			throw new NullPointerException();
		OperationResult res = OperationResult.INSERT;
		if (getAccept() != null) {
			res = getAccept().accept();
			if (res == OperationResult.ABORT) {
				return res;
			}
		}
		if( getCurrentIndex() >0)
		for (int i = getCurrentIndex() - 1; i < getOpperations().size(); i++) {
			ICaDoodleOpperation key = getOpperations().get(i);
			if (i >= getCurrentIndex()) {
				List<CSG> back = cache.remove(key);
				if (back != null)
					back.clear();
			}
			File imageCache = getTimelineImageFile(i);
			// System.err.println("Deleting " + imageCache.getAbsolutePath());
			imageCache.delete();
		}
		if (res == OperationResult.PRUNE) {
			List<ICaDoodleOpperation> subList = (List<ICaDoodleOpperation>) getOpperations().subList(0,
					getCurrentIndex());
			ArrayList<ICaDoodleOpperation> newList = new ArrayList<ICaDoodleOpperation>();
			newList.addAll(subList);
			setOpperations(newList);
			com.neuronrobotics.sdk.common.Log.error("Pruning forward here!");
			fireSaveSuggestion();
		}
		return res;
	}

	private void storeResultInCache(ICaDoodleOpperation op, List<CSG> process) {
		ArrayList<CSG> cachedCopy = new ArrayList<CSG>();
		HashSet<String> names = new HashSet<>();
		for (CSG c : process) {
			if (names.contains(c.getName()))
				throw new RuntimeException("There can not be 2 objects with the same name after an " + op.getType()
						+ " opperation! " + c.getName());
			names.add(c.getName());
			cachedCopy.add(cloneCSG(c).setStorage(new PropertyStorage()).syncProperties(c).setName(c.getName())
					.setRegenerate(c.getRegenerate()));
			// cachedCopy.add(c);
		}
		cache.put(op, cachedCopy);
		System.out.println("\n\nUpdated Memory use: "+getFreeMemory()+"\n\n");
	}
	public static double getFreeMemory() {
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory(); // Maximum memory the JVM will attempt to use
		long totalMemory = runtime.totalMemory(); // Total memory currently allocated to the JVM
		long freeMemory = runtime.freeMemory(); // Free memory within the allocated memory
		long usedMemory = totalMemory - freeMemory; // Actually used memory

		// Calculate the percentage of maximum memory that's currently being used
		return (usedMemory * 100.0) / maxMemory;
	}
	private CSG cloneCSG(CSG dyingCSG) {
		CSG csg = new CSG();

		ArrayList<Polygon> collect = new ArrayList<Polygon>();
		for (Polygon p : dyingCSG.getPolygons()) {
			if (p == null)
				continue;
			try {
				collect.add(p);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		csg.setPolygons(collect);
		Set<String> params = dyingCSG.getParameters();
		for (String param : params) {
			boolean existing = false;
			for (String s : csg.getParameters()) {
				if (s.contentEquals(param))
					existing = true;
			}
			if (!existing) {
				Parameter vals = CSGDatabase.get(param);
				if (vals != null)
					csg.setParameter(vals, dyingCSG.getMapOfparametrics().get(param));
			}
		}
		if (csg.getName().length() == 0)
			csg.setName(dyingCSG.getName());
		csg.setColor(dyingCSG.getColor());
		return csg;
	}

	public void back() {
		if (isBackAvailible())
			setCurrentIndex(getCurrentIndex() - 1);
		updateCurrentFromCache();
		fireSaveSuggestion();
	}

	public void moveToOpIndex(int newIndex) {
		if (newIndex > getOpperations().size())
			return;
		if (newIndex < 0)
			return;
		setCurrentIndex(newIndex + 1);
		updateCurrentFromCache();
		fireSaveSuggestion();
	}

	public boolean isBackAvailible() {
		return getCurrentIndex() > 1;
	}

	private void updateCurrentFromCache() {
		ICaDoodleOpperation key = getCurrentOpperation();
		if (key == null)
			return;
		com.neuronrobotics.sdk.common.Log.error("Current opperation results: " + key.getType());
		setCurrentState(key, getCurrentState());
	}

	public ICaDoodleOpperation getCurrentOpperation() {
		if (getCurrentIndex() == 0)
			return null;
		return getOpperations().get(getCurrentIndex() - 1);
	}

	public void forward() {
		if (isForwardAvailible())
			setCurrentIndex(getCurrentIndex() + 1);
		updateCurrentFromCache();
		fireSaveSuggestion();
	}

	public boolean isForwardAvailible() {
		return getCurrentIndex() < getOpperations().size();
	}

	public File getSelf() {
		if (selfInternal == null) {
			try {
				selfInternal = File.createTempFile(DownloadManager.sanitizeString(projectName), ".doodle");
			} catch (IOException e) {
				// Auto-generated catch block
				e.printStackTrace();
			}
		}
		return selfInternal;
	}

	public CaDoodleFile setSelf(File self) {
		this.selfInternal = self;
		return this;
	}

	public List<CSG> getCurrentState() {
		return getStateAtOperation(getCurrentOpperation());
	}
	public List<CSG> getStateAtOperation(ICaDoodleOpperation op) {
		if (getCurrentIndex() == 0)
			return new ArrayList<CSG>();
		List<CSG> list = cache.get(op);
		if (list == null)
			list = new ArrayList<CSG>();
		return list;
	}

	public List<CSG> getSelect(List<String> selectedSnapshot) {
		List<CSG> cur = getCurrentState();
		ArrayList<CSG> back = new ArrayList<CSG>();
		if (cur != null)
			for (CSG c : cur) {
				for (String s : selectedSnapshot) {
					if (c.getName().contentEquals(s)) {
						back.add(c);
					}
				}
			}
		return back;
	}

	public List<CSG> getPreviouState() {
		if (getCurrentIndex() < 2)
			return new ArrayList<CSG>();
		ICaDoodleOpperation key = getOpperations().get(getCurrentIndex() - 2);
		
		return cache.get(key);
	}

	private void setCurrentState(ICaDoodleOpperation op, List<CSG> currentState) {
		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onUpdate(currentState, op, this);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private void fireSaveSuggestion() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onSaveSuggestion();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private void fireInitializationStart() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onInitializationStart();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private void fireRegenerateDone() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				TickToc.tic("Fire " + l.getClass());
				l.onRegenerateDone();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private void fireRegenerateStart() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onRegenerateStart();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	private void fireWorkplaneChange() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onWorkplaneChange(workplane);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	public String getMyProjectName() {
		return projectName;
	}

	public CaDoodleFile setProjectName(String projectName) {
		this.projectName = projectName;
		return this;
	}

	public String toJson() {
		String ret = null;
		synchronized (this) {
			ret = gson.toJson(this);
		}
		return ret;
	}

	public File save() throws IOException {

		// synchronized (selfInternal) {
		String contents = toJson();
		List<CSG> currentState = getCurrentState();
		int currentIndex2 = getCurrentIndex();
		getSaveUpdate().renderSplashFrame(1, "Save Doodle to "+selfInternal.getName());
		FileUtils.write(selfInternal, contents, StandardCharsets.UTF_8, false);
		// }
		int num=0;
		for (int i = 0; i < opperations.size(); i++) {
			File f = getTimelineImageFile(i);
			ICaDoodleOpperation op = opperations.get(i);
			int percent =(int)( ((double )i)/((double)opperations.size())*100.0);
			List<CSG> process = cache.get(op);
			if (!f.exists())
				try {
					num++;
					getSaveUpdate().renderSplashFrame(percent, "Save Timeline Image "+i+".png");

					setSaveImage(process, op);
					
				} catch (IOException e) {
					// Auto-generated catch block
					e.printStackTrace();
				}
		}
		if (bom != null)
			bom.save();
		getSaveUpdate().renderSplashFrame(100, "Doofle save Done ");
		fireTimelineUpdate(num);
		// System.gc();
		return getSelf();
	}

	private void setSaveImage(List<CSG> currentState, ICaDoodleOpperation op) throws IOException {
		if (selfInternal == null)
			return;
		int currentIndex2 = 0;
		for (int i = 0; i < getOpperations().size(); i++)
			if (getOpperations().get(i) == op)
				currentIndex2 = i;
//		if(currentIndex2==0)
//			return;
		File parent = selfInternal.getAbsoluteFile().getParentFile();
		File imageCache = new File(parent.getAbsolutePath() + delim() + "timeline" + delim() + currentIndex2 + ".png");
		File image = new File(parent.getAbsolutePath() + delim() + "snapshot.png");

		if (imageCache.exists())
			return;
		try {
			WritableImage image2 = loadingImageFromUIThread(currentState);
			if (image2 != null) {
				BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image2, null);
				try {
					ImageIO.write(bufferedImage, "png", imageCache);
				} catch (IOException e) {
					// com.neuronrobotics.sdk.common.Log.error("Error saving image: " +
					// e.getMessage());
					e.printStackTrace();
				}
				do {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
						return;
					}
				} while (!imageCache.exists());
				if (getOpperations().get(getOpperations().size() - 1) == op) {
					Files.copy(imageCache, image);
				}
				//System.err.println("Thumbnail saved successfully to " + imageCache.getAbsolutePath());
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void fireTimelineUpdate(int number) {
		for (ICaDoodleStateUpdate s : listeners) {
			s.onTimelineUpdate(number);
		}
	}

	public WritableImage loadImageFromFile() {
		try {
			File parent = selfInternal.getAbsoluteFile().getParentFile();
			File image = new File(parent.getAbsolutePath() + delim() + "snapshot.png");
			if (image.exists()) {
				BufferedImage bufferedImage = ImageIO.read(image);
				if (bufferedImage != null) {
					img = SwingFXUtils.toFXImage(bufferedImage, null);
				}
			} else {
				loadingImageFromUIThread(getCurrentState());
			}
		} catch (Exception e) {
			com.neuronrobotics.sdk.common.Log.error("Error loading image: " + e.getMessage());
			e.printStackTrace();
		}
		return img;
	}

	private javafx.scene.image.WritableImage loadingImageFromUIThread(List<CSG> currentState) {
		if (currentState == null)
			throw new RuntimeException("Can not be null");
		ArrayList<javafx.scene.image.WritableImage> holder = new ArrayList<WritableImage>();
		try {
			BowlerKernel.runLater(() -> {
				holder.add(ThumbnailImage.get(currentState));
			});
		} catch (Throwable ex) {
			ex.printStackTrace();
			return null;
		}
		long start = System.currentTimeMillis();
		while (holder.size() == 0) {
			try {
				Thread.sleep(16);
				// com.neuronrobotics.sdk.common.Log.error("Waiting for image to write");
			} catch (InterruptedException e) {
				// Auto-generated catch block
				e.printStackTrace();
				break;
			}
			if(System.currentTimeMillis()-start>2500 && holder.size()==0) {
				throw new RuntimeException("Failed to create image");
			}
		}
		return holder.get(0);
	}

	public static CaDoodleFile fromJsonString(String content) throws Exception {
		return fromJsonString(content, null, null, true);
	}

	public static CaDoodleFile fromJsonString(String content, ICaDoodleStateUpdate listener, File self,
			boolean initialize) throws Exception {
		CaDoodleFile file = gson.fromJson(content, TT_CaDoodleFile);
		if (file == null) {
			file = new CaDoodleFile();
			file.setProjectName(RandomStringFactory.getNextRandomName());
			file.getTimeCreated();
		}
		if (listener != null) {
			file.addListener(listener);
		}
		if (self != null) {
			file.setSelf(self);
		}
		if (initialize) {
			file.initialize();
		}
		return file;
	}

	public static CaDoodleFile fromFile(File f) throws Exception {
		return fromFile(f, null, true);
	}

	public static String getProjectName(File f) throws Exception {
		com.neuronrobotics.sdk.common.Log.error("CaDoodle file reading from " + f.getAbsolutePath());
		String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
		CaDoodleFile file = fromJsonString(content, null, f, false);
		return file.getMyProjectName();
	}

	public static CaDoodleFile fromFile(File f, ICaDoodleStateUpdate listener, boolean initialize) throws Exception {
		com.neuronrobotics.sdk.common.Log.error("CaDoodle file loading from " + f.getAbsolutePath());
		String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
		CaDoodleFile file = fromJsonString(content, listener, f, initialize);
		return file;
	}

	public ArrayList<ICaDoodleOpperation> getOpperations() {
		return opperations;
	}

	public void setOpperations(ArrayList<ICaDoodleOpperation> opperations) {
		this.opperations = opperations;
		currentIndex = opperations.size();
	}

	public TransformNR getWorkplane() {
		if (workplane == null)
			workplane = new TransformNR();
		return workplane;
	}

	public void setWorkplane(TransformNR workplane) {
		this.workplane = workplane;
		fireWorkplaneChange();
		fireSaveSuggestion();
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public void setCurrentIndex(int currentIndex) {
		// new Exception("Current Index set to " + currentIndex).printStackTrace();
		if ((currentIndex - 1) >= getOpperations().size())
			throw new RuntimeException("Fail! Can not set an index greater than the availible operations");
		this.currentIndex = currentIndex;
	}

	public javafx.scene.image.WritableImage getImage() {
		return img;
	}

	public javafx.scene.image.WritableImage setImage(javafx.scene.image.WritableImage img) {
		this.img = img;
		return img;
	}

	public double getPercentInitialized() {
		return percentInitialized;
	}

	public void setPercentInitialized(double percentInitialized) {
		this.percentInitialized = percentInitialized;
	}

	public long getTimeCreated() {
		if (timeCreated < 0) {
			timeCreated = System.currentTimeMillis();
		}
		return timeCreated;
	}

	public boolean isRegenerating() {
		return regenerating;
	}

	private void setRegenerating(boolean regenerating) {
		this.regenerating = regenerating;
	}

	public TransformNR getRulerLocation() {
		return rulerLocation;
	}

	public void setRulerLocation(TransformNR rulerLocation) {
		rulerLocation.setRotation(new RotationNR());
		this.rulerLocation = rulerLocation;
		fireWorkplaneChange();
		fireSaveSuggestion();
	}

	public IAcceptPruneForward getAccept() {
		return accept;
	}

	public void setAccept(IAcceptPruneForward accept) {
		this.accept = accept;
	}

	public long timeSinceLastUpdate() {
		return System.currentTimeMillis() - timeOfLastUpdate;
	}

	public OperationResult getResult() {
		return result;
	}

	public void setResult(OperationResult result) {
		this.result = result;
	}

	public ICadoodleSaveStatusUpdate getSaveUpdate() {
		if(saveUpdate==null)
			return defaultSaver;
		return saveUpdate;
	}

	public void setSaveUpdate(ICadoodleSaveStatusUpdate saveUpdate) {
		this.saveUpdate = saveUpdate;
	}
}
