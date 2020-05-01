package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.transform.Affine;

public interface IMobileBaseUI {

  /**
   * Replace all objects in the UI with these CSGs.
   *
   * @param toAdd CSGs to add
   * @param source script source file
   */
  void setAllCSG(Collection<CSG> toAdd, File source);

  /**
   * Add these objects to the UI.
   *
   * @param toAdd CSGs to add
   * @param source script source file
   */
  void addCSG(Collection<CSG> toAdd, File source);

  /**
   * Highlight the exception-causing lines in a file.
   *
   * @param fileEngineRunByName The file that was running when the exception occurred
   * @param ex the stack trace for file names of open files, or for open or executed file names
   */
  void highlightException(File fileEngineRunByName, Exception ex);

  /**
   * Return the CSGs currently visible in the UI.
   *
   * @return visible CSGs
   */
  Set<CSG> getVisibleCSGs();

  /**
   * Highlight the given list of CSGs.
   * This should not change the CSG, just highlight it.
   * 
   * @param selectedCsg the list to highlight
   * NULL is used as a clear highlights
   */
  void setSelectedCsg(Collection<CSG> selectedCsg);
  
  void setSelected(Affine rootListener);
  
  default void selectCsgByFile(File script, int lineNumber){
    List<CSG> objsFromScriptLine = new ArrayList<>();

    // check all visible CSGs
    for (CSG checker : getVisibleCSGs()) {
        for (String trace : checker.getCreationEventStackTraceList()) {
            String[] traceParts = trace.split(":");
            if (traceParts[0].trim().toLowerCase()
                .contains(script.getName().toLowerCase().trim())) {
                  int num = Integer.parseInt(traceParts[1].trim());

                  if (num == lineNumber) {
                      objsFromScriptLine.add(checker);
                  }
            }
        }
    }

    if (objsFromScriptLine.size() > 0) {
        setSelectedCsg(objsFromScriptLine);
    }
  }
  
  default void setCsg(CSG toAdd, File source){
    setAllCSG(Collections.singletonList(toAdd),  source);
  }

  default void setCsg(List<CSG> toAdd){
    setAllCSG(toAdd,  null);
  }
  
  default void setCsg(CSG toAdd){
    setAllCSG(Collections.singletonList(toAdd),  null);
  }

  default  void setCsg(MobileBaseCadManager thread, File cadScript){
    setAllCSG(thread.getAllCad(), cadScript);
  }

  default void addCsg(CSG toAdd, File source){
    addCSG(Collections.singletonList(toAdd),  source);
  }


  
  

}
