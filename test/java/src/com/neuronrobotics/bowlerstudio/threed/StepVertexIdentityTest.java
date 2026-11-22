package com.neuronrobotics.bowlerstudio.threed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertTrue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import eu.mihosoft.vrl.v3d.CSG;

/** Actual nametag corner: triangle 248 and its three edge neighbors after preprocessing. */
public class StepVertexIdentityTest {
    @Test
    public void nearbyDistinctCornerVerticesDoNotBecomeClosedLineEdges() {
        double[] points={
            -64.00000005,-116.99999995,7.9999985928571435, //109
            -64.00000005,-117.00000017857143,7.99999905, //111
            -64.00000005,-116.99999995,7.99999905, //110
            -63.99999995,-117.00000005,1.7499992062499994, //57
            -64.29451211029968,-116.99999995,7.99999905 //108
        };
        CSG csg=new CSG(points,new long[]{0,1,2,0,3,1,1,4,2,4,0,2},javafx.scene.paint.Color.GRAY);
        StepEmitter emitter=new StepEmitter();
        StepBRepWrite.items(emitter,csg,"actual-corner");
        Matcher m=Pattern.compile("EDGE_CURVE\\('',[#](\\d+),[#](\\d+),[#](\\d+),\\.T\\.\\)").matcher(emitter.text());
        int edges=0;
        while(m.find()) {
            edges++;
            assertFalse("distinct source points must not become a LINE edge with the same start/end VERTEX_POINT: "+m.group(),m.group(1).equals(m.group(2)));
        }
        assertTrue("the exact corner must emit edges",edges>=3);
    }
    @Test
    public void subConfusionLineUsesAUnitVectorWithoutMovingItsEndpoints() {
        // The first edge is the actual 9.999999406e-8 mm edge found in the nametag.
        CSG csg=new CSG(new double[]{-64.00000005,-117,8,-63.99999995,-117,8,-64,-116,8},
                new long[]{0,1,2},javafx.scene.paint.Color.GRAY);
        StepEmitter e=new StepEmitter();StepBRepWrite.items(e,csg,"tiny-line");
        Matcher entities=Pattern.compile("#(\\d+)=(.*?);").matcher(e.text());
        Map<String,String> rhs=new HashMap<>();
        while(entities.find())rhs.put(entities.group(1),entities.group(2));
        int count=0;
        for(String entity:rhs.values())if(entity.startsWith("LINE(")) {
            Matcher refs=Pattern.compile("#(\\d+)").matcher(entity);refs.find();refs.find();
            String vector=rhs.get(refs.group(1));
            double magnitude=Double.parseDouble(vector.substring(vector.lastIndexOf(',')+1,vector.length()-1));
            assertEquals("STEP LINE direction scale is independent of edge length",1.0,magnitude,0.0);count++;
        }
        assertEquals(3,count);
    }

}
