#!/bin/bash

source config.sh

# Launch a VM instance
$DIR/worker-scripts/launch-vm.sh

# Install software in the VM instance
$DIR/worker-scripts/install-worker.sh

$DIR/worker-scripts/test-vm.sh

# Create VM image (AMI)
aws ec2 create-image --instance-id $(cat worker-scripts/instance.id) --name Worker-Image | jq -r .ImageId > worker-scripts/image.id
echo "New VM image with id $(cat worker-scripts/image.id)."

# Wait for image to become available
echo "Waiting for image to be ready... (this can take a couple of minutes)"
aws ec2 wait image-available --filters Name=name,Values=Worker-Image
echo "Waiting for image to be ready... done! \o/"

# Terminate the VM instance
aws ec2 terminate-instances --instance-ids $(cat worker-scripts/instance.id)

