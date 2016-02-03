#Bowler Kernel Scripting environment

[![Join the chat at https://gitter.im/NeuronRobotics/bowler-script-kernel](https://badges.gitter.im/NeuronRobotics/bowler-script-kernel.svg)](https://gitter.im/NeuronRobotics/bowler-script-kernel?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
This is the core kernel mechanism for the Bowler operating system. it
consists of a modular scripting environment. This engine passes Lists of JVM objects back and forth across languages. This lets you mix Java, Groovy, Clojure and Python within a single memory sharing application. The sources are stored in git repos and hyperlink at the source level to add modules. You can call this application from the command line like bash and pass it scripts to run sequentially or pipe the output from one into the input of another.

##All platforms 
 You need to use the installer from 
 * [BowlerStudio Installer](https://github.com/NeuronRobotics/BowlerStudio/releases)
 
##Usage
This will let you pass code snippets directly to the scripting engine
```
Object returnVal = ScriptingEngine.inlineScriptRun(String code, ArrayList<Object> args,ShellType activeType)
```

This will let you load code directly out of a github gist:
This code will load this github gist:
https://gist.github.com/madhephaestus/d4312a0787456ec27a2a

<script src="https://gist.github.com/madhephaestus/d4312a0787456ec27a2a.js"></script>

```
Object returnVal = ScriptingEngine.inlineGistScriptRun("d4312a0787456ec27a2a", "helloWorld.groovy" ,null) 
```



# Embed as a library in your projects
##Maven
```
<dependency>
  <groupId>com.neuronrobotics</groupId>
  <artifactId>BowlerScriptingKernel</artifactId>
  <version>0.4.28</version>
</dependency>
```
##Gradle
```
dependencies {
 compile "com.neuronrobotics:BowlerScriptingKernel:0.4.28"
}
```
###Macs Only

In the terminal before running the jar you must run:
```
export OPENCV_DIR=<path to yout BowlerStudio.app>BowlerStudio.app/Contents/MacOS/opencv249build/
```
##All platforms 
```
  Usage: 
  
  java -jar BowlerScriptKernel.jar -s .. # This will load one script after the next

  java -jar BowlerScriptKernel.jar -p .. # This will load one script then take the list of objects returned and pss them to the next script as its 'args' variable
  
  java -jar BowlerScriptKernel.jar -r <Groovy,Clojure,Jython> #Starts a repl fo interactive robot coding
  
# Adding additional languages

## Create Enum
First, add a field to com.neuronrobotics.bowlerstudio.scripting.ShellType
```
	NEWSCRIPT("Display name of NewScript"),
```

## Create execution class
Second create a class with methods for running a script. To do this implement IScriptingLanguage.

```
package com.neuronrobotics.bowlerstudio.scripting;
import java.io.File;
import java.util.ArrayList;
public class NewScriptHelper implements IScriptingLanguage {

	@Override
	public Object inlineScriptRun(File code, ArrayList<Object> args) throws Exception {
		// execute code from a file to preserve debugging and stack traces to link to editor
		return null;
	}

	@Override
	public Object inlineScriptRun(String code, ArrayList<Object> args) throws Exception {
		// execute code from a raw string
		return null;
	}

	@Override
	public ShellType getShellType() {
		return ShellType.NEWSCRIPT;
	}

	@Override
	public boolean isSupportedFileExtenetion(String filename) {
		if (name.toString().toLowerCase().endsWith(".newscript")) {
			return true;
		}
		return false;
	}

}
```
## Add execution class to ScriptingEngine

Finaly in the static {} method of Scripting engine, add your new Scripting languages to the internal list of supported languages. 

```
		addScriptingLanguage(new NewScriptHelper());

```
