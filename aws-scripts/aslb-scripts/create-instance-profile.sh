#!/bin/bash

source config.sh

# Variables
ROLE_NAME="ASLBRole"
INSTANCE_PROFILE_NAME="ASLBInstanceProfile"

# Check if Instance Profile exists
INSTANCE_PROFILE_EXISTS=$(aws iam get-instance-profile --instance-profile-name $INSTANCE_PROFILE_NAME 2>&1)

if [[ $INSTANCE_PROFILE_EXISTS == *"NoSuchEntity"* ]]; then
  # Create Instance Profile
  aws iam create-instance-profile --instance-profile-name $INSTANCE_PROFILE_NAME
  aws iam add-role-to-instance-profile --instance-profile-name $INSTANCE_PROFILE_NAME --role-name $ROLE_NAME
  echo "Instance profile '$INSTANCE_PROFILE_NAME' created and role '$ROLE_NAME' attached."
else
  echo "Instance profile '$INSTANCE_PROFILE_NAME' already exists. Skipping creation."
fi
