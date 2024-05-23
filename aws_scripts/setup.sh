#!/bin/bash

source config.sh 

#Add LoadBalancer Role
./setupScripts/addLBRole.sh

#Add LoadBalancer Instance profile
./setupScripts/addInstanceProfile.sh

#Add VPC and Security group
./setupScripts/addSecGroup.sh

echo "All scripts executed"
