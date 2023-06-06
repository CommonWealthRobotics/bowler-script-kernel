package com.neuronrobotics.bowlerkernel.djl;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neuronrobotics.bowlerstudio.opencv.OpenCVManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.common.DeviceManager;
import com.neuronrobotics.sdk.common.NonBowlerDevice;

import ai.djl.MalformedModelException;
import ai.djl.inference.Predictor;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.ImageFactory;
import ai.djl.pytorch.jni.JniUtils;
import ai.djl.repository.zoo.ModelNotFoundException;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;

public class UniquePersonFactory extends NonBowlerDevice {
	private File database;
	Type TT_mapStringString = new TypeToken<HashSet<UniquePerson>>() {
	}.getType();
	Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
	private HashSet<UniquePerson> longTermMemory = new HashSet<>();;
	private ArrayList<UniquePerson> shortTermMemory = new ArrayList<>();;
	Predictor<Image, float[]> features;

	private static double confidence = 0.90;
	private static long timeout = 30000;
	private static long countPeople = 1;
	private static int numberOfTrainingHashes = 20;
	private Thread processor;
	private boolean run = true;
	private HashMap<BufferedImage, Point> factoryFromImageTMp = null;
	private HashMap<UniquePerson, Point> currentPersons = null;
	private ImageFactory factory;
	private VBox workingMemory = new VBox();
	private boolean processFlag = false;

	private class UniquePersonUI {
		HBox box = new HBox();
		Label percent = new Label();
	}

	private HashMap<UniquePerson, UniquePersonUI> uiElelments = new HashMap<UniquePerson, UniquePersonFactory.UniquePersonUI>();

	private UniquePersonUI getUI(UniquePerson p) {
		if (uiElelments.get(p) == null) {
			uiElelments.put(p, new UniquePersonUI());
		}
		return uiElelments.get(p);
	}

	public static UniquePersonFactory get() {
		return get(new File(ScriptingEngine.getWorkspace().getAbsolutePath() + "/face_memory.json"));

	}

	public static UniquePersonFactory get(File index) {
		return (UniquePersonFactory) DeviceManager.getSpecificDevice("UniquePersonFactory_" + index.getName(),
				() -> new UniquePersonFactory(index));

	}

