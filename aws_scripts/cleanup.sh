#!/bin/bash

# Function to delete all EC2 instances
delete_instances() {
  INSTANCE_IDS=$(aws ec2 describe-instances --query 'Reservations[*].Instances[*].InstanceId' --output text)
  
  if [ -n "$INSTANCE_IDS" ]; then
    echo "Terminating instances: $INSTANCE_IDS"
    aws ec2 terminate-instances --instance-ids $INSTANCE_IDS
    aws ec2 wait instance-terminated --instance-ids $INSTANCE_IDS
    echo "Instances terminated successfully."
  else
    echo "No instances found."
  fi
}

# Function to delete all AMIs
delete_amis() {
  IMAGE_IDS=$(aws ec2 describe-images --owners self --query 'Images[*].ImageId' --output text)
  
  if [ -n "$IMAGE_IDS" ]; then
    for IMAGE_ID in $IMAGE_IDS; do
      echo "Deregistering AMI: $IMAGE_ID"
      aws ec2 deregister-image --image-id $IMAGE_ID
    done
    echo "AMIs deregistered successfully."
  else
    echo "No AMIs found."
  fi
}

# Function to delete all snapshots
delete_snapshots() {
  SNAPSHOT_IDS=$(aws ec2 describe-snapshots --owner-ids self --query 'Snapshots[*].SnapshotId' --output text)
  
  if [ -n "$SNAPSHOT_IDS" ]; then
    for SNAPSHOT_ID in $SNAPSHOT_IDS; do
      echo "Deleting snapshot: $SNAPSHOT_ID"
      aws ec2 delete-snapshot --snapshot-id $SNAPSHOT_ID
    done
    echo "Snapshots deleted successfully."
  else
    echo "No snapshots found."
  fi
}

# Function to delete all EBS volumes
delete_volumes() {
  VOLUME_IDS=$(aws ec2 describe-volumes --query 'Volumes[*].VolumeId' --output text)
  
  if [ -n "$VOLUME_IDS" ]; then
    for VOLUME_ID in $VOLUME_IDS; do
      echo "Deleting volume: $VOLUME_ID"
      aws ec2 delete-volume --volume-id $VOLUME_ID
    done
    echo "Volumes deleted successfully."
  else
    echo "No volumes found."
  fi
}

# Execute deletion functions
delete_instances
delete_amis
delete_snapshots
delete_volumes

echo "All instances, AMIs, snapshots, and volumes have been deleted."

