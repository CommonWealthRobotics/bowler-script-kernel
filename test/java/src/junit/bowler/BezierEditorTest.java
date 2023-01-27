package junit.bowler;

import static org.junit.Assert.*;
import org.junit.Test;

import com.neuronrobotics.bowlerkernel.Bezier3d.BezierEditor;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;

public class BezierEditorTest {

	@Test(expected = IllegalArgumentException.class)
    public void testInvalidInput() {
        BezierEditor editor = new BezierEditor();
        editor.setControlPoints(null);
    }
	
    @Test
    public void testAddControlPoint() {
        BezierEditor editor = new BezierEditor();
        editor.addControlPoint(new Point(1, 2));
        assertEquals(1, editor.getControlPoints().size());
        assertEquals(new Point(1, 2), editor.getControlPoints().get(0));
    }

    @Test
    public void testRemoveControlPoint() {
        BezierEditor editor = new BezierEditor();
        editor.addControlPoint(new Point(1, 2));
        editor.addControlPoint(new Point(3, 4));
        editor.removeControlPoint(new Point(1, 2));
        assertEquals(1, editor.getControlPoints().size());
        assertEquals(new Point(3, 4), editor.getControlPoints().get(0));
    }

    @Test
    public void testMoveControlPoint() {
        BezierEditor editor = new BezierEditor();
        editor.addControlPoint(new Point(1, 2));
        editor.moveControlPoint(new Point(1, 2), new Point(3, 4));
        assertEquals(1, editor.getControlPoints().size());
        assertEquals(new Point(3, 4), editor.getControlPoints().get(0));
    }

    @Test
    public void testGetCurvePoints() {
        BezierEditor editor = new BezierEditor();
        editor.addControlPoint(new Point(1, 2));
        editor.addControlPoint(new Point(3, 4));
        editor.addControlPoint(new Point(5, 6));
        List<Point> curvePoints = editor.getCurvePoints();
        assertTrue(curvePoints.size() > 0);
    }
}
