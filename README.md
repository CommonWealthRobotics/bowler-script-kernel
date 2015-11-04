#Bowler Kernel Scripting environment
This is the core kernel mechanism for the Bowler operating system. it
consists of a modular scripting environment. This engine passes Lists of JVM objects back and forth across languages. This lets you mix Java, Groovy, Clojure and Python within a single memory sharing application. The sources are stored in git repos and hyperlink at the source level to add modules. You can call this application from the command line like bash and pass it scripts to run sequentially or pipe the output from one into the input of another.

##All platforms 
 You need to use the installer from 
 * [BowlerStudio Installer](https://github.com/NeuronRobotics/BowlerStudio/releases)

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
  
```
 

