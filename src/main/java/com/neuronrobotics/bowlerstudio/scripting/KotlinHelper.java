package com.neuronrobotics.bowlerstudio.scripting;
//
//import de.swirtz.ktsrunner.objectloader.KtsObjectLoader;
//
//import java.io.File;
//import java.nio.file.Files;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.stream.Collectors;
//import java.util.stream.Stream;
//
///**
// * Provides kotlin support. If you want to pass arguments to the script. return a class which
// * implements KotlinScriptSkeleton (it will be instantiated and runScript will be called).
// */
//
//public class KotlinHelper implements IScriptingLanguage {
//	static {
//		System.setProperty("idea.io.use.fallback", "true");
//	}
//    private Object inline(String content, ArrayList<Object> args) throws Exception {
//        KtsObjectLoader loader = new KtsObjectLoader();
//        Object result = loader.getEngine().eval(content);
//
//        if (result instanceof Class<?>) {
//            // Try to parse the class into a KotlinScriptSkeleton
//            Object instance = ((Class<?>) result).getDeclaredConstructor().newInstance();
//            if (instance instanceof KotlinScriptSkeleton) {
//                // This is the skeleton interface for kotlin scripts, so we can pass args in
//                return ((KotlinScriptSkeleton) instance).runScript(args);
//            } else {
//                return instance;
//            }
//        } else {
//            // Didn't get a class so we don't know what to do
//            return result;
//        }
//    }
//
//    @Override
//    public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
//        try (Stream<String> lines = Files.lines(code.toPath())) {
//            return inline(lines.collect(Collectors.joining("\n")), args);
//        }
//    }
//
//    @Override
//    public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
//        return inline(code, args);
//    }
//
//    @Override
//    public String getShellType() {
//        return "Kotlin";
//    }
//
//    @Override
//    public ArrayList<String> getFileExtenetion() {
//        return new ArrayList<>(Arrays.asList("kt", "kts"));
//    }
//
//    @Override
//    public boolean getIsTextFile() {
//        return true;
//    }
//}
