#!/bin/bash

source config.sh

# Install java and maven.
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat worker-scripts/instance.dns) $cmd

# Install web server.
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $DIR/../specialvfx/webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar ec2-user@$(cat worker-scripts/instance.dns):
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $DIR/../specialvfx/tooling/target/JavassistWrapper-1.0-jar-with-dependencies.jar ec2-user@ec2-54-242-204-249.compute-1.amazonaws.com:

# Setup web server to start on instance launch.
#cmd="echo \"java -cp /home/ec2-user/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.webserver.WebServer\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
cmd="echo \"java -cp /home/ec2-user/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:/home/ec2-user/JavassistWrapper-1.0-jar-with-dependencies.jar=ICount:pt.ulisboa.tecnico.cnv.raytracer,pt.ulisboa.tecnico.cnv.imageproc,boofcv:output pt.ulisboa.tecnico.cnv.webserver.WebServer\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat worker-scripts/instance.dns) $cmd

