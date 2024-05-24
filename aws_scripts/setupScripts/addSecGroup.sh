#!/bin/bash

source config.sh
# Variables
SECURITY_GROUP_NAME="MySecurityGroup"
DESCRIPTION="Security group for SSH, HTTP, HTTPS, and custom TCP on port 8000"

# Check if a default VPC exists
DEFAULT_VPC_ID=$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" --query 'Vpcs[0].VpcId' --output text)

if [ "$DEFAULT_VPC_ID" != "None" ]; then
    echo "Default VPC already exists with ID: $DEFAULT_VPC_ID"
else
    echo "No default VPC found. Creating a new default VPC..."
    # Create the default VPC
    DEFAULT_VPC_ID=$(aws ec2 create-default-vpc --query 'Vpc.VpcId' --output text)
    echo "Created default VPC with ID: $DEFAULT_VPC_ID"
fi

# Check if Security Group exists
SECURITY_GROUP_ID=$(aws ec2 describe-security-groups --filters "Name=group-name,Values=$SECURITY_GROUP_NAME" "Name=vpc-id,Values=$DEFAULT_VPC_ID" --query 'SecurityGroups[0].GroupId' --output text)

if [ "$SECURITY_GROUP_ID" == "None" ]; then
  # Create Security Group
  SECURITY_GROUP_ID=$(aws ec2 create-security-group --group-name $SECURITY_GROUP_NAME --description "$DESCRIPTION" --vpc-id $DEFAULT_VPC_ID --query 'GroupId' --output text)
  echo "Security group '$SECURITY_GROUP_NAME' with ID '$SECURITY_GROUP_ID' created successfully."

  # Add Inbound Rules to Security Group
  aws ec2 authorize-security-group-ingress --group-id $SECURITY_GROUP_ID --protocol tcp --port 22 --cidr 0.0.0.0/0 # SSH
  aws ec2 authorize-security-group-ingress --group-id $SECURITY_GROUP_ID --protocol tcp --port 80 --cidr 0.0.0.0/0 # HTTP
  aws ec2 authorize-security-group-ingress --group-id $SECURITY_GROUP_ID --protocol tcp --port 443 --cidr 0.0.0.0/0 # HTTPS
  aws ec2 authorize-security-group-ingress --group-id $SECURITY_GROUP_ID --protocol tcp --port 8000 --cidr 0.0.0.0/0 # Custom TCP

  echo "Inbound rules for SSH, HTTP, HTTPS, and custom TCP on port 8000 added to security group '$SECURITY_GROUP_NAME'."
else
  echo "Security group '$SECURITY_GROUP_NAME' already exists with ID '$SECURITY_GROUP_ID'."
fi

# Set environment variable
echo "export AWS_SECURITY_GROUP=$SECURITY_GROUP_ID" >> config.sh

