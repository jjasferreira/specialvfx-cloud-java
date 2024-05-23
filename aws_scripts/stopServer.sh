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

delete_instances
