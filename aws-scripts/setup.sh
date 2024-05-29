#!/bin/bash

source config.sh 

# Add AutoScaler & LoadBalancer role
./setup-scripts/add-aslb-role.sh

# Add AutoScaler & LoadBalancer instance profile
./setup-scripts/add-aslb-instance-profile.sh

# Add VPC and Security group
./setup-scripts/add-vpc-security-group.sh

echo "All scripts executed"
