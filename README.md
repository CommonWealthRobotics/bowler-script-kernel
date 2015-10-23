Bowler Kernel Scripting environment
@madhephaestus madhephaestus released this an hour ago · 2 commits to master since this release

This is the core kernel mechanism for the Bowler operating system. it
consists of a modular scripting environment. This engine passes Lists of JVM objects back and forth across languages. This lets you mix Java, Groovy, Clojure and Python within a single memory sharing application. The sources are stored in git repos and hyperlink at the source level to add modules. You can call this application from the command line like bash and pass it scripts to run sequentially or pipe the output from one into the input of another.

Usage: java -jar BowlerScriptKernel.jar -s .. # This will load one script after the next

Usage: java -jar BowlerScriptKernel.jar -p .. # This will load one script then take the list of objects returned and pss them to the next script as its 'args' variable
