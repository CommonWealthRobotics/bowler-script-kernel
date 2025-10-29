package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javafx.scene.image.WritableImage;
import org.apache.commons.io.FileUtils;
import org.apache.hc.client5.http.impl.Operations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.creature.ThumbnailImage;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.MakeRobot;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.TickToc;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import eu.mihosoft.vrl.v3d.parametrics.IParametric;
import eu.mihosoft.vrl.v3d.parametrics.Parameter;
import eu.mihosoft.vrl.v3d.parametrics.StringParameter;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

public class CaDoodleFile {
	public static final String NO_NAME = "NoName";
	@Expose(serialize = true, deserialize = true)
	private ArrayList<CaDoodleOperation> opperations = new ArrayList<CaDoodleOperation>();
	@Expose(serialize = true, deserialize = true)
	private int currentIndex = 0;
	@Expose(serialize = true, deserialize = true)
	private long timeCreated = -1;
	@Expose(serialize = true, deserialize = true)
	private String projectName = NO_NAME;
	@Expose(serialize = true, deserialize = true)
	private TransformNR rulerLocation = new TransformNR();
	@Expose(serialize = true, deserialize = true)

	// Non Serialised private variables
	private TransformNR workplane = new TransformNR();
	private File self;
//	@Expose (serialize = false, deserialize = false)
//	private List<CSG> currentState = new ArrayList<CSG>();
	private double percentInitialized = 0;
	private final HashMap<CaDoodleOperation, List<CSG>> cache = new HashMap<CaDoodleOperation, List<CSG>>();
	private static Type TT_CaDoodleFile = new TypeToken<CaDoodleFile>() {
	}.getType();
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
			.excludeFieldsWithoutExposeAnnotation()
			.registerTypeAdapterFactory(new CaDoodleJsonOperationAdapterFactory()).create();
	private final ArrayList<ICaDoodleStateUpdate> listeners = new ArrayList<ICaDoodleStateUpdate>();
	private final ArrayList<Thread> opperationRunner = new ArrayList<Thread>();
	private boolean regenerating;
	private final CopyOnWriteArrayList<CaDoodleOperation> toProcess = new CopyOnWriteArrayList<CaDoodleOperation>();
	private javafx.scene.image.WritableImage img;
	private boolean initializing;
	private static HashMap<String, VitaminBomManager> bomManagers = new HashMap<>();
	private VitaminBomManager bom;
	private IAcceptPruneForward accept = null;
	private long timeOfLastUpdate = 0;
	private OperationResult result = OperationResult.APPEND;
	private ICadoodleSaveStatusUpdate defaultSaver = new ICadoodleSaveStatusUpdate() {
		@Override
		public void renderSplashFrame(int percent, String message) {
			com.neuronrobotics.sdk.common.Log.debug(percent + "% " + message);
		}
	};
	private ICadoodleSaveStatusUpdate saveUpdate = null;
	private boolean timelineOpen = false;
	private HashMap<String, MobileBaseBuilder> robots = new HashMap<String, MobileBaseBuilder>();
	private CSGDatabaseInstance csgDBinstance;
	private File objectDir;
	private ExecutorService executor = Executors.newFixedThreadPool(5);
	private File imageCacheDir;

	public ArrayList<MobileBase> getMobileBases() {
		ArrayList<MobileBase> back = new ArrayList<MobileBase>();
		for (MobileBaseBuilder b : robots.values()) {
			back.add(b.getMobileBase());
		}
		return back;
	}

	public void close() {
		// new Exception("CaDoodle File Closed here").printStackTrace();
		for (CaDoodleOperation op : getOpperations()) {
			op.setCaDoodleFile(null);
		}
//		for (CaDoodleOperation op : cache.keySet()) {
//			clearCache(op);
//		}
		cache.clear();
		clearListeners();
		toProcess.clear();
		img = null;
		for (Thread t : opperationRunner)
			t.interrupt();

	}

	private int opToIndex(CaDoodleOperation op) {
		for (int i = 0; i < opperations.size(); i++) {
			if (op == opperations.get(i))
				return i;
		}
		throw new IndexOutOfBoundsException();
	}

