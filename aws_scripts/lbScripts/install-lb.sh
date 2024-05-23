#!/bin/bash

source config.sh

# Install java and maven.
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat lbScripts/instance.dns) $cmd

# Install web server.
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $DIR/../cnv-shared/cnv24-g33/as_lb/webserver/target/cnv-1.0.0-SNAPSHOT.jar ec2-user@$(cat lbScripts/instance.dns):

# Setup web server to start on instance launch.
cmd="echo \"java -cp /home/ec2-user/cnv-1.0.0-SNAPSHOT.jar pt.ulisboa.tecnico.cnv.as_lb_server.AS_LB_Server\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat lbScripts/instance.dns) $cmd