	private UniquePersonFactory(File database) {
		this.setDatabase(database);

		try {
			features = PredictorFactory.faceFeatureFactory();
		} catch (ModelNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedModelException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		factory = ImageFactory.getInstance();

	}

	public String save() {
		String jsonString = gson.toJson(longTermMemory, TT_mapStringString);
		Path path = Paths.get(getDatabase().getAbsolutePath());
		byte[] strToBytes = jsonString.getBytes();

		try {
			Files.write(path, strToBytes);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return jsonString;
	}

	/**
	 * @return the confidence
	 */
	public static double getConfidence() {
		return confidence;
	}

	/**
	 * @param confidence the confidence to set
	 */
	public static void setConfidence(double confidence) {
		UniquePersonFactory.confidence = confidence;
	}

	/**
	 * @return the timeout
	 */
	public static long getTimeout() {
		return timeout;
	}

	/**
	 * @param timeout the timeout to set
	 */
	public static void setTimeout(long timeout) {
		UniquePersonFactory.timeout = timeout;
	}

	/**
	 * @return the numberOfTrainingHashes
	 */
	public static int getNumberOfTrainingHashes() {
		return numberOfTrainingHashes;
	}

	/**
	 * @param numberOfTrainingHashes the numberOfTrainingHashes to set
	 */
	public static void setNumberOfTrainingHashes(int numberOfTrainingHashes) {
		UniquePersonFactory.numberOfTrainingHashes = numberOfTrainingHashes;
	}

	@Override
	public void disconnectDeviceImp() {
		run = false;
		if (processor != null)
			processor.interrupt();
	}

	@Override
	public boolean connectDeviceImp() {
		disconnectDeviceImp();
		run = true;
		processor = new Thread(() -> {
			processBlocking();
		});
		processor.start();
		return run;
	}

	public void addFace(Mat matrix, Rect crop, ai.djl.modality.cv.output.Point nose) {
		if (factoryFromImageTMp == null) {
			factoryFromImageTMp = new HashMap<BufferedImage, Point>();
		}
		HashMap<BufferedImage, Point> local = factoryFromImageTMp;
		try {
			Mat tmpImg = new Mat(matrix, crop);
			Mat image_roi = new Mat();
			// Converting the image to grey scale
			Imgproc.cvtColor(tmpImg, image_roi, Imgproc.COLOR_RGB2GRAY);
			BufferedImage image = new BufferedImage(crop.width,
					crop.height, BufferedImage.TYPE_BYTE_GRAY);
			WritableRaster raster = image.getRaster();
			DataBufferByte dataBuffer = (DataBufferByte) raster.getDataBuffer();
			byte[] data = dataBuffer.getData();
			image_roi.get(0, 0, data);
			local.put(image, new Point(nose.getX(),nose.getY()));
		} catch (Throwable tr) {
			tr.printStackTrace();
		}
	}

	private void processBlocking() {
		JniUtils.setGraphExecutorOptimize(false);
		while (run) {
			if (!isProcessFlag()) {
				try {
					Thread.sleep(16);
				} catch (InterruptedException e) {
					break;
				}
				continue;
			}
			processFlag = false;
			try {
				HashMap<BufferedImage, Point> local = new HashMap<>();
				local.putAll(factoryFromImageTMp);
				factoryFromImageTMp = null;
				for (UniquePerson up : shortTermMemory) {
					if (up.timesSeen > numberOfTrainingHashes) {
						if (!longTermMemory.contains(up)) {
							longTermMemory.add(up);
							System.out.println("Saving new Face to dataabase " + up.name);
							save();
						}
					}
				}
				
				HashMap<UniquePerson, org.opencv.core.Point> tmpPersons = new HashMap<>();
				for (BufferedImage imgBuff : local.keySet()) {
					ai.djl.modality.cv.Image cmp = factory.fromImage(imgBuff);
					Point point = local.get(imgBuff);
					// println "Processing new image "
					int minSize = 70;
					if (imgBuff.getHeight() < minSize || imgBuff.getWidth() < minSize)
						continue;
					float[] id;
					try {
						id = features.predict(cmp);
					} catch (Throwable ex) {
						System.out.println("Image failed h=" + imgBuff.getHeight() + " w=" + imgBuff.getWidth());
						ex.printStackTrace();
						continue;
					}
					boolean found = false;
					ArrayList<UniquePerson> duplicates = new ArrayList<UniquePerson>();

					for (UniquePerson pp : shortTermMemory) {
						UniquePerson p = pp;

						int count = 0;
						// for(int i=0;i<p.features.size();i++) {
						// float[] featureFloats =p.features.get(i);
						float result = PredictorFactory.calculSimilarFaceFeature(id, p.features);
						// println "Difference from "+p.name+" is "+result
						if (result > p.confidenceTarget) {
							if (found) {
								duplicates.add(p);
							} else {
								count++;

								p.timesSeen++;
								found = true;
								if (p.timesSeen > 2)
									tmpPersons.put(p, point);
								UniquePersonUI UI = getUI(p);

								if (p.timesSeen > 3 && !workingMemory.getChildren().contains(UI.box)) {
									// on the third seen, display
									WritableImage tmpImg = SwingFXUtils.toFXImage(imgBuff, null);
									UI.box.getChildren().addAll(new ImageView(tmpImg));
									UI.box.getChildren().addAll(new Label(p.name));
									UI.percent = new Label();
									UI.box.getChildren().addAll(UI.percent);
									Platform.runLater(() -> {
										workingMemory.getChildren().add(UI.box);
									});
								}
								p.time = System.currentTimeMillis();
								// if(result<(confidence+0.01))
								// if (p.features.size() < numberOfTrainingHashes) {
								p.features.add(id);
								int percent = (int) (((double) p.features.size()) / ((double) numberOfTrainingHashes)
										* 100);
								if (percent > 100)
									percent = 100;
								double perc = percent;
								// println "Trained "+percent;
								Platform.runLater(() -> {
									UI.percent.setText(" : Trained " + perc + "%");
								});
								p.confidenceTarget = confidence;
								if (p.features.size() == numberOfTrainingHashes) {
									// println " Trained "+p.name;
									Platform.runLater(() -> {
										UI.box.getChildren().addAll(new Label(" Done! "));
									});

								}
								// }
							}
						}
					}
					for (int i = 0; i < shortTermMemory.size(); i++) {
						UniquePerson p = shortTermMemory.get(i);
						if ((System.currentTimeMillis() - p.time) > timeout && p.timesSeen < numberOfTrainingHashes) {
							duplicates.add(p);
						}
					}
					for (UniquePerson p : duplicates) {
						shortTermMemory.remove(p);
						UniquePersonUI UI = getUI(p);
						uiElelments.remove(p);
						Platform.runLater(() -> {
							workingMemory.getChildren().remove(UI.box);
						});
						// println "Removing "+p.name
					}

					if (found == false) {
						resetHash();
						countPeople++;
						UniquePerson p = new UniquePerson();
						p.features.add(id);
						p.name = "Person " + (countPeople);
						String tmpDirsLocation = System.getProperty("java.io.tmpdir") + "/idFiles/" + p.name + ".jpeg";
						UniquePersonUI UI = getUI(p);
						p.referenceImageLocation = tmpDirsLocation;
						// println "New person found! "+tmpDirsLocation
						shortTermMemory.add(p);
					}
				}
				if (currentPersons != null)
					currentPersons.clear();
				currentPersons = tmpPersons;
			} catch (Throwable tr) {
				tr.printStackTrace(); // run=false;
			}

		}
	}

	@Override
	public ArrayList<String> getNamespacesImp() {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @return the currentPersons
	 */
	public HashMap<UniquePerson, Point> getCurrentPersons() {
		if (currentPersons == null)
			return null;
		HashMap<UniquePerson, Point> tmp = new HashMap<UniquePerson, Point>();

		tmp.putAll(currentPersons);

		return tmp;
	}

	/**
	 * @return the workingMemory
	 */
	public VBox getWorkingMemory() {
		return workingMemory;
	}

	/**
	 * @param workingMemory the workingMemory to set
	 */
	public void setWorkingMemory(VBox workingMemory) {
		this.workingMemory = workingMemory;
	}

	/**
	 * @return the database
	 */
	public File getDatabase() {
		return database;
	}

	private void resetHash() {
		countPeople = 0;
		for (UniquePerson u : longTermMemory) {
			if (u.UUID > countPeople)
				countPeople = u.UUID;

		}
		for (UniquePerson u : shortTermMemory) {
			if (u.UUID > countPeople)
				countPeople = u.UUID;

		}
	}

	/**
	 * @param database the database to set
	 */
	public void setDatabase(File database) {
		this.database = database;
		if (!database.exists())
			try {
				database.createNewFile();
				save();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		else {
			String jsonString;
			try {
				jsonString = new String(Files.readAllBytes(Paths.get(database.getAbsolutePath())));
				longTermMemory = gson.fromJson(jsonString, TT_mapStringString);
				for (UniquePerson u : longTermMemory) {
					shortTermMemory.add(u);
				}
				resetHash();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * @return the processFlag
	 */
	public boolean isProcessFlag() {
		return processFlag;
	}

	/**
	 * @param processFlag the processFlag to set
	 */
	public void setProcessFlag() {
		this.processFlag = true;
	}
}
