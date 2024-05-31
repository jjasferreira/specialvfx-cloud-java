#!/bin/bash

source config.sh

# Install Java and Maven
cmd="sudo yum update -y; sudo yum install java-11-amazon-corretto.x86_64 -y"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat aslb-scripts/instance.dns) $cmd

# Transfer the ASLB JAR to the instance
scp -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH $DIR/../aslb/target/cnv-1.0.0-SNAPSHOT.jar ec2-user@$(cat aslb-scripts/instance.dns):

# Setup ASLB server to start on instance launch
cmd="echo \"java -cp /home/ec2-user/cnv-1.0.0-SNAPSHOT.jar pt.ulisboa.tecnico.cnv.aslb.ASLBServer >> /home/ec2-user/aslbserver.log 2>&1 &\" | sudo tee -a /etc/rc.local; sudo chmod +x /etc/rc.local"
ssh -o StrictHostKeyChecking=no -i $AWS_EC2_SSH_KEYPAR_PATH ec2-user@$(cat aslb-scripts/instance.dns) $cmd
