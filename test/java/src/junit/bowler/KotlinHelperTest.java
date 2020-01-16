package junit.bowler;

import com.neuronrobotics.bowlerstudio.scripting.KotlinHelper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;

public class KotlinHelperTest {

    private KotlinHelper helper = new KotlinHelper();

    @Test
    public void testReturningNonClassValue() throws Exception {
        assertEquals(42, helper.inlineScriptRun("42", null));
    }

    @Test
    public void testReturningClassValue() throws Exception {
        assertEquals("Foo", helper.inlineScriptRun("class Foo\nFoo::class.java", null).getClass()
                .getSimpleName());
    }

    @Test
    public void testReturningKotlinScriptSkeletonInstance() throws Exception {
        assertEquals(42, helper.inlineScriptRun(
                "import com.neuronrobotics.bowlerstudio.scripting.KotlinScriptSkeleton\n"
                        + "class Foo : KotlinScriptSkeleton {\n"
                        + "override fun runScript(args: List<Any?>?): Any? = 42\n" + "}\n"
                        + "Foo::class.java", null));
    }

    @Test
    public void testReturningKotlinScriptSkeletonInstanceWithArguments() throws Exception {
        ArrayList<Object> args = new ArrayList<>(Collections.singletonList(42));
        assertEquals(args, helper.inlineScriptRun(
                "import com.neuronrobotics.bowlerstudio.scripting.KotlinScriptSkeleton\n"
                        + "class Foo : KotlinScriptSkeleton {\n"
                        + "override fun runScript(args: List<Any?>?): Any? = args\n" + "}\n"
                        + "Foo::class.java", args));
    }
}
