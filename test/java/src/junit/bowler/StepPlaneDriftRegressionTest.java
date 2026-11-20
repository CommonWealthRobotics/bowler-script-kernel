package junit.bowler;

import static org.junit.Assert.assertEquals;
import java.util.List;
import org.junit.Test;
import com.neuronrobotics.bowlerstudio.threed.StepBRepWrite;
import com.neuronrobotics.bowlerstudio.threed.StepFace;
import eu.mihosoft.vrl.v3d.CSG;

/** Exact preprocessed nametag triangles 96/117, with the source model's extent. */
public class StepPlaneDriftRegressionTest {
    @Test
    public void nonplanarNeighboursRetainTheirOwnPlanes() {
        double[] points={
            -70.99999995,-108.24999995,2.999998999999998,
            -65.0,-80.20770331072389,2.999998999999998,
            -74.38268338236509,-108.24999995,2.92287852883697,
            -64.91155046826222,-79.7769668991996,2.999998999999998,
            // Unused extent vertices retain the exact full-model tolerance context.
            -75,-124,-1e-6,-35,-36.500001,23.20629118318275
        };
        CSG c=new CSG(points,new long[]{0,1,2,2,1,3},javafx.scene.paint.Color.GRAY);
        List<StepFace> faces=StepBRepWrite.faces(c);
        assertEquals("these source triangles are nonplanar; their weighted fit moves the boundary 5.55e-5 mm",2,faces.size());
    }
}
