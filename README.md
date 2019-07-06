# Bowler Kernel Scripting environment

[![Join the chat at https://gitter.im/CommonWealthRobotics/bowler-script-kernel](https://badges.gitter.im/NeuronRobotics/bowler-script-kernel.svg)](https://gitter.im/NeuronRobotics/bowler-script-kernel?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
This is the core kernel mechanism for the Bowler operating system. It consists of a modular scripting environment. This engine passes Lists of JVM objects back and forth across languages, letting you mix Java, Groovy, Clojure and Python within a single memory sharing application. The sources are stored in git repos and hyperlink at the source level to add modules. You can call this application from the command line like bash and pass it scripts to run sequentially or pipe the output from one into the input of another.

## All platforms 
 You need to use the installer from 
 * [BowlerStudio Installer](https://github.com/CommonWealthRobotics/BowlerStudio/releases)
 
## Usage
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

## Maven
![](https://img.shields.io/nexus/r/https/oss.sonatype.org/com.neuronrobotics/BowlerScriptingKernel.svg?style=flat)


```
<dependency>
  <groupId>com.neuronrobotics</groupId>
  <artifactId>BowlerScriptingKernel</artifactId>
  <version>VERSION_FROM_BADGE</version>
</dependency>
```
## Gradle
![](https://img.shields.io/nexus/r/https/oss.sonatype.org/com.neuronrobotics/BowlerScriptingKernel.svg?style=flat)
```
dependencies {
 compile "com.neuronrobotics:BowlerScriptingKernel:VERSION_FROM_BADGE"
}
```

## All platforms 
```
  Usage: 
  
  java -jar BowlerScriptKernel.jar -s .. # This will load one script after the next

  java -jar BowlerScriptKernel.jar -p .. # This will load one script then take the list of objects returned and pass them to the next script as its 'args' variable
  
  java -jar BowlerScriptKernel.jar -r <Groovy,Clojure,Jython> #Starts a repl for interactive robot coding
  
  java -jar BowlerScriptKernel.jar -g <GIT url>  <File> # Runs a file from its git location
```

