#!/bin/bash

source config.sh

# Create Worker role
$DIR/worker-scripts/create-role.sh

# Create Worker instance profile
$DIR/worker-scripts/create-instance-profile.sh
sleep 10

# Launch Worker VM instance
$DIR/worker-scripts/launch-vm.sh

# Install Worker server in the instance
$DIR/worker-scripts/install-server.sh
sleep 5

# Test Worker server
$DIR/worker-scripts/test-vm.sh

# Create VM image (AMI)
aws ec2 create-image --instance-id $(cat worker-scripts/instance.id) --name WorkerImage | jq -r .ImageId > worker-scripts/image.id
echo "New VM image with id $(cat worker-scripts/image.id)."

# Wait for image to become available
echo "Waiting for image to be ready... (this can take a couple of minutes)"
aws ec2 wait image-available --filters Name=name,Values=WorkerImage
echo "Waiting for image to be ready... done! \o/"

# Terminate the VM instance
aws ec2 terminate-instances --instance-ids $(cat worker-scripts/instance.id)
