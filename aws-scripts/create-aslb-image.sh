#!/bin/bash

source config.sh

# Launch a vm instance
$DIR/aslb-scripts/launch-vm.sh

# Install software in the VM instance
$DIR/aslb-scripts/install-aslb.sh

$DIR/aslb-scripts/test-vm.sh

# Create VM image (AMI)
aws ec2 create-image --instance-id $(cat aslb-scripts/instance.id) --name LB-Image | jq -r .ImageId > aslb-scripts/image.id
echo "New VM image with id $(cat aslb-scripts/image.id)."

# Wait for image to become available
echo "Waiting for image to be ready... (this can take a couple of minutes)"
aws ec2 wait image-available --filters Name=name,Values=LB-Image
echo "Waiting for image to be ready... done! \o/"

# Terminate the VM instance
aws ec2 terminate-instances --instance-ids $(cat aslb-scripts/instance.id)

