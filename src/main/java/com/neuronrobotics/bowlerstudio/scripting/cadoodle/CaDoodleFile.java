package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Affine;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.creature.ImagePorviderInterface;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseBuilder;
import com.neuronrobotics.bowlerstudio.creature.ThumbnailImage;
import com.neuronrobotics.bowlerstudio.physics.TransformFactory;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.cadoodle.robot.MakeRobot;
import com.neuronrobotics.bowlerstudio.vitamins.VitaminBomManager;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.addons.kinematics.VitaminLocation;
import com.neuronrobotics.sdk.addons.kinematics.math.RotationNR;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;
import com.neuronrobotics.sdk.common.Log;
import com.neuronrobotics.sdk.common.TickToc;
import com.piro.bezier.BezierPath;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.MissingManipulatorException;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.Vector3d;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabaseInstance;
import eu.mihosoft.vrl.v3d.parametrics.Parameter;
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
	private double TextResolutionPoints = 0.5;
	@Expose(serialize = true, deserialize = true)
	private long timeCreated = -1;
	@Expose(serialize = true, deserialize = true)
	private String projectName = NO_NAME;
	@Expose(serialize = true, deserialize = true)
	private TransformNR rulerLocation = new TransformNR();
	@Expose(serialize = true, deserialize = true)
	private TransformNR workplane = new TransformNR();
	@Expose(serialize = true, deserialize = true)
	private CaDoodleParameters parameters;
	private HashMap<CSG, Bounds> boundsCache = new HashMap<CSG, Bounds>();
	private File self;
	// @Expose (serialize = false, deserialize = false)
	// private List<CSG> currentState = new ArrayList<CSG>();
	private double percentInitialized = 0;
	private final HashMap<CaDoodleOperation, List<CSG>> cache = new HashMap<CaDoodleOperation, List<CSG>>();
	private static Type TT_CaDoodleFile = new TypeToken<CaDoodleFile>() {
	}.getType();
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
			.excludeFieldsWithoutExposeAnnotation()
			.registerTypeAdapterFactory(new CaDoodleJsonOperationAdapterFactory()).create();
	private final ArrayList<ICaDoodleStateUpdate> listeners = new ArrayList<ICaDoodleStateUpdate>();
	private final ArrayList<Thread> operationRunner = new ArrayList<Thread>();
	private boolean regenerating;
	private final CopyOnWriteArrayList<CaDoodleOperation> toProcess = new CopyOnWriteArrayList<CaDoodleOperation>();
	private javafx.scene.image.WritableImage img;
	private boolean initializing;
	private static HashMap<String, VitaminBomManager> bomManagers = new HashMap<>();
	private VitaminBomManager bom;
	private IAcceptPruneForward accept = null;
	private long timeOfLastUpdate = 0;
	private OperationResult result = OperationResult.APPEND;
	private static ImagePorviderInterface imageEngine = null;
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
	private File imageCacheDir;
	private boolean saveing;

	@Override
	public String toString() {
		return projectName;
	}

	public void deleteTailFromCurrent() {
		fireRegenerateStart(getCurrentOperation());
		IAcceptPruneForward oldAccept = getAccept();

		setAccept(() -> OperationResult.PRUNE);

		try {
			pruneForward(getCurrentOperation());
		} catch (Exception ex) {
			com.neuronrobotics.sdk.common.Log.error("Failed to prune tail" + ex);
		}

		setAccept(oldAccept);
		fireRegenerateDone();
	}

	public ArrayList<MobileBase> getMobileBases() {
		ArrayList<MobileBase> back = new ArrayList<MobileBase>();
		for (MobileBaseBuilder b : robots.values()) {
			back.add(b.getMobileBase());
		}
		return back;
	}

	public void close() {
		// new Exception("CaDoodle File Closed here").printStackTrace();
		for (CaDoodleOperation op : getOperations()) {
			op.setCaDoodleFile(null);
		}
		// for (CaDoodleOperation op : cache.keySet()) {
		// clearCache(op);
		// }
		cache.clear();
		clearListeners();
		toProcess.clear();
		img = null;
		for (Thread t : operationRunner)
			t.interrupt();

	}

	public int opToIndex(CaDoodleOperation op) {
		for (int i = 0; i < opperations.size(); i++) {
			if (op == opperations.get(i))
				return i;
		}
		throw new IndexOutOfBoundsException();
	}

	// private boolean inCache(CaDoodleOperation op) {
	// int opIndex = opToIndex(op);
	// File cacheFile = new File(getObjectDir().getAbsolutePath() + delim() +
	// opIndex);
	// return cacheFile.exists();
	// }

	private List<CSG> getCachedCSGs(CaDoodleOperation op) {
		try {
			if (Platform.isFxApplicationThread()) {
				// new RuntimeException("This should not be called from the UI
				// thread!").printStackTrace();
				;
			}
		} catch (Exception ex) {
			// skipping no toolkit exceptions
		}
		// if (cache.get(op) == null && isInitialized()) {
		// try {
		// int opIndex = opToIndex(op);
		// File cacheFile = new File(getObjectDir().getAbsolutePath() + delim() +
		// opIndex + ".csg");
		// if (cacheFile.exists()) {
		// Log.debug("Loading Cached Objects from file: " +
		// cacheFile.getAbsolutePath());
		// // Log.error(new Exception());
		// ObjectInputStream ois = new ObjectInputStream(new
		// FileInputStream(cacheFile));
		// cache.put(op, (List<CSG>) ois.readObject());
		// ois.close();
		// }
		// } catch (Exception ex) {
		// Log.error(ex);
		// }
		// }
		return cache.get(op);
	}

	private void memoryCheck() {
		if (getFreeMemory() > 85) {
			com.neuronrobotics.sdk.common.Log.error("\n\nClearing Memory use: " + getFreeMemory() + "\n\n");
			CaDoodleOperation op = getCurrentOperation();
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
		List<CSG> cachedCopy = new ArrayList<CSG>(cachedCopyIn);
		try {
			if (cachedCopy.size() > 0)
				getBounds(cachedCopy, workplane, boundsCache, null);
		} catch (BoundsComputFailure e) {
			Log.error(e);
		}
		cache.put(op, cachedCopy);
		// executor.submit(() -> {
		// File cacheFile = new File(getObjectDir().getAbsolutePath() + delim() +
		// opToIndex(op) + ".csg");
		// if (cacheFile.exists() && !isInitialized())
		// return;
		// if (cacheFile.exists())
		// cacheFile.delete();
		// try (ObjectOutputStream oos = new ObjectOutputStream(new
		// FileOutputStream(cacheFile))) {
		// oos.writeObject(cachedCopy);
		// Log.debug("Saved " + cacheFile.getAbsolutePath());
		// } catch (Exception ex) {
		// Log.error(ex);
		// throw new RuntimeException(ex);
		// }
		// });

	}

	private void clearCache(CaDoodleOperation key) {
		int opIndex = opToIndex(key);
		// File cacheFile = new File(getObjectDir().getAbsolutePath() + delim() +
		// opIndex);
		// if (cacheFile.exists())
		// cacheFile.delete();

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
		// if (initializing)
		// throw new RuntimeException("Can not initialize while initializing.");
		fireInitializationStart();
		BezierPath.setMaximumInterpolationStep(TextResolutionPoints);
		getImageEngine();
		initializing = true;
		if (timeCreated < 0)
			timeCreated = System.currentTimeMillis();
		if (self != null) {
			getImageCacheDir();
			getObjectDir();
			getCsgDBinstance();// initialize the instance on initialize
			// CSGDatabase.setInstance(getCsgDBinstance());

			getBom().clear();
			getBom().save();
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
		ArrayList<CaDoodleOperation> toRem = new ArrayList<CaDoodleOperation>();
		for (int i = 0; i < getOperations().size(); i++) {
			CaDoodleOperation op = getOperations().get(i);
			if (op == null)
				continue;
			op.setCaDoodleFile(this);
			setPercentInitialized(((double) i) / (double) getOperations().size());
			// if(!inCache(op))
			try {
				process(op);
				setSaveImage(getCurrentState(), op);
			} catch (Throwable t) {
				com.neuronrobotics.sdk.common.Log.error(t);
				// indexStarting = i ;
				// break;
				// toRem.add(op);
			}
		}
		// operations.removeAll(toRem);
		// if(indexStarting>operations.size()) {
		// indexStarting = operations.size();
		// }

		setPercentInitialized(1);
		try {
			setCurrentIndex(indexStarting);
			fireOnUpdate(getCurrentOperation());
			loadImageFromFile();
			for (ICaDoodleStateUpdate l : listeners) {
				try {
					l.onInitializationDone();
				} catch (Throwable e) {
					com.neuronrobotics.sdk.common.Log.error(e);
				}
			}
			updateBoM();
		} catch (Throwable t) {
			Log.error(t);
		}
		initializing = false;
	}

	public void updateBoM() {
		if (bom == null)
			return;
		getBom().clear();
		getBom().save();
		for (CSG c : getCurrentState()) {
			String type = null;
			String size = null;
			Set<String> parameters = c.getParameters(getCsgDBinstance());
			for (String param : parameters) {
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
					getBom().addVitamin(new VitaminLocation(false, c.getName(), type, size, new TransformNR()));
					break;
				}
			}

		}
		getBom().save();
	}

	public static VitaminBomManager getBillOfMaterials(CaDoodleFile cf) {

		String strValue = cf.getSelf().getAbsolutePath();
		File file = new File(strValue).getParentFile();
		if (bomManagers.get(strValue) == null) {
			bomManagers.put(strValue, new VitaminBomManager(file));
		}
		return bomManagers.get(strValue);
	}

	// private static String getCadoodleFileLocation() {
	//
	// return get;
	// }
	public Thread regenerateAll() throws FailedToApplyOperation {
		if (opperations.size() > 0)
			return regenerateFrom(opperations.get(0));
		throw new FailedToApplyOperation("Failed to apply regenerate, no operations");
	}

	public Thread regenerateFrom(CaDoodleOperation source) throws FailedToApplyOperation {
		if (initializing)
			throw new FailedToApplyOperation("Failed to apply regenerate, it is initializing");
		while (isRegenerating() || isOperationRunning() || source == null) {
			com.neuronrobotics.sdk.common.Log.error(new Exception("Operation Running, bailing"));
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		fireRegenerateStart(source);
		int endIndex = getCurrentIndex();
		double size = getOperations().size();
		if (endIndex != size) {
			// new Exception("Regenerationg from a position back in time " + endIndex + "
			// but have " + size)
			// .printStackTrace();
		}
		Thread t = null;
		CaDoodleFile cf = this;

		t = new Thread() {
			public void run() {
				this.setName("Regeneration Threads");

				try {
					try {
						timeOfLastUpdate = System.currentTimeMillis();
						setRegenerating(true);
						// com.neuronrobotics.sdk.common.Log.error("Regenerating Object from
						// "+source.getType());
						int opIndex = -1;
						for (int i = 0; i < size; i++) {
							CaDoodleOperation op = getOperations().get(i);
							if (source == op) {
								opIndex = i;
								break;
							}
						}
						setCurrentIndex(opIndex);
						boundsCache.clear();
						for (; getCurrentIndex() < size;) {
							int percent = (int) (((double) getCurrentIndex()) / ((double) getOperations().size())
									* 100.0);
							setCurrentIndex(getCurrentIndex() + 1);
							setPercentInitialized(((double) getCurrentIndex()) / size);
							// com.neuronrobotics.sdk.common.Log.error("Regenerating "+currentIndex);
							int currentIndex2 = getCurrentIndex() - 1;
							CaDoodleOperation op = getOperations().get(currentIndex2);
							getSaveUpdate().renderSplashFrame(percent,
									"Regenerating " + op.getType() + " " + currentIndex2);
							getTimelineImageFile(op).delete();

							// clearCache(op);
							try {
								op.setCaDoodleFile(cf);
								List<CSG> previouState = getPreviouState();

								List<CSG> process = op.process(previouState);
								storeResultInCache(op, process);
								fireOnUpdate(op);
								setSaveImage(process, op);
							} catch (Throwable tr) {
								com.neuronrobotics.sdk.common.Log.error(tr);
							}
						}
						if (getCurrentIndex() != endIndex) {
							setCurrentIndex(endIndex);
							fireOnUpdate(getCurrentOperation());
						}
					} catch (Exception ex) {
						com.neuronrobotics.sdk.common.Log.error(ex);;
					}

					setRegenerating(false);
					setPercentInitialized(1);
					updateBoM();
					fireSaveSuggestion();
					fireRegenerateDone();
				} catch (Throwable th) {
					com.neuronrobotics.sdk.common.Log.error(th);
					setRegenerating(false);
					fireSaveSuggestion();
					fireRegenerateDone();
				}
				operationRunner.remove(this);
			}

		};
		operationRunner.add(t);
		t.start();
		return t;
	}

	public Thread regenerateCurrent() throws FailedToApplyOperation {
		if (isOperationRunning()) {
			throw new FailedToApplyOperation("Regenerate failed because operation is running");
		}
		if (initializing) {
			throw new FailedToApplyOperation("Regenerate failed because operation is running");
		}
		CaDoodleOperation op = getCurrentOperation();

		return regenerateFrom(op);

	}

	private void process(CaDoodleOperation op) throws Exception {
		op.setCaDoodleFile(this);
		List<CSG> process = null;
		Exception ex = null;
		try {
			process = op.process(getCurrentState());
			if (MakeRobot.class.isInstance(op)) {
				MakeRobot mr = (MakeRobot) op;
				getRobots().put(mr.getName(), mr.getBuilder());
			}
		} catch (Exception ex1) {
			ex = ex1;
		}
		if (process == null) {
			process = getCurrentState();
		}
		if (process.size() == 0) {
			Log.error("Nothing returned in the process step?");
		}
		int currentIndex2 = getCurrentIndex();
		storeResultInCache(op, process);
		setCurrentIndex(currentIndex2 + 1);
		fireOnUpdate(op);

		if (ex != null)
			throw ex;
	}

	public boolean isOperationRunning() {
		for (int i = 0; i < operationRunner.size(); i++) {
			Thread t = operationRunner.get(i);
			if (t != null) {
				if (!t.isAlive()) {
					operationRunner.remove(t);
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

	public Thread addOperation(CaDoodleOperation o) throws CadoodleConcurrencyException {
		if (o == null)
			throw new NullPointerException();
		toProcess.add(o);
		if (isOperationRunning()) {
			com.neuronrobotics.sdk.common.Log.error(new Exception("Operation Running, bailing"));
			return operationRunner.get(0);
		}
		Thread t = null;
		t = new Thread() {
			public void run() {
				try {
					timeOfLastUpdate = System.currentTimeMillis();
					while (toProcess.size() > 0) {
						result = OperationResult.APPEND;
						this.setName("addOperation Thread " + toProcess.size());
						CaDoodleOperation op = toProcess.remove(0);
						com.neuronrobotics.sdk.common.Log.debug("Adding Operation " + op);
						if (getCurrentIndex() != getOperations().size()) {
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
								getOperations().add(op);
								process(op);
								setSaveImage(getCurrentState(), op);
							} catch (Exception ex) {
								com.neuronrobotics.sdk.common.Log.error(ex);;
							}
						}
						if (getResult() == OperationResult.INSERT) {
							getOperations().add(getCurrentIndex(), op);
							try {
								process(op);
								setSaveImage(getCurrentState(), op);
							} catch (Exception e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							try {
								regenerateFrom(op).join();
							} catch (InterruptedException e) {
								com.neuronrobotics.sdk.common.Log.error(e);
							}
							fireOnUpdate(getCurrentOperation());
						}
						if (getResult() == OperationResult.ABORT) {
							fireOnUpdate(getCurrentOperation());
						}
					}
					updateBoM();
					fireSaveSuggestion();
					fireRegenerateDone();
				} catch (Throwable t) {
					Log.error(t);
				}
				operationRunner.remove(this);

			}
		};
		operationRunner.add(t);
		t.start();
		return t;
	}

	public Thread deleteOperation(CaDoodleOperation op) throws FailedToApplyOperation {
		if (op == null)
			throw new NullPointerException();
		if (isOperationRunning()) {
			throw new FailedToApplyOperation("Delete failed because operation is running");
		}
		Thread t = null;
		t = new Thread() {
			public void run() {
				try {
					timeOfLastUpdate = System.currentTimeMillis();
					this.setName("deleteOperation Thread " + toProcess.size());
					int index = 0;
					for (int i = 0; i < getOperations().size(); i++)
						if (getOperations().get(i) == op)
							index = i;
					getOperations().remove(op);
					op.pruneCleanup();
					// if (index == getOperations().size())
					// index -= 1;
					if (index < 1)
						index = 1;
					CaDoodleOperation newTar = getOperations().get(index - 1);
					setCurrentIndex(index);
					try {
						regenerateFrom(newTar).join();
					} catch (InterruptedException e) {
						com.neuronrobotics.sdk.common.Log.error(e);
					}
					fireOnUpdate(getCurrentOperation());
					updateBoM();
					fireSaveSuggestion();
				} catch (Throwable t) {
					Log.error(t);
				}
				operationRunner.remove(this);
			}
		};
		operationRunner.add(t);
		t.start();
		return t;
	}

	public static CSG getByName(List<CSG> back, String name) throws NameMissingException {
		for (CSG c : back) {
			if (c.getName().contentEquals(name))
				return c;
		}
		throw new NameMissingException("Fail! there was no object named " + name);
	}

	public static int applyToAllConstituantElements(boolean addRet, List<String> targetNames, ArrayList<CSG> back,
			ICadoodleRecursiveEvent p, int depth) {
		HashSet<String> appliedMemory = new HashSet<String>();
		return applyToAllConstituantElements(addRet, targetNames, back, p, depth, appliedMemory);
	}

	public static int applyToAllConstituantElements(boolean addRet, List<String> targetNames, ArrayList<CSG> back,
			ICadoodleRecursiveEvent p, int depth, HashSet<String> appliedMemory) {

		// for (CSG c : back) {
		// c.getStorage().delete("applyToAllConstituantElements");
		// }
		for (int i = 0; i < targetNames.size(); i++) {
			String s = targetNames.get(i);
			try {
				CSG c = getByName(back, s);
				// if (c.isInGroup())
				// continue;
			} catch (NameMissingException e) {
				continue;
			}

			applyToAllConstituantElementsInternal(addRet, s, back, p, depth, appliedMemory);
		}
		// for (CSG c : back) {
		// c.getStorage().delete("applyToAllConstituantElements");
		// }
		return back.size();
	}

	public static int applyToAllConstituantElements(boolean addRet, String targetName, ArrayList<CSG> back,
			ICadoodleRecursiveEvent p, int depth, HashSet<String> appliedMemory) {
		List<String> targetNames = Arrays.asList(targetName);
		return applyToAllConstituantElements(addRet, targetNames, back, p, depth, appliedMemory);
	}

	private static int applyToAllConstituantElementsInternal(boolean addRet, String targetName, ArrayList<CSG> back,
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
			// if (csg.getStorage().getValue("applyToAllConstituantElements").isPresent()) {
			// Log.error(new Exception("Can not apply op to leaf node twice!"));
			// continue;
			// }
			// boolean inGroup = csg.isInGroup();
			boolean thisCSGIsInGroupNamedAfterTarget = csg.checkGroupMembership(targetName);
			String thisCSGName = csg.getName();
			boolean thisCSGIsTheTarget = thisCSGName.contentEquals(targetName);
			boolean groupResult = csg.isGroupResult();

			if (thisCSGIsTheTarget) {
				// move it
				ArrayList<CSG> tmpToAdd = p.process(csg, depth);
				// csg.getStorage().set("applyToAllConstituantElements", true);
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
		for (int i = 0; i < getOperations().size(); i++) {
			CaDoodleOperation key = getOperations().get(i);
			if (key == test) {
				File file = getTimelineImageFile(i - 1);
				return file;
			}
		}
		throw new RuntimeException("File not found!");
	}

	private File getTimelineImageFile(int i) {
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
			for (int i = getCurrentIndex() - 1; i < getOperations().size(); i++) {
				CaDoodleOperation key = getOperations().get(i);
				if (i >= getCurrentIndex()) {
					clearCache(key);
				}
				File imageCache = getTimelineImageFile(i);
				// System.err.println("Deleting " + imageCache.getAbsolutePath());
				imageCache.delete();
			}
		if (res == OperationResult.PRUNE) {
			List<CaDoodleOperation> subList = (List<CaDoodleOperation>) getOperations().subList(0, getCurrentIndex());
			for (int i = getCurrentIndex(); i < getOperations().size(); i++) {
				getOperations().get(i).pruneCleanup();
			}
			ArrayList<CaDoodleOperation> newList = new ArrayList<CaDoodleOperation>();
			newList.addAll(subList);
			setOperations(newList);
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
				continue;
			names.add(c.getName());
			CSG cachedVer = cloneCSG(c).setStorage(new PropertyStorage()).syncProperties(getCsgDBinstance(), c)
					.setName(c.getName()).setRegenerate(c.getRegenerate()).setID(c);
			if (c.hasManufacturing())
				cachedVer.setManufacturing(c.getManufacturing());
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
		CSG csg = dyingCSG.clone();

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
		CaDoodleOperation op = getCurrentOperation();
		if (isBackAvailable())
			setCurrentIndex(getCurrentIndex() - 1);
		boundsCache.clear();
		fireOnUpdate(getCurrentOperation());
		if (ICadoodleOperationUndo.class.isInstance(op)) {
			ICadoodleOperationUndo un = (ICadoodleOperationUndo) op;
			un.undo();
		}
		fireSaveSuggestion();
	}

	public void forward() {
		if (isForwardAvailable())
			setCurrentIndex(getCurrentIndex() + 1);
		boundsCache.clear();
		fireOnUpdate(getCurrentOperation());
		CaDoodleOperation op = getCurrentOperation();
		if (ICadoodleOperationUndo.class.isInstance(op)) {
			ICadoodleOperationUndo un = (ICadoodleOperationUndo) op;
			un.redo();
		}
		fireSaveSuggestion();
	}

	public void moveToOpIndex(int newIndex) {
		if (newIndex > getOperations().size())
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
		boundsCache.clear();
		setCurrentIndex(ni);
		fireOnUpdate(getCurrentOperation());
		fireSaveSuggestion();
	}

	public boolean isBackAvailable() {
		return getCurrentIndex() > 1;
	}

	// private void fireCurrentStateOnUpdate() {
	// CaDoodleOperation key = getCurrentOperation();
	// if (key == null)
	// return;
	// com.neuronrobotics.sdk.common.Log.debug("Current operation results: " +
	// key.getType());
	// fireOnUpdate(key, getStateAtOperation(key));
	// }

	public CaDoodleOperation getCurrentOperation() {
		if (getCurrentIndex() == 0)
			return null;
		return getOperations().get(getCurrentIndex() - 1);
	}

	public boolean isForwardAvailable() {
		return getCurrentIndex() < getOperations().size();
	}

	public File getSelf() {
		if (self == null) {
			try {
				self = File.createTempFile(DownloadManager.sanitizeString(getMyProjectName()), ".doodle");
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
		return getStateAtOperation(getCurrentOperation());
	}

	public List<CSG> getStateAtOperation(CaDoodleOperation op) {
		if (getCurrentIndex() == 0)
			return new ArrayList<CSG>();
		List<CSG> list = getCachedCSGs(op);
		if (list == null) {
			list = new ArrayList<CSG>();
		}
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
		CaDoodleOperation key = getOperations().get(getCurrentIndex() - 2);

		return getCachedCSGs(key);
	}

	private void fireOnUpdate(CaDoodleOperation op) {
		ArrayList<CSG> toClear = new ArrayList<CSG>();
		for (CSG c : getCurrentState()) {
			for (String s : op.getNamesAddedInThisOperation()) {
				if (c.getName().contentEquals(s))
					toClear.add(c);
			}

		}
		List<CSG> currentState = getStateAtOperation(op);
		if (currentState.size() > 0)
			try {
				getBounds(currentState, workplane, boundsCache, toClear);
			} catch (BoundsComputFailure e) {
				Log.error(e);
			}
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

	public File save() throws IOException, SaveOverwriteException {
		return save(false);
	}

	public File save(boolean ignoreUninitialized) throws IOException, SaveOverwriteException {
		if (!isInitialized() && !ignoreUninitialized)
			throw new SaveOverwriteException("Uninitialized");
		if (initializing && !ignoreUninitialized)
			throw new SaveOverwriteException("Still initializing");
		if (saveing)
			throw new SaveOverwriteException("Saving right now");
		saveing = true;
		try {
			if (timeCreated < 0)
				timeCreated = System.currentTimeMillis();
			String contents = toJson();
			FileUtils.write(getSelf(), contents, StandardCharsets.UTF_8, false);
			try {
				if (getCsgDBinstance().getDatabase().size() > 0)
					getCsgDBinstance().saveDatabase();
			} catch (Exception e) {
				Log.error(e);
			}
			if (getBom() != null)
				getBom().save();
			List<CSG> currentState = getCurrentState();

			String string = get3mfThumbnailLocation();

			CSG.toThreeMF(currentState, false, Paths.get(string));
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
						// if (isTimelineOpen())
						// getSaveUpdate().renderSplashFrame(percent, "Save Timeline Image " + i +
						// ".png");
						// else
						Log.debug(percent + " Save Timeline Image " + i + ".png");
						setSaveImage(process, op);

					} catch (IOException e) {
						// Auto-generated catch block
						com.neuronrobotics.sdk.common.Log.error(e);
					}
			}

			// if (isTimelineOpen())
			// getSaveUpdate().renderSplashFrame(100, "Doodle save Done ");

		} catch (Throwable t) {
			Log.error(t);
			saveing = false;
			throw new SaveOverwriteException(t);
		}
		saveing = false;
		return getSelf();
	}

	public File getBomFile() {
		return getBom().getBomFile();
	}

	public File getBomCsv() {
		return getBom().getBomCsv();
	}

	public File get3mfThumbnailFile() {
		File back = new File(get3mfThumbnailLocation());
		return back;
	}

	public String get3mfThumbnailLocation() {
		File folder = getSelf().getAbsoluteFile().getParentFile();
		if (!folder.exists())
			folder.mkdirs();
		String string = folder.getAbsolutePath() + delim() + "thumbnail.3mf";
		return string;
	}

	private void setSaveImage(List<CSG> currentState, CaDoodleOperation op) throws IOException {
		if (getSelf() == null)
			return;
		int currentIndex2 = opToIndex(op);
		// if(currentIndex2==0)
		// return;
		File parent = getSelf().getAbsoluteFile().getParentFile();

		File imageCache = getTimelineImageFile(op);
		File image = new File(parent.getAbsolutePath() + delim() + "snapshot.png");

		if (imageCache.exists())
			return;
		try {
			WritableImage image2 = loadingImageFromUIThread(currentState, imageCache);
			if (image2 != null) {
				BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image2, null);
				try {
					ImageIO.write(bufferedImage, "png", imageCache);
				} catch (IOException e) {
					// com.neuronrobotics.sdk.common.Log.error("Error saving image: " +
					// e.getMessage());
					com.neuronrobotics.sdk.common.Log.error(e);
					return;
				}
				do {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						com.neuronrobotics.sdk.common.Log.error(e);
						return;
					}
				} while (!imageCache.exists());
				if (getOperations().get(getOperations().size() - 1) == op) {
					Files.copy(imageCache.toPath(), image.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
				System.err.println("Thumbnail saved successfully to " + imageCache.getAbsolutePath());
				fireTimelineUpdateListeners(currentIndex2, image2);
			}
		} catch (Throwable t) {
			com.neuronrobotics.sdk.common.Log.error(t);
		}
	}

	private void fireTimelineUpdateListeners(int number, WritableImage image) {
		for (ICaDoodleStateUpdate s : listeners) {
			s.onTimelineUpdate(number, image);
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
			}

		} catch (Exception e) {
			com.neuronrobotics.sdk.common.Log.error("Error loading image: " + e.getMessage());
			// com.neuronrobotics.sdk.common.Log.error(e);
		}
		if (img == null) {
			img = new WritableImage(10, 10);
			Log.error("Cadoodle File Image failed to read");
		}
		return img;
	}

	public Bounds getBounds(List<CSG> incoming) throws BoundsComputFailure {
		return getBounds(incoming, getWorkplane(), getBoundsCache(), null);
	}

	static Bounds getBounds(List<CSG> incoming, TransformNR frame, HashMap<CSG, Bounds> cache, List<CSG> toClear)
			throws BoundsComputFailure {
		if (cache == null)
			cache = new HashMap<>();
		Vector3d min = null;
		Vector3d max = null;
		// TickToc.tic("getSellectedBounds "+incoming.size());

		for (CSG csg : incoming) {
			if (csg.isHide() || csg.isInGroup()) {
				// Log.debug("Skipping bounds for " + csg.getName() + " hide:" + csg.isHide() +
				// " in group:"
				// + csg.isInGroup());
				continue;
			}
			boolean forceClear = false;
			if (toClear != null)
				for (CSG tc : toClear)
					if (tc.getName().contentEquals(csg.getName()))
						forceClear = true;
			if (cache.get(csg) == null || forceClear) {
				if (Platform.isFxApplicationThread())
					Log.error(new RuntimeException("Computed bounds in UI thread!"));
				else
					Log.debug("Computing bounds for " + csg.getName());
				// Log.error(new RuntimeException("Computing bounds for " + csg.getName()));
				Transform inverse = TransformFactory.nrToCSG(frame).inverse();

				if (csg.hasManipulator()) {
					Affine af;
					try {
						af = csg.getManipulator();
						TransformNR afNR = TransformFactory.affineToNr(af);
						inverse = TransformFactory.nrToCSG(afNR.inverse().times(frame)).inverse();
					} catch (MissingManipulatorException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				cache.put(csg, csg.transformed(inverse).getBounds());
			}
			Bounds b = cache.get(csg);
			Vector3d min2 = b.getMin().clone();
			Vector3d max2 = b.getMax().clone();
			if (min == null && min2 != null)
				min = min2.clone();
			if (max == null && max2 != null)
				max = max2.clone();
			if (min2.x < min.x)
				min.x = min2.x;
			if (min2.y < min.y)
				min.y = min2.y;
			if (min2.z < min.z)
				min.z = min2.z;
			if (max.x < max2.x)
				max.x = max2.x;
			if (max.y < max2.y)
				max.y = max2.y;
			if (max.z < max2.z)
				max.z = max2.z;
			// TickToc.tic("Bounds for "+c.getName());
			if (min == null || max == null) {
				Log.error("Failed to find bounds!");
				throw new BoundsComputFailure("Failed to find bounds!!");
			}
		}
		if (min == null || max == null)
			throw new BoundsComputFailure("Failed to get bounds for objects: " + incoming);
		return new Bounds(min, max);
	}

	private javafx.scene.image.WritableImage loadingImageFromUIThread(List<CSG> currentState, File destination) {

		if (currentState == null || imageEngine == null)
			throw new RuntimeException("Can not be null");
		ArrayList<javafx.scene.image.WritableImage> holder = new ArrayList<WritableImage>();

		try {
			holder.add(imageEngine.get(getCsgDBinstance(), currentState, destination));
		} catch (Exception ex) {
			Log.error(ex);
			holder.add(new WritableImage(100, 100));
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

	public ArrayList<CaDoodleOperation> getOperations() {
		return opperations;
	}

	public void setOperations(ArrayList<CaDoodleOperation> operations) {
		this.opperations = operations;
		currentIndex = operations.size();
	}

	public TransformNR getWorkplane() {
		if (workplane == null)
			workplane = new TransformNR();
		RotationNR r = workplane.getRotation();
		r.normalize();
		return workplane;
	}

	public void setWorkplane(TransformNR workplane) {
		this.workplane = workplane;
		try {
			// clear all bounds and recompute with the workplane
			getBounds(getCurrentState(), workplane, boundsCache, getCurrentState());
		} catch (BoundsComputFailure e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		fireWorkplaneChange();
		fireSaveSuggestion();
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public void setCurrentIndex(int currentIndex) {
		// if(currentIndex==0)
		// new Exception("Current Index set to " + currentIndex).printStackTrace();
		if ((currentIndex - 1) >= getOperations().size())
			throw new RuntimeException("Fail! Can not set an index greater than the available operations");
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
		return !(percentInitialized < 1) && !initializing;
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
		com.neuronrobotics.sdk.common.Log.debug("Setting Ruler Location " + rulerLocation);
		// com.neuronrobotics.sdk.common.Log.error(new Exception());
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

	public void setTimelineVisible(boolean timelineOpen) {
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
			this.csgDBinstance = (new CSGDatabaseInstance(db));
		}
		return csgDBinstance;
	}

	// private void setCsgDBinstance(CSGDatabaseInstance csgDBinstance) {
	// this.csgDBinstance = csgDBinstance;
	// }

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
			imageCacheDir = (new File(parent.getAbsolutePath() + delim() + "timeline"));
			if (!imageCacheDir.exists())
				imageCacheDir.mkdir();
		}
		return imageCacheDir;
	}

	public VitaminBomManager getBom() {
		if (bom == null) {
			bom = CaDoodleFile.getBillOfMaterials(this);
		}
		return bom;
	}

	public File getIgnoreFile() {
		return new File(getSelf().getParent() + delim() + "ignoreMeInProjectManager");
	}

	public boolean isIgnore() {
		return getIgnoreFile().exists();
	}

	public void setIgnore() {
		File f = getIgnoreFile();
		if (!f.exists())
			try {
				f.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	public void clearIgnore() {
		File f = getIgnoreFile();
		if (f.exists())
			try {
				f.delete();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	public static ImagePorviderInterface getImageEngine() {
		if (imageEngine == null)
			setImageEngine(new ThumbnailImage());
		return imageEngine;
	}

	public static void setImageEngine(ImagePorviderInterface ie) {
		imageEngine = ie;
	}

	public CaDoodleParameters getParameters() {
		if (parameters == null)
			parameters = new CaDoodleParameters();
		parameters.setDb(csgDBinstance);
		return parameters;
	}

	public HashMap<CSG, Bounds> getBoundsCache() {
		return boundsCache;
	}

	public double getTextResolutionPoints() {
		return TextResolutionPoints;
	}

	public void setTextResolutionPoints(double textResolutionPoints) {
		TextResolutionPoints = textResolutionPoints;
		BezierPath.setMaximumInterpolationStep((double) getTextResolutionPoints());
		Log.debug("Setting path resolution to " + TextResolutionPoints);
	}
}
