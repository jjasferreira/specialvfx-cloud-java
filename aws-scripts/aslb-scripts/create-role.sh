#!/bin/bash

source config.sh

# Variables
ROLE_NAME="ASLBRole"
POLICY_NAME="CustomPermissionPolicy"
TRUST_POLICY='{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "ec2.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    },
    {
      "Effect": "Allow",
      "Principal": {
        "Service": "lambda.amazonaws.com"
      },
      "Action": "sts:AssumeRole"
    }
  ]
}'
CUSTOM_POLICY='{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "Statement1",
      "Effect": "Allow",
      "Action": [
        "iam:GetRole",
        "iam:PassRole"
      ],
      "Resource": [
        "arn:aws:iam::381491903948:role/WorkerRole"
      ]
    }
  ]
}'

# Check if IAM Role exists
ROLE_EXISTS=$(aws iam get-role --role-name $ROLE_NAME 2>&1)

if [[ $ROLE_EXISTS == *"NoSuchEntity"* ]]; then
  # Create IAM Role
  aws iam create-role --role-name $ROLE_NAME --assume-role-policy-document "$TRUST_POLICY"
  echo "IAM Role '$ROLE_NAME' created successfully."
else
  echo "IAM Role '$ROLE_NAME' already exists. Skipping creation."
fi

# Attach predefined policies to the Role
aws iam attach-role-policy --role-name $ROLE_NAME --policy-arn arn:aws:iam::aws:policy/AmazonEC2FullAccess
aws iam attach-role-policy --role-name $ROLE_NAME --policy-arn arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess
aws iam attach-role-policy --role-name $ROLE_NAME --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

echo "Policies AmazonEC2FullAccess, AmazonDynamoDBFullAccess, and AWSLambdaBasicExecutionRole attached to IAM Role '$ROLE_NAME'."

# Check if the custom permission policy exists
POLICY_ARN=$(aws iam list-policies --query "Policies[?PolicyName=='$POLICY_NAME'].Arn" --output text)

if [[ -z "$POLICY_ARN" ]]; then
  # Create the custom permission policy
  POLICY_ARN=$(aws iam create-policy --policy-name $POLICY_NAME --policy-document "$CUSTOM_POLICY" --query 'Policy.Arn' --output text)
  echo "Custom permission policy '$POLICY_NAME' created successfully."
else
  echo "Custom permission policy '$POLICY_NAME' already exists. Skipping creation."
fi

# Attach the custom permission policy to the role
aws iam attach-role-policy --role-name $ROLE_NAME --policy-arn "$POLICY_ARN"

echo "Custom permission policy attached to IAM Role '$ROLE_NAME'."