	private boolean inCache(CaDoodleOperation op) {
		int opIndex = opToIndex(op);
		File cacheFile = new File(getObjectDir().getAbsolutePath() + delim() + opIndex);
		return cacheFile.exists();
	}

	private List<CSG> getCachedCSGs(CaDoodleOperation op) {
		if (Platform.isFxApplicationThread()) {
			new RuntimeException("This should not be called from the UI thread!").printStackTrace();
			;
		}
		if (cache.get(op) == null && isInitialized()) {
			try {
				int opIndex = opToIndex(op);
				File cacheFile = new File(getObjectDir().getAbsolutePath() + delim() + opIndex + ".csg");
				if (cacheFile.exists()) {
					Log.debug("Loading Cached Objects from file: " + cacheFile.getAbsolutePath());
					// Log.error(new Exception());
					ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile));
					cache.put(op, (List<CSG>) ois.readObject());
					ois.close();
				}
			} catch (Exception ex) {
				Log.error(ex);
			}
		}
		return cache.get(op);
	}

	private void memoryCheck() {
		if (getFreeMemory() > 85) {
			com.neuronrobotics.sdk.common.Log.error("\n\nClearing Memory use: " + getFreeMemory() + "\n\n");
			CaDoodleOperation op = getCurrentOpperation();
			List<CSG> back = cache.get(op);

			cache.clear();
			cache.put(op, back);
			System.gc();
			com.neuronrobotics.sdk.common.Log.debug("Memory use down to: " + getFreeMemory());
		} else {
			// com.neuronrobotics.sdk.common.Log.debug("Memory use: " + getFreeMemory());
		}
	}

	private void placeCSGsInCache(CaDoodleOperation op, List<CSG> cachedCopyIn) {
		memoryCheck();
		// clear the stale cache value
		List<CSG> back = cache.remove(op);
		if (back != null)
			back.clear();
		List<CSG> cachedCopy=new ArrayList<>(cachedCopyIn);
		cache.put(op, cachedCopy);
		//executor.submit(() -> {
			File cacheFile = new File(getObjectDir().getAbsolutePath() + delim() + opToIndex(op) + ".csg");
			if (cacheFile.exists() && !isInitialized())
				return;
			if (cacheFile.exists())
				cacheFile.delete();
			try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
				oos.writeObject(cachedCopy);
				Log.debug("Saved " + cacheFile.getAbsolutePath());
			} catch (Exception ex) {
				Log.error(ex);
				throw new RuntimeException(ex);
			}
		//});

	}

	private void clearCache(CaDoodleOperation key) {
		int opIndex = opToIndex(key);
		File cacheFile = new File(getObjectDir().getAbsolutePath() + delim() + opIndex);
		if (cacheFile.exists())
			cacheFile.delete();

		List<CSG> back = cache.remove(key);
		if (back != null)
			back.clear();
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
		if (timeCreated < 0)
			timeCreated = System.currentTimeMillis();
		if (self != null) {
			getImageCacheDir();
			getObjectDir();
			getCsgDBinstance();// initialize the instance on initialize
			// CSGDatabase.setInstance(getCsgDBinstance());
			bom = CaDoodleFile.getBillOfMaterials(this);
			bom.clear();
			bom.save();
		}
		int indexStarting = getCurrentIndex();
		if (indexStarting == 0) {
			indexStarting = opperations.size();
		}
		this.currentIndex = 0;
		setPercentInitialized(0);
		opperations = opperations.stream().filter(Objects::nonNull).collect(Collectors.toCollection(ArrayList::new));
		if (indexStarting > opperations.size())
			indexStarting = opperations.size();
		for (int i = 0; i < getOpperations().size(); i++) {
			CaDoodleOperation op = getOpperations().get(i);
			if (op == null)
				continue;
			op.setCaDoodleFile(this);
			setPercentInitialized(((double) i) / (double) getOpperations().size());
			// if(!inCache(op))
			try {
				process(op);
			} catch (Throwable t) {
				com.neuronrobotics.sdk.common.Log.error(t);
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
				com.neuronrobotics.sdk.common.Log.error(e);
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
			for (String param : c.getParameters(getCsgDBinstance())) {
				if (!param.contains(c.getName()))
					continue;
				if (param.contains("_CaDoodle_Vitamin_Type")) {
					Parameter p = getCsgDBinstance().get(param);
					type = p.getStrValue();
				}
				if (param.contains("_CaDoodle_Vitamin_Size")) {
					Parameter p = getCsgDBinstance().get(param);
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

	public static VitaminBomManager getBillOfMaterials(CaDoodleFile cf) {

		String strValue = cf.getSelf().getAbsolutePath();
		File file = new File(strValue).getParentFile();
		if (bomManagers.get(strValue) == null) {
			bomManagers.put(strValue, new VitaminBomManager(file));
		}
		return bomManagers.get(strValue);
	}

//	private static String getCadoodleFileLocation() {
//		
//		return get;
//	}

	public Thread regenerateFrom(CaDoodleOperation source) {
		if (initializing)
			return null;
		if (isRegenerating() || isOperationRunning() || source == null) {
			com.neuronrobotics.sdk.common.Log.error(new Exception("Operation Running, bailing"));
			return null;
		}
		fireRegenerateStart(source);
		int endIndex = getCurrentIndex();
		double size = getOpperations().size();
		if (endIndex != size) {
//			new Exception("Regenerationg from a position back in time " + endIndex + " but have " + size)
//					.printStackTrace();
		}
		Thread t = null;
		CaDoodleFile cf = this;

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
						CaDoodleOperation op = getOpperations().get(i);
						if (source == op) {
							opIndex = i;
							break;
						}
					}
					setCurrentIndex(opIndex);
					try {
						for (; getCurrentIndex() < size;) {
							int percent = (int) (((double) getCurrentIndex()) / ((double) getOpperations().size())
									* 100.0);
							setCurrentIndex(getCurrentIndex() + 1);
							setPercentInitialized(((double) getCurrentIndex()) / size);
							// com.neuronrobotics.sdk.common.Log.error("Regenerating "+currentIndex);
							int currentIndex2 = getCurrentIndex() - 1;
							CaDoodleOperation op = getOpperations().get(currentIndex2);
							getSaveUpdate().renderSplashFrame(percent,
									"Regenerating " + op.getType() + " " + currentIndex2);
							getTimelineImageFile(op).delete();
							// clearCache(op);
							try {
								op.setCaDoodleFile(cf);
								List<CSG> process = op.process(getPreviouState());
								storeResultInCache(op, process);
								setCurrentState(op, process);
							} catch (Throwable tr) {
								com.neuronrobotics.sdk.common.Log.error(tr);
							}
						}
						if (getCurrentIndex() != endIndex) {
							setCurrentIndex(endIndex);
							updateCurrentFromCache();
						}
					} catch (Exception ex) {
						com.neuronrobotics.sdk.common.Log.error(ex);
						;
					}
					setPercentInitialized(1);
					updateBoM();
					setRegenerating(false);
					fireSaveSuggestion();
					fireRegenerateDone();
				} catch (Throwable th) {
					com.neuronrobotics.sdk.common.Log.error(th);
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
			com.neuronrobotics.sdk.common.Log.error(new Exception("Operation Running, bailing"));

			return opperationRunner.get(0);
		}
		if (initializing) {
			Thread t = new Thread();
			t.start();
			return t;
		}
		CaDoodleOperation op = getCurrentOpperation();

		fireRegenerateStart(op);
		Thread t = null;
		CaDoodleFile cf = this;
		t = new Thread() {
			public void run() {
				timeOfLastUpdate = System.currentTimeMillis();

				// TickToc.setEnabled(true);

				this.setName("regenerateCurrent Thread");

				TickToc.tic("Start regenerate");
				op.setCaDoodleFile(cf);
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

	private void process(CaDoodleOperation op) {
		op.setCaDoodleFile(this);
		List<CSG> process = op.process(getCurrentState());
		if (MakeRobot.class.isInstance(op)) {
			MakeRobot mr = (MakeRobot) op;
			getRobots().put(mr.getName(), mr.getBuilder());
		}
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

	public Thread addOpperation(CaDoodleOperation o) throws CadoodleConcurrencyException {
		if (o == null)
			throw new NullPointerException();
		toProcess.add(o);
		if (isOperationRunning()) {
			com.neuronrobotics.sdk.common.Log.error(new Exception("Operation Running, bailing"));
			return opperationRunner.get(0);
		}
		Thread t = null;
		t = new Thread() {
			public void run() {

				timeOfLastUpdate = System.currentTimeMillis();
				while (toProcess.size() > 0) {
					result = OperationResult.APPEND;
					this.setName("addOpperation Thread " + toProcess.size());
					CaDoodleOperation op = toProcess.remove(0);
					com.neuronrobotics.sdk.common.Log.debug("Adding Operation " + op);
					if (getCurrentIndex() != getOpperations().size()) {
						try {
							fireRegenerateStart(op);
							setResult(pruneForward(op));
						} catch (Exception e) {
							com.neuronrobotics.sdk.common.Log.error(e);
							break;
						}
					}
					if (getResult() == OperationResult.APPEND || getResult() == OperationResult.PRUNE) {
						try {
							getOpperations().add(op);
							process(op);
						} catch (Exception ex) {
							com.neuronrobotics.sdk.common.Log.error(ex);
							;
						}
					}
					if (getResult() == OperationResult.INSERT) {
						getOpperations().add(getCurrentIndex(), op);
						process(op);
						try {
							regenerateFrom(op).join();
						} catch (InterruptedException e) {
							com.neuronrobotics.sdk.common.Log.error(e);
						}
						updateCurrentFromCache();
					}
					if (getResult() == OperationResult.ABORT) {
						setCurrentState(getCurrentOpperation(), getCurrentState());
					}
					updateBoM();
					fireSaveSuggestion();
					fireRegenerateDone();
				}
				opperationRunner.remove(this);
			}
		};
		opperationRunner.add(t);
		t.start();
		return t;
	}

	public Thread deleteOperation(CaDoodleOperation op) {
		if (op == null)
			throw new NullPointerException();
		if (isOperationRunning()) {
			com.neuronrobotics.sdk.common.Log.error(new Exception("Operation Running, bailing"));
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
				op.pruneCleanup();
//				if (index == getOpperations().size())
//					index -= 1;
				if (index < 1)
					index = 1;
				CaDoodleOperation newTar = getOpperations().get(index - 1);
				setCurrentIndex(index);
				try {
					regenerateFrom(newTar).join();
				} catch (InterruptedException e) {
					com.neuronrobotics.sdk.common.Log.error(e);
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
		HashSet<String> appliedMemory = new HashSet<String>();
		for (int i = 0; i < targetNames.size(); i++) {
			String s = targetNames.get(i);
			try {
				CSG c = getByName(back, s);
				if (c.isInGroup())
					continue;
			} catch (Exception ex) {
				com.neuronrobotics.sdk.common.Log.error(ex);
				;
			}
			applyToAllConstituantElements(addRet, s, back, p, depth, appliedMemory);
		}
		return back.size();
	}

	public static int applyToAllConstituantElements(boolean addRet, String targetName, ArrayList<CSG> back,
			ICadoodleRecursiveEvent p, int depth, HashSet<String> appliedMemory) {
		if (appliedMemory.contains(targetName))
			return back.size();
		appliedMemory.add(targetName);
		ArrayList<CSG> immutable = new ArrayList<>();
		immutable.addAll(back);
		for (int i = 0; i < immutable.size(); i++) {
			CSG csg = immutable.get(i);
			if (csg == null || csg.isLock())
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
				applyToAllConstituantElements(addRet, thisCSGName, back, p, depth + 1, appliedMemory);
			}
		}
		back.removeAll(Collections.singleton(null));
		return back.size();
	}

	public File getTimelineImageFile(CaDoodleOperation test) {
		for (int i = 0; i < getOpperations().size(); i++) {
			CaDoodleOperation key = getOpperations().get(i);
			if (key == test) {
				File file = getTimelineImageFile(i);
				return file;
			}
		}
		throw new RuntimeException("File not found!");
	}

	public File getTimelineImageFile(int i) {
		File file = new File(getImageCacheDir().getAbsolutePath() + delim() + (i + 1) + ".png");
		return file;
	}

	private OperationResult pruneForward(CaDoodleOperation op) throws Exception {
		if (op == null)
			throw new NullPointerException();
		OperationResult res = OperationResult.INSERT;
		if (getAccept() != null) {
			res = getAccept().accept();
			if (res == OperationResult.ABORT) {
				return res;
			}
		}
		if (getCurrentIndex() > 0)
			for (int i = getCurrentIndex() - 1; i < getOpperations().size(); i++) {
				CaDoodleOperation key = getOpperations().get(i);
				if (i >= getCurrentIndex()) {
					clearCache(key);
				}
				File imageCache = getTimelineImageFile(i);
				// System.err.println("Deleting " + imageCache.getAbsolutePath());
				imageCache.delete();
			}
		if (res == OperationResult.PRUNE) {
			List<CaDoodleOperation> subList = (List<CaDoodleOperation>) getOpperations().subList(0, getCurrentIndex());
			for (int i = getCurrentIndex(); i < getOpperations().size(); i++) {
				getOpperations().get(i).pruneCleanup();
			}
			ArrayList<CaDoodleOperation> newList = new ArrayList<CaDoodleOperation>();
			newList.addAll(subList);
			setOpperations(newList);
			com.neuronrobotics.sdk.common.Log.error("Pruning forward here!");
			fireSaveSuggestion();
		}
		return res;
	}

	private void storeResultInCache(CaDoodleOperation op, List<CSG> process) {
		ArrayList<CSG> cachedCopy = new ArrayList<CSG>();
		HashSet<String> names = new HashSet<>();
		for (CSG c : process) {
			if (names.contains(c.getName()))
				throw new RuntimeException("There can not be 2 objects with the same name after an " + op.getType()
						+ " opperation! " + c.getName());
			names.add(c.getName());
			CSG cachedVer = cloneCSG(c).setStorage(new PropertyStorage()).syncProperties(getCsgDBinstance(), c)
					.setName(c.getName()).setRegenerate(c.getRegenerate());
			if (cachedVer.isHole() != c.isHole() || cachedVer.isHide() != c.isHide()) {
				throw new RuntimeException("Lost properties");
			}
			cachedCopy.add(cachedVer);
			// cachedCopy.add(c);
		}
		placeCSGsInCache(op, cachedCopy);

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
				com.neuronrobotics.sdk.common.Log.error(ex);
				;
			}
		}
		csg.setPolygons(collect);
		Set<String> params = dyingCSG.getParameters(getCsgDBinstance());
		for (String param : params) {
			boolean existing = false;
			for (String s : csg.getParameters(getCsgDBinstance())) {
				if (s.contentEquals(param))
					existing = true;
			}
			if (!existing) {
				Parameter vals = getCsgDBinstance().get(param);
				if (vals != null)
					csg.setParameter(getCsgDBinstance(), vals,
							getCsgDBinstance().getMapOfparametrics(dyingCSG).get(param));
			}
		}
		if (csg.getName().length() == 0)
			csg.setName(dyingCSG.getName());
		csg.setColor(dyingCSG.getColor());
		return csg;
	}

	public void back() {
		CaDoodleOperation op = getCurrentOpperation();
		if (isBackAvailible())
			setCurrentIndex(getCurrentIndex() - 1);
		updateCurrentFromCache();
		if (ICadoodleOperationUndo.class.isInstance(op)) {
			ICadoodleOperationUndo un = (ICadoodleOperationUndo) op;
			un.undo();
		}
		fireSaveSuggestion();
	}

	public void forward() {
		if (isForwardAvailible())
			setCurrentIndex(getCurrentIndex() + 1);
		updateCurrentFromCache();
		CaDoodleOperation op = getCurrentOpperation();
		if (ICadoodleOperationUndo.class.isInstance(op)) {
			ICadoodleOperationUndo un = (ICadoodleOperationUndo) op;
			un.redo();
		}
		fireSaveSuggestion();
	}

	public void moveToOpIndex(int newIndex) {
		if (newIndex > getOpperations().size())
			return;
		if (newIndex < 0)
			return;
		int ci = getCurrentIndex();
		int ni = newIndex + 1;
		boolean forward = ci < ni;
		if (forward) {
			for (int i = ci; i < ni + 1; i++) {
				try {
					CaDoodleOperation op = opperations.get(i - 1);
					if (ICadoodleOperationUndo.class.isInstance(op)) {
						ICadoodleOperationUndo un = (ICadoodleOperationUndo) op;
						un.redo();
					}
				} catch (Exception ex) {
					Log.error(ex);
				}
			}
		} else {
			for (int i = ni; i < ci + 1; i++) {
				CaDoodleOperation op = opperations.get(i - 1);
				if (ICadoodleOperationUndo.class.isInstance(op)) {
					ICadoodleOperationUndo un = (ICadoodleOperationUndo) op;
					un.undo();
				}
			}
		}
		setCurrentIndex(ni);
		updateCurrentFromCache();
		fireSaveSuggestion();
	}

	public boolean isBackAvailible() {
		return getCurrentIndex() > 1;
	}

	private void updateCurrentFromCache() {
		CaDoodleOperation key = getCurrentOpperation();
		if (key == null)
			return;
		com.neuronrobotics.sdk.common.Log.debug("Current opperation results: " + key.getType());
		setCurrentState(key, getCurrentState());
	}

	public CaDoodleOperation getCurrentOpperation() {
		if (getCurrentIndex() == 0)
			return null;
		return getOpperations().get(getCurrentIndex() - 1);
	}

	public boolean isForwardAvailible() {
		return getCurrentIndex() < getOpperations().size();
	}

	public File getSelf() {
		if (self == null) {
			try {
				self = File.createTempFile(DownloadManager.sanitizeString(projectName), ".doodle");
			} catch (IOException e) {
				// Auto-generated catch block
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		}
		return self;
	}

	public CaDoodleFile setSelf(File self) {
		this.self = self.getAbsoluteFile();
		return this;
	}

	public List<CSG> getCurrentState() {
		return getStateAtOperation(getCurrentOpperation());
	}

	public List<CSG> getStateAtOperation(CaDoodleOperation op) {
		if (getCurrentIndex() == 0)
			return new ArrayList<CSG>();
		List<CSG> list = getCachedCSGs(op);
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
		CaDoodleOperation key = getOpperations().get(getCurrentIndex() - 2);

		return getCachedCSGs(key);
	}

	private void setCurrentState(CaDoodleOperation op, List<CSG> currentState) {
		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onUpdate(currentState, op, this);
			} catch (Throwable e) {
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		}
	}

	private void fireSaveSuggestion() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onSaveSuggestion();
			} catch (Throwable e) {
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		}
	}

	private void fireInitializationStart() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onInitializationStart();
			} catch (Throwable e) {
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		}
	}

	private void fireRegenerateDone() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				TickToc.tic("Fire " + l.getClass());
				l.onRegenerateDone();
			} catch (Throwable e) {
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		}
	}

	private void fireRegenerateStart(CaDoodleOperation source) {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onRegenerateStart(source);
			} catch (Throwable e) {
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		}
	}

	private void fireWorkplaneChange() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onWorkplaneChange(workplane);
			} catch (Throwable e) {
				com.neuronrobotics.sdk.common.Log.error(e);
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
		if (!isInitialized())
			return null;// do not save during initialize
		if (timeCreated < 0)
			timeCreated = System.currentTimeMillis();
		String contents = toJson();
		List<CSG> currentState = getCurrentState();
		CSG thumb = null;
		for (CSG c : currentState) {
			if (c.isInGroup())
				continue;
			if (c.isHide())
				continue;
			if (thumb == null)
				thumb = c;
			else {
				thumb = thumb.dumbUnion(c);
			}
		}
		String string = getSTLThumbnailLocation();
		int currentIndex2 = getCurrentIndex();
		if (isTimelineOpen())
			getSaveUpdate().renderSplashFrame(1, "Save Doodle to " + getSelf().getName());
		if (thumb != null) {
			boolean manif = CSG.isPreventNonManifoldTriangles();
			if (manif)
				CSG.setPreventNonManifoldTriangles(false);
			FileUtil.write(Paths.get(string), thumb.toStlString());
			if (manif)
				CSG.setPreventNonManifoldTriangles(true);
		}
		FileUtils.write(getSelf(), contents, StandardCharsets.UTF_8, false);
		// }
		int num = 0;
		for (int i = 0; i < opperations.size(); i++) {
			File f = getTimelineImageFile(i);
			CaDoodleOperation op = opperations.get(i);
			if (!f.exists() && cache.get(op) != null)
				try {
					int percent = (int) (((double) i) / ((double) opperations.size()) * 100.0);
					List<CSG> process = getCachedCSGs(op);
					num++;
					if (isTimelineOpen())
						getSaveUpdate().renderSplashFrame(percent, "Save Timeline Image " + i + ".png");
					else
						Log.debug(percent + " Save Timeline Image " + i + ".png");
					setSaveImage(process, op);

				} catch (IOException e) {
					// Auto-generated catch block
					com.neuronrobotics.sdk.common.Log.error(e);
				}
		}
		if (bom != null)
			bom.save();
		if (isTimelineOpen())
			getSaveUpdate().renderSplashFrame(100, "Doodle save Done ");
		fireTimelineUpdate(num);
		// System.gc();
		return getSelf();
	}

	public File getSTLThumbnailFile() {
		File back = new File(getSTLThumbnailLocation());
		return back;
	}

	public String getSTLThumbnailLocation() {
		File folder = getSelf().getAbsoluteFile().getParentFile();
		if (!folder.exists())
			folder.mkdirs();
		String string = folder.getAbsolutePath() + delim() + "thumbnail.stl";
		return string;
	}

	private void setSaveImage(List<CSG> currentState, CaDoodleOperation op) throws IOException {
		if (getSelf() == null)
			return;
		int currentIndex2 = 0;
		for (int i = 0; i < getOpperations().size(); i++)
			if (getOpperations().get(i) == op)
				currentIndex2 = i;
//		if(currentIndex2==0)
//			return;
		File parent = getSelf().getAbsoluteFile().getParentFile();

		File imageCache = new File(getImageCacheDir().getAbsolutePath() + delim() + currentIndex2 + ".png");
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
					com.neuronrobotics.sdk.common.Log.error(e);
				}
				do {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						com.neuronrobotics.sdk.common.Log.error(e);
						return;
					}
				} while (!imageCache.exists());
				if (getOpperations().get(getOpperations().size() - 1) == op) {
					Files.copy(imageCache.toPath(), image.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				System.err.println("Thumbnail saved successfully to " + imageCache.getAbsolutePath());
			}
		} catch (Throwable t) {
			com.neuronrobotics.sdk.common.Log.error(t);
		}
	}

	private void fireTimelineUpdate(int number) {
		for (ICaDoodleStateUpdate s : listeners) {
			s.onTimelineUpdate(number);
		}
	}

	public WritableImage loadImageFromFile() {
		try {
			File parent = getSelf().getAbsoluteFile().getParentFile();
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
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		return img;
	}

	private javafx.scene.image.WritableImage loadingImageFromUIThread(List<CSG> currentState) {
		if (currentState == null)
			throw new RuntimeException("Can not be null");
		ArrayList<javafx.scene.image.WritableImage> holder = new ArrayList<WritableImage>();
		try {
			BowlerKernel.runLater(() -> {
				holder.add(ThumbnailImage.get(getCsgDBinstance(), currentState));
			});
		} catch (Throwable ex) {
			com.neuronrobotics.sdk.common.Log.error(ex);
			;
			return null;
		}
		long start = System.currentTimeMillis();
		while (holder.size() == 0) {
			try {
				Thread.sleep(16);
				// com.neuronrobotics.sdk.common.Log.error("Waiting for image to write");
			} catch (InterruptedException e) {
				// Auto-generated catch block
				com.neuronrobotics.sdk.common.Log.error(e);
				break;
			}
			if (System.currentTimeMillis() - start > 25000 && holder.size() == 0) {
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
		com.neuronrobotics.sdk.common.Log.debug("CaDoodle file reading from " + f.getAbsolutePath());
		String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
		CaDoodleFile file = fromJsonString(content, null, f, false);
		return file.getMyProjectName();
	}

	public static CaDoodleFile fromFile(File f, ICaDoodleStateUpdate listener, boolean initialize) throws Exception {
		com.neuronrobotics.sdk.common.Log.debug("CaDoodle file loading from " + f.getAbsolutePath());
		String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
		CaDoodleFile file = fromJsonString(content, listener, f, initialize);
		return file;
	}

	public ArrayList<CaDoodleOperation> getOpperations() {
		return opperations;
	}

	public void setOpperations(ArrayList<CaDoodleOperation> opperations) {
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
//		if(currentIndex==0)
//			new Exception("Current Index set to " + currentIndex).printStackTrace();
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

	public boolean isInitialized() {
		return !(percentInitialized < 1);
	}

	/**
	 * A value from 0 to 1 representing how complete the initialization is
	 * 
	 * @return
	 */
	public double getPercentInitialized() {
		return percentInitialized;
	}

	private void setPercentInitialized(double percentInitialized) {
		if (percentInitialized > 1 || percentInitialized < 0)
			throw new NumberFormatException("Number must be between 0 and 1");
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
		if (saveUpdate == null)
			return defaultSaver;
		return saveUpdate;
	}

	public void setSaveUpdate(ICadoodleSaveStatusUpdate saveUpdate) {
		this.saveUpdate = saveUpdate;
	}

	public void setTimelineVisable(boolean timelineOpen) {
		Log.debug("Setting timeline state " + timelineOpen);
		this.timelineOpen = timelineOpen;
	}

	public boolean isTimelineOpen() {
		return timelineOpen;
	}

	public void setTimeCreated(long timeCreated) {
		this.timeCreated = timeCreated;
	}

	/**
	 * @return the robots
	 */
	public HashMap<String, MobileBaseBuilder> getRobots() {
		return robots;
	}

	/**
	 * @param robots the robots to set
	 */
	public void setRobots(HashMap<String, MobileBaseBuilder> robots) {
		this.robots = robots;
	}

	public CSGDatabaseInstance getCsgDBinstance() {
		if (csgDBinstance == null) {
			if (self == null) {
				try {
					self = Files.createTempFile("temp", ".doodle").toFile();
					Log.error("Failed to have a file! " + self.getAbsolutePath());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			File parent = self.getAbsoluteFile().getParentFile();
			if (!parent.exists()) {
				parent.mkdirs();
			}
			File db = new File(parent.getAbsolutePath() + delim() + "CSGdatabase.json");
			if (!db.exists())
				try {
					db.createNewFile();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			setCsgDBinstance(new CSGDatabaseInstance(db));
		}
		return csgDBinstance;
	}

	private void setCsgDBinstance(CSGDatabaseInstance csgDBinstance) {
		this.csgDBinstance = csgDBinstance;
	}

	public File getObjectDir() {
		if (objectDir == null) {
			objectDir = new File(getImageCacheDir().getAbsolutePath() + delim() + "objectCache");
			if (!getObjectDir().exists())
				getObjectDir().mkdir();
		}
		return objectDir;
	}

	public File getImageCacheDir() {
		if (imageCacheDir == null) {
			File parent = getSelf().getAbsoluteFile().getParentFile();
			imageCacheDir=(new File(parent.getAbsolutePath() + delim() + "timeline"));
			if (!imageCacheDir.exists())
				imageCacheDir.mkdir();
		}
		return imageCacheDir;
	}


}
