package junit.bowler;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import com.neuronrobotics.bowlerstudio.threed.STEPFile;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.Cube;

public class StepLinkedTessellationRegressionTest {
    @Test
    public void alternateMeshRetainsItsIndicesWithoutCreatingAnotherPart() throws Exception {
        CSG c=new Cube(10).toCSG();
        STEPFile writer=new STEPFile(c);
        String text=writer.toStep();
        STEPFile reader=new STEPFile(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        assertEquals("one physical part despite two representations",1,reader.getShapes().size());
        CSG mesh=reader.getShapes().get(0).getMesh();
        assertNotNull("retain the alternate mesh",mesh);
        assertArrayEquals(writer.getCsg().getVertices(),mesh.getVertices(),0.0);
        assertArrayEquals(writer.getCsg().getTriangles(),mesh.getTriangles());
    }
}
