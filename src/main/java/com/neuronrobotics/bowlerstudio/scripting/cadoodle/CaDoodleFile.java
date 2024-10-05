package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.awt.image.BufferedImage;
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

import javax.imageio.ImageIO;
import javafx.scene.image.WritableImage;
import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.ThumbnailImage;
import com.neuronrobotics.bowlerstudio.scripting.DownloadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.math.TransformNR;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.PropertyStorage;
import eu.mihosoft.vrl.v3d.parametrics.CSGDatabase;
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
	private TransformNR workplane = new TransformNR();
	@Expose(serialize = false, deserialize = false)
	private File selfInternal;
//	@Expose (serialize = false, deserialize = false)
//	private List<CSG> currentState = new ArrayList<CSG>();
	@Expose(serialize = false, deserialize = false)
	private
	double percentInitialized=0;
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
	private javafx.scene.image.WritableImage img;
	
	public void close() {
		for(ICaDoodleOpperation op:cache.keySet()) {
			cache.get(op).clear();
		}
		cache.clear();
		cache=null;
		clearListeners();
		listeners=null;
		toProcess.clear();
		toProcess=null;
		img=null;
		if(opperationRunner!=null)
			opperationRunner.interrupt();
		opperationRunner=null;
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
		if (selfInternal != null) {
			File db = new File(selfInternal.getAbsoluteFile().getParent() + delim() + "CSGdatabase.json");
			CSGDatabase.setDbFile(db);
		}
		int indexStarting = getCurrentIndex();
		setCurrentIndex(0);
		setPercentInitialized(0);
		for (int i = 0; i < opperations.size(); i++) {
			ICaDoodleOpperation op = opperations.get(i);
			setPercentInitialized(((double)i)/(double)opperations.size());
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
		loadImageFromFile();
		setPercentInitialized(1);
		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onInitializationDone();
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
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
			fireSaveSuggestion();
			opperationRunner = null;
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

			ICaDoodleOpperation op = getCurrentOpperation();
			storeResultInCache(op, op.process(getPreviouState()));
			setCurrentState(op);
			fireSaveSuggestion();
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
			fireSaveSuggestion();
			opperationRunner = null;
		});
		opperationRunner.start();
		return opperationRunner;
	}

	private void pruneForward() {
		for (int i = getCurrentIndex(); i < getOpperations().size(); i++) {
			List<CSG> back = cache.remove(getOpperations().get(i));
			if (back != null)
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
		fireSaveSuggestion();
	}

	public boolean isBackAvailible() {
		return getCurrentIndex() > 1;
	}

	private void updateCurrentFromCache() {
		ICaDoodleOpperation key = getCurrentOpperation();
		System.out.println("Current opperation results: " + key.getType());
		setCurrentState(key);
	}

	public ICaDoodleOpperation getCurrentOpperation() {
		if(getCurrentIndex()==0)
			return getOpperations().get(0);
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
		return cache.get(getCurrentOpperation());
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

	private void fireSaveSuggestion() {

		for (ICaDoodleStateUpdate l : listeners) {
			try {
				l.onSaveSuggestion();
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

		synchronized (selfInternal) {
			String contents = toJson();
			FileUtils.write(selfInternal, contents, StandardCharsets.UTF_8, false);
			File parent = selfInternal.getAbsoluteFile().getParentFile();
			File image = new File(parent.getAbsolutePath() + delim() + "snapshot.png");
			setImage(null);
			loadingImageFromUIThread();
			BufferedImage bufferedImage = SwingFXUtils.fromFXImage(getImage(), null);
			try {
				ImageIO.write(bufferedImage, "png", image);
				// System.out.println("Thumbnail saved successfully to " +
				// image.getAbsolutePath());
			} catch (IOException e) {
				// System.err.println("Error saving image: " + e.getMessage());
				e.printStackTrace();
			}
		}

		return getSelf();
	}

	public WritableImage loadImageFromFile() {
		try {
			File parent = selfInternal.getAbsoluteFile().getParentFile();
			File image = new File(parent.getAbsolutePath() + delim() + "snapshot.png");
			if(image.exists()) {
				BufferedImage bufferedImage = ImageIO.read(image);
				if (bufferedImage != null) {
					img = SwingFXUtils.toFXImage(bufferedImage, null);
				}
			}else {
				loadingImageFromUIThread();
			}
		} catch (IOException e) {
			System.err.println("Error loading image: " + e.getMessage());
			e.printStackTrace();
		}
		return img;
	}

	private void loadingImageFromUIThread() {
		BowlerKernel.runLater(() -> setImage(ThumbnailImage.get(getCurrentState())));
		while (getImage() == null)
			try {
				Thread.sleep(16);
				// System.out.println("Waiting for image to write");
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				break;
			}
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
		if (initialize) {
			file.initialize();
		}
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
		fireSaveSuggestion();
	}

	public int getCurrentIndex() {
		return currentIndex;
	}

	public void setCurrentIndex(int currentIndex) {
		// new Exception("Current Index set to " + currentIndex).printStackTrace();
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

	public long getTimeCreated() throws IOException {
		if(timeCreated<0) {
			timeCreated=System.currentTimeMillis();
			save();
		}
		return timeCreated;
	}



}
