package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Before;
import org.junit.Test;

import com.neuronrobotics.bowlerstudio.BowlerKernel;

import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Polygon;
import eu.mihosoft.vrl.v3d.svg.SVGLoad;

public class SVGLoadTest {
	  @Before
	  public void setup() throws InvalidRemoteException, TransportException, IOException, GitAPIException, Exception {
		  BowlerKernel.startupProcedures();
	  }
	@Test
	public void test() throws IOException {
		File svg = new File("Test.SVG");
		if(!svg.exists())
			throw new RuntimeException("Test file missing!"+svg.getAbsolutePath());
		SVGLoad s = new SVGLoad(svg.toURI());
		run(s);
		//fail("Not yet implemented");
	}
	private ArrayList<Object>  run(SVGLoad s) {
		
		ArrayList<Object> polys= new ArrayList<>();
		HashMap<String, List<Polygon>> polygons = s.toPolygons();
		for(String key:polygons.keySet()) {
			for(Polygon P:polygons.get(key)) {
				polys.add(P);
			}
		}
		
		List<String> layers = s.getLayers();
		double depth =5+(layers.size()*5);
		for(int i=0;i<layers.size();i++) {
			String layerName=layers.get(i);
			CSG extrudeLayerToCSG = s.extrudeLayerToCSG(depth,layerName);
			//extrudeLayerToCSG.setColor(Color.web(SVGExporter.colorNames.get(i)));
			polys.add(extrudeLayerToCSG);
			depth-=5;
		}
		
		return polys;
	}

}
