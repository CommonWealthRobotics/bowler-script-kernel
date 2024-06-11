package com.neuronrobotics.sdk.addons.gamepad;


import com.neuronrobotics.bowlerstudio.BowlerKernel;

/**
 * Sample Skeleton for "jogTrainerWidget.fxml" Controller Class
 * You can copy and paste this code into your favorite IDE
 **/

import com.neuronrobotics.bowlerstudio.assets.AssetFactory;
import com.neuronrobotics.bowlerstudio.assets.ConfigurationDatabase;
import com.neuronrobotics.bowlerstudio.assets.FontSizeManager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ResourceBundle;
import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class JogTrainerWidget extends Application implements IGameControlEvent {

	@FXML // ResourceBundle that was given to the FXMLLoader
	private ResourceBundle resources;
	
    @FXML // fx:id="controllername"
    private Label controllername; // Value injected by FXMLLoader

	@FXML // URL location of the FXML file that was given to the FXMLLoader
	private URL location;

	@FXML // fx:id="grid"
	private GridPane grid; // Value injected by FXMLLoader
	private int mappingIndex = 0;
	private HashMap<Integer, TextField> fields = new HashMap<>();
	private String axisWaiting=null;
	private ArrayList<String> listOfMappedAxis =new ArrayList<>();
	private Button save;
	private Stage primaryStage;
	private HashMap<String,Float> values=new HashMap<>();
	private HashMap<String,Long> timeOfLastAxisSet = new HashMap<>();
	private BowlerJInputDevice gameController;

	@FXML // This method is called by the FXMLLoader when initialization is complete
	void initialize() {
		assert grid != null : "fx:id=\"grid\" was not injected: check your FXML file 'jogTrainerWidget.fxml'.";
		assert gameController != null : "Game controller missing!";
		//assert primaryStage != null : "Stage missing!";
		assert controllername!= null: "fx:id=\"grid\" was not injected: check your FXML file 'jogTrainerWidget.fxml'.";
		
		controllername.setText(gameController.getName());
		save = new Button("Save Mapping");
		Button reset = new Button("Reset");
		reset.setOnAction(event -> {
			PersistantControllerMap.clearMapping(gameController.getName());
			for(Integer key:fields.keySet()) {
				fields.get(key).setText("");
			}
			reset();
		});
		save.setOnAction(new EventHandler<ActionEvent>() {
 
            @Override
            public void handle(ActionEvent event) {	
            	new Thread(()->{
            		List<String> maps = PersistantControllerMap.getDefaultMaps();
            		for (int i = 0; i < maps.size(); i++) 
            			ConfigurationDatabase.setObject(gameController.getName(), fields.get(i).getText(), maps.get(i));
            		ConfigurationDatabase.save();
            	}).start();
            	if(primaryStage!=null)
            		primaryStage.hide();
            }
        });

		List<String> maps = PersistantControllerMap.getDefaultMaps();
		int i = 0;
		System.out.println("There are "+maps.size()+" rows");
		for (i = 0; i < maps.size(); i++) {
			String map = maps.get(i);

			Label name = new Label(map);
			Label setto = new Label("set to");
			TextField toBeMapped = new TextField();
			if(PersistantControllerMap.isMapedAxis(gameController.getName(), map)) {
				toBeMapped.setText(PersistantControllerMap.getHardwareAxisFromMappedValue(gameController.getName(), map));
			}
			if(i!=0)
				toBeMapped.setDisable(true);
			grid.add(name, 0, i);
			grid.add(setto, 1, i);
			grid.add(toBeMapped, 2, i);
			fields.put(i, toBeMapped);
		}
		grid.add(save, 2, i);
		grid.add(reset, 1, i);
		reset();
		if(PersistantControllerMap.areAllAxisMapped(gameController.getName())) {
			BowlerKernel.runLater(() ->fields.get(0).setDisable(true));
			gameController.removeListeners(this);
		}else {
			gameController.addListeners(this);
		}
	}


	public JogTrainerWidget(BowlerJInputDevice gameController) {
		this.gameController = gameController;
		
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.primaryStage = primaryStage;
		File fxmlFIle = AssetFactory.loadFile("layout/jogTrainerWidget.fxml");
	    URL fileURL = fxmlFIle.toURI().toURL();
		FXMLLoader loader = new FXMLLoader(fileURL);
		loader.setLocation(fileURL);
		Parent root;
		loader.setController(this);
		// This is needed when loading on MAC
		loader.setClassLoader(getClass().getClassLoader());
		root = loader.load();
		FontSizeManager.addListener(fontNum->{
			int tmp = fontNum-10;
			if(tmp<12)
				tmp=12;
			root.setStyle("-fx-font-size: "+tmp+"pt");
		});
		
		BowlerKernel.runLater(() -> {
			primaryStage.setTitle("Configure the controller");

			Scene scene = new Scene(root);
			primaryStage.setScene(scene);
			primaryStage.initModality(Modality.WINDOW_MODAL);
			primaryStage.setResizable(true);
			primaryStage.show();

		});
		
	}

	public void reset() {
		listOfMappedAxis.clear();
		mappingIndex=0;
		BowlerKernel.runLater(() ->fields.get(mappingIndex).setDisable(false));
		gameController.addListeners(this);
		BowlerKernel.runLater(() ->controllername.setText(gameController.getName()));	
		save.setDisable(true);
	}



	@Override
	public void onEvent(String name, float value) {
		if(Math.abs(value)<0.1)
			value=0;

		if(values.get(name)==null) {
			values.put(name, value);
			timeOfLastAxisSet.put(name, System.currentTimeMillis());
			return;
		}
		if(System.currentTimeMillis()-timeOfLastAxisSet.get(name)<100) {
			return;// wait for a value to settle
		}
		Float float1 = values.get(name);
		float abs = Math.abs(float1-value);
		if(	abs <0.5) {
			values.put(name,value);
			timeOfLastAxisSet.put(name, System.currentTimeMillis());
			if(abs>0.2)
				System.out.println("value for "+name+" seems noisy "+value+" most recent was "+values.get(name));
			return;
		}else {
			System.out.println("Value changed! "+name+" "+float1+" to "+value);
			values.put(name, value);
			timeOfLastAxisSet.put(name, System.currentTimeMillis());
		}
			
		
		for(String s:listOfMappedAxis) {
			if(s.contentEquals(name)) {
				System.out.println("mapping skipped for "+name);
				System.out.println(gameController);
				return;// This axis name is already mapped and will not be mapped again
			}
		}
		for(String s:PersistantControllerMap.getDefaultMaps()) {
			if(name.contentEquals(s))
				return;// Do not use maped axis for re-mapping
		}

		axisWaiting=name;
		System.out.println("Adding Axis "+name);
		
		listOfMappedAxis.add(name);
		timeOfLastAxisSet.put(name,System.currentTimeMillis());
		BowlerKernel.runLater(() -> {
			TextField textField = fields.get(mappingIndex);
			textField.setText(name);
			
			textField.setDisable(true);
			mappingIndex++;
			if(mappingIndex==PersistantControllerMap.getDefaultMaps().size()) {
				save.setDisable(false);
				gameController.removeListeners(this);
			}else
				fields.get(mappingIndex).setDisable(false);
		});

	}

	public static void run(BowlerJInputDevice c) {
		//System.out.println("Launching Controller mapping");
		BowlerKernel.runLater(new Runnable() {
			@Override
			public void run() {
				//System.out.println("Creating stage");
				Stage s = new Stage();
				new Thread() {
					public void run() {
						JogTrainerWidget controller = new JogTrainerWidget(c);
						try {
							//System.out.println("Loading FXML");
							controller.start(s);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}.start();
			}
		});

	}

}
