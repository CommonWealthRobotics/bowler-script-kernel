package junit.bowler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import com.neuronrobotics.bowlerstudio.threed.StepBRepWrite;
import com.neuronrobotics.bowlerstudio.threed.StepFace;
import eu.mihosoft.vrl.v3d.CSG;

/** Exact triangles 777, 805 and 925 after STEPFile's unchanged nametag preprocessing. */
public class StepNametagSeamRegressionTest {
    @Test
    public void theMeasuredNametagDiagonalStaysInsideOneFace() {
        double[] points = {
            -59.882693858936115, -68.86891176844921, 2.999998999999998, // 381
            -59.92195629552549, -69.60209397678534, 2.999998999999998, // 372
            -59.92190534855179, -69.6020939797163, 5.999997999999998, // 420
            -59.88264291222147, -68.8689117655158, 5.999997999999998, // 457
            -59.88265600289158, -68.8691561371088, 5.999997999999998 // 456
        };
        CSG mesh = new CSG(points, new long[]{0,1,2, 0,2,3, 3,2,4}, javafx.scene.paint.Color.GRAY);
        List<StepFace> faces = StepBRepWrite.faces(mesh);
        assertEquals("the side and its 4.39e-9 mm sliver must form one face", 1, faces.size());
        assertEquals(1, faces.get(0).loops.size());
        List<double[]> loop = faces.get(0).loops.get(0);
        assertEquals("keep the sliver tip on the outline", 5, loop.size());
        for (int i=0; i<loop.size(); i++) {
            double[] a=loop.get(i), b=loop.get((i+1)%loop.size());
            boolean diagonal=(same(a,points,0)&&same(b,points,2)) || (same(a,points,2)&&same(b,points,0));
            assertFalse("the original 3.088541354 mm diagonal is internal", diagonal);
        }
        assertTrue(faces.get(0).isPlanar());
    }
    private static boolean same(double[] p,double[] points,int v) {
        for(int i=0;i<3;i++) if(p[i]!=points[v*3+i]) return false;
        return true;
    }
}
