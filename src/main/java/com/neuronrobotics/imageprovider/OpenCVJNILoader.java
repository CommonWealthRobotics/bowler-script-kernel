package com.neuronrobotics.imageprovider;

import java.io.File;

import javax.management.RuntimeErrorException;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class OpenCVJNILoader {

  static NativeResource resource = null;

  public static void load() {
    if (resource != null) {
      return;
    }
    resource = new NativeResource();
    //+Core.NATIVE_LIBRARY_NAME+".so"
    //+Core.NATIVE_LIBRARY_NAME+".so"
    if (NativeResource.isLinux()) {
      String[] possibleLocals = new String[]{
          "/usr/local/share/OpenCV/java/lib/",
          "/usr/lib/jni/lib/",
          "/usr/lib/jni/"
      };
      String erBack = "";
      for (String local : possibleLocals) {
        File libDirectory = new File(local);
        if (libDirectory.isDirectory()) {
          File[] possibleLibs = libDirectory.listFiles();
          for (File f : possibleLibs) {
            //System.out.println("Checking file: "+f);
            if (!f.isDirectory() &&
                f.getName().contains("opencv_java24") &&
                f.getName().endsWith(".so")) {
              try {
                System.load(f.getAbsolutePath());
                Mat m = Mat.eye(3, 3, CvType.CV_8UC1);
                System.out.println("Loading opencv lib " + f.getAbsolutePath());
                return;
              } catch (Error e) {
                //try the next one
                erBack += " " + e.getMessage();
                e.printStackTrace();
              }
            }
          }

        } else {
          erBack += "No file " + local;
        }
      }

      throw new RuntimeException(erBack);
    } else if (NativeResource.isWindows()) {
      String basedir = System.getenv("OPENCV_DIR");
      if (basedir == null) {
        throw new RuntimeException(
            "OPENCV_DIR was not found, environment variable OPENCV_DIR needs to be set");
      }
      System.err.println("OPENCV_DIR found at " + basedir);
      if ((!System.getProperty("sun.arch.data.model").contains("32") && basedir.contains("x64"))) {

        basedir.replace("x64", "x86");
        System.err.println("OPENCV_DIR environment variable is not set correctly");
      }
      basedir += "\\..\\..\\java\\";
      //if(basedir.contains("x64")){
      System.load(basedir + "x64\\" + Core.NATIVE_LIBRARY_NAME + ".dll");
      //}else{
      //	System.load(basedir+"x86\\"+Core.NATIVE_LIBRARY_NAME+".dll");
      //}
    } else if (NativeResource.isOSX()) {
      String basedir = System.getenv("OPENCV_DIR");
      if (basedir == null) {
        throw new RuntimeException(
            "OPENCV_DIR was not found, environment variable OPENCV_DIR needs to be set");
      }
      //basedir="/Users/hephaestus/Desktop/opencv249build/";
      String lib = basedir.trim() + "/lib/lib" + Core.NATIVE_LIBRARY_NAME + ".dylib";
      System.err.println("OPENCV_DIR found at " + lib);
      System.load(lib);

    }

    //Mat m  = Mat.eye(3, 3, CvType.CV_8UC1);
    //System.out.println("m = " + m.dump());

  }

}
