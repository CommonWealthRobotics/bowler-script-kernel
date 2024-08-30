package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;

import static com.neuronrobotics.bowlerstudio.scripting.DownloadManager.*;

public class CaDoodleFile {
	public static final String NO_NAME = "NoName";
	@Expose(serialize = true, deserialize = true)
	private ArrayList<ICaDoodleOpperation> opperations = new ArrayList<ICaDoodleOpperation>();
	@Expose(serialize = true, deserialize = true)
	private int currentIndex = 0;
	@Expose(serialize = true, deserialize = true)
	private String projectName = NO_NAME;
	@Expose(serialize = true, deserialize = true)
	private TransformNR workplane = new TransformNR();
	@Expose(serialize = false, deserialize = false)
	private File selfInternal;
//	@Expose (serialize = false, deserialize = false)
//	private List<CSG> currentState = new ArrayList<CSG>();
	@Expose(serialize = false, deserialize = false)
	private HashMap<ICaDoodleOpperation, List<CSG>> cache = new HashMap<ICaDoodleOpperation, List<CSG>>();
	private static Type TT_CaDoodleFile = new TypeToken<CaDoodleFile>() {
	}.getType();
	private static Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting()
			.excludeFieldsWithoutExposeAnnotation().registerTypeAdapterFactory(new ICaDoodleOperationAdapterFactory())
			.create();
	private ArrayList<ICaDoodleStateUpdate> listeners = new ArrayList<ICaDoodleStateUpdate>();
	private Thread opperationRunner = null;
	private boolean regenerating;
	private CopyOnWriteArrayList<ICaDoodleOpperation> toProcess = new CopyOnWriteArrayList<ICaDoodleOpperation>();

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
		if (selfInternal != null) {
			File db = new File(selfInternal.getAbsoluteFile().getParent() + delim() + "CSGdatabase.json");
			CSGDatabase.setDbFile(db);
		}
		int indexStarting = getCurrentIndex();
		setCurrentIndex(0);
		for (int i = 0; i < opperations.size(); i++) {
			ICaDoodleOpperation op = opperations.get(i);
			try {
				process(op);
			} catch (Throwable t) {
				t.printStackTrace();
				pruneForward();
				return;
			}
		}
		setCurrentIndex(indexStarting);
		updateCurrentFromCache();
	}

	public Thread regenerateFrom(ICaDoodleOpperation source) {
		if (regenerating || isOperationRunning()) {
			System.out.println("Opperation is running, ignoring regen");
			return opperationRunner;
		}
		int endIndex = getCurrentIndex();
		int size = opperations.size();
		if (endIndex != size) {
//			new Exception("Regenerationg from a position back in time " + endIndex + " but have " + size)
//					.printStackTrace();
		}
		opperationRunner = new Thread(() -> {
			opperationRunner.setName("Regeneration Thread");
			regenerating = true;
			// System.out.println("Regenerating Object from "+source.getType());
			int opIndex = 0;
			for (int i = 0; i < size; i++) {
				ICaDoodleOpperation op = opperations.get(i);
				if (source == op) {
					opIndex = i;
					break;
				}
			}
			setCurrentIndex(opIndex);
			for (; getCurrentIndex() < size;) {
				setCurrentIndex(getCurrentIndex() + 1);
				// System.out.println("Regenerating "+currentIndex);
				ICaDoodleOpperation op = opperations.get(getCurrentIndex() - 1);
				storeResultInCache(op, op.process(getPreviouState()));
				setCurrentState(op);
			}
			if (getCurrentIndex() != endIndex) {
				setCurrentIndex(endIndex);
				updateCurrentFromCache();
			}
			regenerating = false;
		});
		opperationRunner.start();
		return opperationRunner;
	}

	public Thread regenerateCurrent() {
		if (isOperationRunning()) {
			return opperationRunner;
		}
		opperationRunner = new Thread(() -> {
			opperationRunner.setName("regenerateCurrent Thread");

			ICaDoodleOpperation op = currentOpperation();
			storeResultInCache(op, op.process(getPreviouState()));
			setCurrentState(op);
			opperationRunner = null;
		});
		opperationRunner.start();
		return opperationRunner;

	}

	private void process(ICaDoodleOpperation op) {
		storeResultInCache(op, op.process(getCurrentState()));
		setCurrentIndex(getCurrentIndex() + 1);
		setCurrentState(op);
	}

	public boolean isOperationRunning() {
		if (opperationRunner != null)
			if (!opperationRunner.isAlive())
				opperationRunner = null;
		return opperationRunner != null;
	}

	public Thread addOpperation(ICaDoodleOpperation o) throws CadoodleConcurrencyException {
		toProcess.add(o);
		if (isOperationRunning()) {

			return opperationRunner;
		}
		opperationRunner = new Thread(() -> {

			while (toProcess.size() > 0) {
				opperationRunner.setName("addOpperation Thread " + toProcess.size());
				ICaDoodleOpperation op = toProcess.remove(0);
				if (getCurrentIndex() != getOpperations().size()) {
					pruneForward();
				}
				try {
					getOpperations().add(op);
					process(op);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
			opperationRunner = null;
		});
		opperationRunner.start();
		return opperationRunner;
	}

	private void pruneForward() {
		for (int i = getCurrentIndex(); i < getOpperations().size(); i++) {
			List<CSG> back = cache.remove(getOpperations().get(i));
			back.clear();
		}
		List<ICaDoodleOpperation> subList = (List<ICaDoodleOpperation>) getOpperations().subList(0, getCurrentIndex());
		ArrayList<ICaDoodleOpperation> newList = new ArrayList<ICaDoodleOpperation>();
		newList.addAll(subList);
		setOpperations(newList);
		System.err.println("Pruning forward here!");
	}

	private void storeResultInCache(ICaDoodleOpperation op, List<CSG> process) {
		ArrayList<CSG> cachedCopy = new ArrayList<CSG>();
		HashSet<String> names = new HashSet<>();
		for (CSG c : process) {
			if (names.contains(c.getName()))
				throw new RuntimeException("There can not be 2 objects with the same name after an opperation!");
			names.add(c.getName());
			cachedCopy.add(c.clone().setStorage(new PropertyStorage()).syncProperties(c).setName(c.getName()));
		}
		cache.put(op, cachedCopy);
	}

	public void back() {
		if (isBackAvailible())
			setCurrentIndex(getCurrentIndex() - 1);
		updateCurrentFromCache();
	}

	public boolean isBackAvailible() {
		return getCurrentIndex() > 1;
	}

	private void updateCurrentFromCache() {
		ICaDoodleOpperation key = currentOpperation();
		System.out.println("Current opperation results: " + key.getType());
		setCurrentState(key);
	}

	public ICaDoodleOpperation currentOpperation() {
		return getOpperations().get(getCurrentIndex() - 1);
	}

	public void forward() {
		if (isForwardAvailible())
			setCurrentIndex(getCurrentIndex() + 1);
		updateCurrentFromCache();
	}

	public boolean isForwardAvailible() {
		return getCurrentIndex() < getOpperations().size();
	}

	public File getSelf() {
		if (selfInternal == null) {
			try {
				selfInternal = File.createTempFile(DownloadManager.sanitizeString(projectName), ".doodle");
			} catch (IOException e) {
				// TODO Auto-generated catch block
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
		if (getCurrentIndex() == 0)
			return new ArrayList<CSG>();
		return cache.get(currentOpperation());
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
		return cache.get(getOpperations().get(getCurrentIndex() - 2));
	}

	private void setCurrentState(ICaDoodleOpperation op) {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onUpdate(getCurrentState(), op, this);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	public String getProjectName() {
		return projectName;
	}

	public CaDoodleFile setProjectName(String projectName) {
		this.projectName = projectName;
		return this;
	}

	public String toJson() {
		return gson.toJson(this);
	}

	public File save() throws IOException {

		synchronized (getSelf()) {
			String contents = toJson();
			FileUtils.write(getSelf(), contents, StandardCharsets.UTF_8, false);
		}
		return getSelf();
	}

	public static CaDoodleFile fromJsonString(String content) throws Exception {
		return fromJsonString(content, null, null, true);
	}

	public static CaDoodleFile fromJsonString(String content, ICaDoodleStateUpdate listener, File self,
			boolean initialize) throws Exception {
		CaDoodleFile file = gson.fromJson(content, TT_CaDoodleFile);
		if (listener != null) {
			file.addListener(listener);
		}
		if (self != null) {
			file.setSelf(self);
		}
		if (initialize)
			file.initialize();
		return file;
	}

	public static CaDoodleFile fromFile(File f) throws Exception {
		return fromFile(f, null, true);
	}

	public static String getProjectName(File f) throws Exception {
		System.out.println("CaDoodle file reading from " + f.getAbsolutePath());
		String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
		CaDoodleFile file = fromJsonString(content, null, f, false);
		return file.getProjectName();
	}

	public static CaDoodleFile fromFile(File f, ICaDoodleStateUpdate listener, boolean initialize) throws Exception {
		System.out.println("CaDoodle file loading from " + f.getAbsolutePath());
		String content = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
		CaDoodleFile file = fromJsonString(content, listener, f, initialize);
		return file;
	}

	public ArrayList<ICaDoodleOpperation> getOpperations() {
		return opperations;
	}

	public void setOpperations(ArrayList<ICaDoodleOpperation> opperations) {
		this.opperations = opperations;
	}

	public TransformNR getWorkplane() {
		if (workplane == null)
			workplane = new TransformNR();
		return workplane;
	}

	public void setWorkplane(TransformNR workplane) {
		this.workplane = workplane;
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public void setCurrentIndex(int currentIndex) {
		//new Exception("Current Index set to " + currentIndex).printStackTrace();
		this.currentIndex = currentIndex;
	}

}
