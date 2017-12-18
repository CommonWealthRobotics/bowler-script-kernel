package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import eu.mihosoft.vrl.v3d.CSG;

public interface IMobileBaseUI {

  /**
   * Replace all objects in the UI with these CSG's
   * @param toadd
   * @param source
   */
  void setCsg(List<CSG> toadd, File source);
  /**
   * Add these objects to the UI
   * @param toadd
   * @param source
   */
  void addCsg(List<CSG> toadd, File source);
  /**
   * Highlight the lines in a file
   * @param fileEngineRunByName THe file that was running when the exception occurred
   * @param ex the stack trace for file names of open files or for open or executed file names. 
   */
  void highlightException(File fileEngineRunByName, Exception ex);
  /**
   * Return the CSG's currently visible in this UI
   * @return
   */
  Set<CSG>  getVisableCSGs();
  /**
   * Highlight the given list of CSG's
   * THis should not change the CSG, just highlight it
   * 
   * @param selectedCsg the list to highlight
   * NULL is used as a clear highlights
   */
  void setSelectedCsg(List<CSG> selectedCsg);
  
  default void selectCsgByFile(File script, int lineNumber){
    ArrayList<CSG> objsFromScriptLine = new ArrayList<>();
    // check all visable CSGs
    for (CSG checker : getVisableCSGs()) {
        for (String trace : checker.getCreationEventStackTraceList()) {
            String[] traceParts = trace.split(":");
            // System.err.println("Seeking: "+script.getName()+" line=
            // "+lineNumber+" checking from line: "+trace);
            // System.err.println("TraceParts "+traceParts[0]+" and
            // "+traceParts[1]);
            if (traceParts[0].trim().toLowerCase().contains(script.getName().toLowerCase().trim())) {
                // System.out.println("Script matches");
                try {
                    int num = Integer.parseInt(traceParts[1].trim());

                    if (num == lineNumber) {
                        // System.out.println("MATCH");
                        objsFromScriptLine.add(checker);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    if (objsFromScriptLine.size() > 0) {
        setSelectedCsg(objsFromScriptLine);
    }
  }
  
  default void setCsg(CSG toadd, File source){
    setCsg(Arrays.asList(toadd),  source);
  }
  default void setCsg(List<CSG> toadd){
    setCsg(toadd,  null);
  }
  
  default void setCsg(CSG toadd){
    setCsg(Arrays.asList(toadd),  null);
  }
  default  void setCsg(MobileBaseCadManager thread, File cadScript){
    setCsg(thread.getAllCad(), cadScript);
  }
  default void addCsg(CSG toadd, File source){
    addCsg(Arrays.asList(toadd),  source);
  }
}
