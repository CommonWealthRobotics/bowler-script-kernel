package com.neuronrobotics.bowlerstudio.creature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.apache.commons.io.FilenameUtils;

import com.neuronrobotics.bowlerstudio.scripting.BlenderLoader;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.FileUtil;
import eu.mihosoft.vrl.v3d.JavaFXInitializer;
import eu.mihosoft.vrl.v3d.Transform;
import eu.mihosoft.vrl.v3d.svg.SVGExporter;
import javafx.scene.transform.Affine;

public class CadFileExporter {
  
  IMobileBaseUI  ui;
  public CadFileExporter(IMobileBaseUI myUI){
    ui=myUI;
  }
  public CadFileExporter(){
    ui=new IMobileBaseUI() {
      
      @Override
      public void setSelectedCsg(Collection<CSG> selectedCsg) {
        // TODO Auto-generated method stub
        
      }
      
      @Override
      public void setAllCSG(Collection<CSG> toAdd, File source) {
        // TODO Auto-generated method stub
        
      }
      
      @Override
      public void highlightException(File fileEngineRunByName, Throwable ex) {
        // TODO Auto-generated method stub
        
      }
      
      @Override
      public Set<CSG> getVisibleCSGs() {
        // TODO Auto-generated method stub
        return null;
      }
      
      @Override
      public void addCSG(Collection<CSG> toAdd, File source) {
        // TODO Auto-generated method stub
        
      }

	@Override
	public void setSelected(Affine rootListener) {
		// TODO Auto-generated method stub
		
	}
    };
  }
	public ArrayList<File> generateManufacturingParts(List<CSG> totalAssembly , File baseDirForFiles) throws IOException {
		ArrayList<File> allCadStl = new ArrayList<>();
		if(!baseDirForFiles.isDirectory()){
			String fileNameWithOutExt = FilenameUtils.removeExtension(baseDirForFiles.getAbsolutePath());
			baseDirForFiles = new File(fileNameWithOutExt);
			if (!baseDirForFiles.exists())
				baseDirForFiles.mkdirs();
		}
		File dir;
		if(!baseDirForFiles.getName().contentEquals("manufacturing")){
			 dir = new File(baseDirForFiles.getAbsolutePath() + "/manufacturing/");
			if (!dir.exists())
				dir.mkdirs();
		}else{
			dir=baseDirForFiles;
		}
		int index=0;
		ArrayList<CSG> svgParts = new ArrayList<>();
		String svgName =null;
		String nameBase ="";
		for(CSG part: totalAssembly){
			String name = part.getName();
			CSG manufactured = part.prepForManufacturing();
			if( manufactured==null){
			  continue;
			}
			manufactured.setName(part.getName());
			if(name.length()==0)
				name="Part-Num-"+index;
			nameBase = dir.getAbsolutePath()+"/"+name;
			index++;
			if(part.getExportFormats()==null){
				try {
					allCadStl.add(makeStl(nameBase,manufactured));// default to stl
				}catch(Throwable t) {
					System.err.println("Failed to generate "+part.getName());
					t.printStackTrace();
				}
			}else{

				for(String format:part.getExportFormats()){
					if(format.toLowerCase().contains("obj")){
						allCadStl.add(makeObj(nameBase,manufactured));//
						ui.setCsg(manufactured , null);
					}
					if(format.toLowerCase().contains("blend")){
						allCadStl.add(makeBlender(nameBase,manufactured));// 
						ui.setCsg(manufactured , null);
					}
					if(format.toLowerCase().contains("stl")){
						allCadStl.add(makeStl(nameBase,manufactured));// default to stl
						ui.setCsg(manufactured , null);
					}
					if(format.toLowerCase().contains("svg")){
						if(svgName==null){
							svgName =part.toString();
						}
						svgParts.add(manufactured);
						ui.setAllCSG(svgParts , null);
					}
					
				}

			}
		}
		if(svgParts.size()>0){
			allCadStl.add(makeSvg(nameBase,svgParts));// default to stl
		}
		
		return allCadStl;
	}
	private File makeStl(String nameBase,CSG tmp ) throws IOException{
		File stl = new File(nameBase + ".stl");
		
		FileUtil.write(Paths.get(stl.getAbsolutePath()), tmp.toStlString());
		System.out.println("Writing "+stl.getAbsolutePath());
		return stl;
	}
	private File makeObj(String nameBase,CSG tmp ) throws IOException{
		File stl = new File(nameBase + ".obj");
		
		FileUtil.write(Paths.get(stl.getAbsolutePath()), tmp.toObjString());
		System.out.println("Writing "+stl.getAbsolutePath());
		return stl;
	}
	
	private File makeBlender(String nameBase,CSG tmp ) throws IOException{
		File blend = new File(nameBase + ".blend");
		System.out.println("Writing "+blend.getAbsolutePath());
		BlenderLoader.toBlenderFile(tmp, blend);
		return blend;
	}
	
	private File makeSvg(String nameBase, List<CSG> currentCsg) throws IOException {
		File stl = new File(nameBase + ".svg");

		
			for (CSG csg : currentCsg) {
				if (csg.getSlicePlanes() == null) {
					csg.addSlicePlane(new Transform());
				}
			}
			try {
				try {
					SVGExporter.export(currentCsg, stl);
				} catch (Exception e) {
					ArrayList<CSG> movedDown = new ArrayList<>();
					for (CSG csg : currentCsg) {
						CSG movez = csg.toZMin().movez(-0.01);
						if (movez.getSlicePlanes() == null)
							movez.addSlicePlane(new Transform());
						movez.setName(csg.getName());
						movedDown.add(movez);
					}
					SVGExporter.export(movedDown, stl);

				}

				System.out.println("Writing " + stl.getAbsolutePath());
			} catch (Throwable t) {
				System.err.println("ERROR, NO pixelization engine availible for slicing");
				t.printStackTrace();
			}
		
		return stl;
	}
	
}
