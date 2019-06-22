package com.neuronrobotics.bowlerstudio.assets;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

//import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXMLLoader;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;

public class AssetFactory {

  public static final String repo = "BowlerStudioImageAssets";
  private static String gitSource = "https://github.com/madhephaestus/" + repo + ".git";
  private static HashMap<String, Image> cache = new HashMap<>();
  private static HashMap<String, FXMLLoader> loaders = new HashMap<>();
  private static String assetRepoBranch = "";

  private AssetFactory() {
  }

  public static FXMLLoader loadLayout(String file, boolean refresh) throws Exception {
    File fxmlFIle = loadFile(file);
    URL fileURL = fxmlFIle.toURI().toURL();

    if (loaders.get(file) == null || refresh) {
      loaders.put(file, new FXMLLoader(fileURL));
    }

    loaders.get(file).setLocation(fileURL);
    return loaders.get(file);
  }

  public static FXMLLoader loadLayout(String file) throws Exception {
    return loadLayout(file, false);
  }

  public static File loadFile(String file) throws Exception {
    return ScriptingEngine
        .fileFromGit(
            getGitSource(),// git repo, change this if you fork this demo
            getAssetRepoBranch(),
            file// File from within the Git repo
        );
  }
  
  public static void writeImage(Image img, File file) {
	  int width = (int) img.getWidth();
	  int height = (int) img.getHeight();
	  PixelReader reader = img.getPixelReader();
	  byte[] buffer = new byte[width * height * 4];
	  javafx.scene.image.WritablePixelFormat<ByteBuffer> format = javafx.scene.image.PixelFormat.getByteBgraInstance();
	  reader.getPixels(0, 0, width, height, format, buffer, 0, width * 4);
	  try {
	      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(file));
	      for(int count = 0; count < buffer.length; count += 4) {
	          out.write(buffer[count + 2]);
	          out.write(buffer[count + 1]);
	          out.write(buffer[count]);
	          out.write(buffer[count + 3]);
	      }
	      out.flush();
	      out.close();
	  } catch(IOException e) {
	      e.printStackTrace();
	  }
  }

  @SuppressWarnings("restriction")
  public static Image loadAsset(String file) throws Exception {
    if (cache.get(file) == null) {
      File f = loadFile(file);
      if (f.getName().endsWith(".fxml")) {
        loadLayout(file);
        return null;
      } else if ((f == null || !f.exists()) && f.getName().endsWith(".png")) {
        WritableImage obj_img = new WritableImage(30, 30);
        byte alpha = (byte) 0;
        for (int cx = 0; cx < obj_img.getWidth(); cx++) {
          for (int cy = 0; cy < obj_img.getHeight(); cy++) {
            int color = obj_img.getPixelReader().getArgb(cx, cy);
            int mc = (alpha << 24) | 0x00ffffff;
            int newColor = color & mc;
            obj_img.getPixelWriter().setArgb(cx, cy, newColor);
          }
        }

        cache.put(file, obj_img);
        System.out.println("No image at " + file);

        try {
          File imageFile = ScriptingEngine.createFile(getGitSource(), file, "create file");
          try {
            String fileName = imageFile.getName();
            writeImage(obj_img, imageFile);

          } catch (Exception ignored) {
          }
          ScriptingEngine.createFile(getGitSource(), file, "saving new content");
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else {
        cache.put(file, new Image(f.toURI().toString()));
      }
    }
    return cache.get(file);
  }

  public static ImageView loadIcon(String file) {
    try {
      return new ImageView(loadAsset(file));
    } catch (Exception e) {
      return new ImageView();
    }
  }

  public static String getGitSource() throws Exception {
    return gitSource;
  }

  public static void setGitSource(String gitSource, String assetRepoBranch) throws Exception {
    System.err.println("Assets from: " + gitSource + "#" + assetRepoBranch);
    //new Exception().printStackTrace();
    setAssetRepoBranch(assetRepoBranch);
    AssetFactory.gitSource = gitSource;
    cache.clear();
    loadAllAssets();
  }

  public static void loadAllAssets() throws Exception {
    List<String> files = ScriptingEngine.filesInGit(gitSource, StudioBuildInfo.getVersion(), null);
    for (String file : files) {
      loadAsset(file);
    }
  }

  public static String getAssetRepoBranch() {
    return assetRepoBranch;
  }

  public static void setAssetRepoBranch(String assetRepoBranch) {
    AssetFactory.assetRepoBranch = assetRepoBranch;
  }

  public static void deleteFolder(File folder) {
    File[] files = folder.listFiles();
    if (files != null) { //some JVMs return null for empty dirs
      for (File f : files) {
        if (f.isDirectory()) {
          deleteFolder(f);
        } else {
          f.delete();
        }
      }
    }
    folder.delete();
  }
}
