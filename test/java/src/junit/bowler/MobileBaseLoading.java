package junit.bowler;

import static org.junit.Assert.*;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;
import com.neuronrobotics.bowlerstudio.creature.IMobileBaseUI;
import com.neuronrobotics.bowlerstudio.creature.MobileBaseCadManager;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.addons.kinematics.MobileBase;
import com.neuronrobotics.sdk.common.DeviceManager;
import com.neuronrobotics.sdk.util.ThreadUtil;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.ICSGProgress;

public class MobileBaseLoading {
  int numCSG =0;

  @Test
  @Ignore
  public void test() throws Exception {
    /*
    ScriptingEngine.setupAnyonmous();
    numCSG =0;
    IMobileBaseUI mobileBaseUI = new IMobileBaseUI() {
      @Override
      public void setAllCSG(Collection<CSG> collection, File file) {
        com.neuronrobotics.sdk.common.Log.error("Setting CSG's # " +collection.size());
        numCSG=collection.size();
      }

      @Override
      public void addCSG(Collection<CSG> collection, File file) {
        com.neuronrobotics.sdk.common.Log.error("Adding CSG's # " +collection.size());

      }

      @Override
      public void highlightException(File file, Exception e) {
        e.printStackTrace();
        fail();
      }

      @Override
      public Set<CSG> getVisibleCSGs() {
        return null;
      }

      @Override
      public void setSelectedCsg(Collection<CSG> collection) {
      }
    };

    CSG.setProgressMoniter(new ICSGProgress() {
      
      @Override
      public void progressUpdate(int currentIndex, int finalIndex, String type, CSG intermediateShape) {
        // Auto-generated method stub
        
      }
    });
    String[] file = {"https://github.com/madhephaestus/SeriesElasticActuator.git", "seaArm.xml"};
    String xmlContent = ScriptingEngine.codeFromGit(file[0], file[1])[0];
    MobileBase mobileBase = new MobileBase(IOUtils.toInputStream(xmlContent, "UTF-8"));
    mobileBase.setGitSelfSource(file);
    mobileBase.connect();
    MobileBaseCadManager mobileBaseCadManager = new MobileBaseCadManager(mobileBase, mobileBaseUI);
   // MobileBaseCadManager.get(mobileBase).getUi().
    DeviceManager.addConnection(mobileBase, mobileBase.getScriptingName());
    mobileBaseCadManager.generateCad();
    com.neuronrobotics.sdk.common.Log.error("Waiting for cad to generate");
    ThreadUtil.wait(1000);
    while (MobileBaseCadManager.get(mobileBase).getProcesIndictor().get() < 1 ) {
      //com.neuronrobotics.sdk.common.Log.error("Waiting: " + MobileBaseCadManager.get(mobileBase).getProcesIndictor().get());
      ThreadUtil.wait(1000);
    }
    if(numCSG==0)
      fail();
*/
  }

}
