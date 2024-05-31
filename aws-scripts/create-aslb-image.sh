#!/bin/bash

source config.sh

# Create AutoScaler & LoadBalancer role
$DIR/aslb-scripts/create-role.sh

# Create AutoScaler & LoadBalancer instance profile
$DIR/aslb-scripts/create-instance-profile.sh
sleep 10

# Launch AutoScaler & LoadBalancer VM instance
$DIR/aslb-scripts/launch-vm.sh

# Install AutoScaler & LoadBalancer server in the instance
$DIR/aslb-scripts/install-server.sh
sleep 5

# Test AutoScaler & LoadBalancer server
$DIR/aslb-scripts/test-vm.sh

# Create VM image (AMI)
aws ec2 create-image --instance-id $(cat aslb-scripts/instance.id) --name ASLBImage | jq -r .ImageId > aslb-scripts/image.id
echo "New VM image with id $(cat aslb-scripts/image.id)."

# Wait for image to become available
echo "Waiting for image to be ready... (this can take a couple of minutes)"
aws ec2 wait image-available --filters Name=name,Values=ASLBImage
echo "Waiting for image to be ready... done! \o/"

# Terminate the VM instance
aws ec2 terminate-instances --instance-ids $(cat aslb-scripts/instance.id)
