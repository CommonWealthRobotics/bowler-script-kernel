#!/bin/bash

rm build/libs/* ; 
ps ax|grep java|grep Kernel|cut -d' ' -f2|xargs -n1 kill -9 ; 
./gradlew --offline shadowJar;
rm ~/.github
rm -rf ~/bowler-workspace
java -jar build/libs/BowlerScriptingKernel-*-fat.jar -g https://github.com/OperationSmallKat/greycat.git launch.groovy 

exit 0

rm -rf /media/hephaestus/9a5619d0-38d6-4005-89ba-72d1e34dd71f/home/pi/*.jar
cp build/libs/BowlerScriptingKernel-*-fat.jar /media/hephaestus/9a5619d0-38d6-4005-89ba-72d1e34dd71f/home/pi/