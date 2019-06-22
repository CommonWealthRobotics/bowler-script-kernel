#!/bin/bash

rm build/libs/* ; 
ps ax|grep java|grep Kernel|cut -d' ' -f2|xargs -n1 kill -9 ; 
./gradlew --offline shadowJar;
java -jar build/libs/BowlerScriptingKernel-*-fat.jar -s ~/bowler-workspace/gitcache/github.com/OperationSmallKat/greycat/launch.groovy 
