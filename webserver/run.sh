#!/bin/bash

cd tooling; mvn package; cd ..;

java -cp webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:tooling/target/JavassistWrapper-1.0-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv.raytracer,pt.ulisboa.tecnico.cnv.imageproc,boofcv:output pt.ulisboa.tecnico.cnv.webserver.WebServer

#java -cp /home/vagrant/cnv-shared/cnv24-g33/aws-java-sdk-1.12.732/lib/aws-java-sdk-1.12.732.jar:/home/vagrant/cnv-shared/cnv24-g33/aws-java-sdk-1.12.732/third-party/lib/*:. pt.ulisboa.tecnico.cnv.javassist.AmazonDynamoDBSample
