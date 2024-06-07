#!/bin/bash

source config.sh

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

# Function to delete an IAM role and its instance profile
delete_iam_role_and_instance_profile() {
  ROLE_NAME=$1
  INSTANCE_PROFILE_NAME=$2

  # Detach policies from the role
  POLICY_ARNS=$(aws iam list-attached-role-policies --role-name $ROLE_NAME --query 'AttachedPolicies[*].PolicyArn' --output text)
  for POLICY_ARN in $POLICY_ARNS; do
    aws iam detach-role-policy --role-name $ROLE_NAME --policy-arn $POLICY_ARN
    echo "Detached policy '$POLICY_ARN' from role '$ROLE_NAME'."
  done

  # Delete policy named 'CustomPermissionPolicy'
  POLICY_ARN=$(aws iam list-policies --query 'Policies[?PolicyName==`CustomPermissionPolicy`].Arn' --output text)
  if [ -n "$POLICY_ARN" ]; then
    aws iam delete-policy --policy-arn $POLICY_ARN
    echo "Deleted policy '$POLICY_ARN'."
  fi

  # Delete instance profile
  INSTANCE_PROFILE_EXISTS=$(aws iam get-instance-profile --instance-profile-name $INSTANCE_PROFILE_NAME 2>&1)
  if [[ $INSTANCE_PROFILE_EXISTS != *"NoSuchEntity"* ]]; then
    aws iam remove-role-from-instance-profile --instance-profile-name $INSTANCE_PROFILE_NAME --role-name $ROLE_NAME
    aws iam delete-instance-profile --instance-profile-name $INSTANCE_PROFILE_NAME
    echo "Deleted instance profile '$INSTANCE_PROFILE_NAME'."
  else
    echo "Instance profile '$INSTANCE_PROFILE_NAME' does not exist."
  fi

  # Delete the role
  aws iam delete-role --role-name $ROLE_NAME
  echo "Deleted IAM role '$ROLE_NAME'."
}

# Function to delete a VPC and its associated resources
delete_vpc_security_group() {
  VPC_ID=$(aws ec2 describe-vpcs --filters "Name=isDefault,Values=true" --query 'Vpcs[0].VpcId' --output text)

  if [ "$VPC_ID" == "None" ]; then
    echo "VPC '$VPC_NAME' does not exist."
    return
  fi

  # Detach and delete Internet Gateways
  IGW_ID=$(aws ec2 describe-internet-gateways --filters "Name=attachment.vpc-id,Values=$VPC_ID" --query 'InternetGateways[0].InternetGatewayId' --output text)
  if [ "$IGW_ID" != "None" ]; then
    aws ec2 detach-internet-gateway --internet-gateway-id $IGW_ID --vpc-id $VPC_ID
    aws ec2 delete-internet-gateway --internet-gateway-id $IGW_ID
    echo "Detached and deleted Internet Gateway '$IGW_ID'."
  fi

  # Delete subnets
  SUBNET_IDS=$(aws ec2 describe-subnets --filters "Name=vpc-id,Values=$VPC_ID" --query 'Subnets[*].SubnetId' --output text)
  for SUBNET_ID in $SUBNET_IDS; do
    aws ec2 delete-subnet --subnet-id $SUBNET_ID
    echo "Deleted Subnet '$SUBNET_ID'."
  done

  # Delete route tables (excluding the main route table)
  ROUTE_TABLE_IDS=$(aws ec2 describe-route-tables --filters "Name=vpc-id,Values=$VPC_ID" --query 'RouteTables[?Associations[0].Main==`false`].RouteTableId' --output text)
  for ROUTE_TABLE_ID in $ROUTE_TABLE_IDS; do
    aws ec2 delete-route-table --route-table-id $ROUTE_TABLE_ID
    echo "Deleted Route Table '$ROUTE_TABLE_ID'."
  done

  # Delete security groups (excluding the default security group)
  SECURITY_GROUP_IDS=$(aws ec2 describe-security-groups --filters "Name=vpc-id,Values=$VPC_ID" --query 'SecurityGroups[?GroupName!=`default`].GroupId' --output text)
  for SECURITY_GROUP_ID in $SECURITY_GROUP_IDS; do
    aws ec2 delete-security-group --group-id $SECURITY_GROUP_ID
    echo "Deleted Security Group '$SECURITY_GROUP_ID'."
  done

  # Delete VPC
  aws ec2 delete-vpc --vpc-id $VPC_ID
  echo "Deleted VPC '$VPC_ID'."
}

# Execute deletion functions
delete_instances
delete_amis
delete_snapshots
delete_volumes

# Delete the IAM role and its instance profile
delete_iam_role_and_instance_profile "WorkerRole" "WorkerInstanceProfile"
delete_iam_role_and_instance_profile "ASLBRole" "ASLBInstanceProfile"

# Delete the VPC and the security groups
delete_vpc_security_group

echo "All instances, AMIs, snapshots, volumes, roles, instance profiles, the VPC and security groups have been deleted."
