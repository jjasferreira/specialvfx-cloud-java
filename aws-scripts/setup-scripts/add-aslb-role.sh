#!/bin/bash

source config.sh
# Variables
ROLE_NAME="ASLB"
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


# Check if IAM Role exists
ROLE_EXISTS=$(aws iam get-role --role-name $ROLE_NAME 2>&1)

if [[ $ROLE_EXISTS == *"NoSuchEntity"* ]]; then
  # Create IAM Role
  aws iam create-role --role-name $ROLE_NAME --assume-role-policy-document "$TRUST_POLICY"
  echo "IAM Role '$ROLE_NAME' created successfully."
else
  echo "IAM Role '$ROLE_NAME' already exists. Skipping creation."
fi

# Attach Policies to the Role
aws iam attach-role-policy --role-name $ROLE_NAME --policy-arn arn:aws:iam::aws:policy/AmazonEC2FullAccess
aws iam attach-role-policy --role-name $ROLE_NAME --policy-arn arn:aws:iam::aws:policy/AmazonDynamoDBFullAccess
aws iam attach-role-policy --role-name $ROLE_NAME --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole

echo "Policies AmazonEC2FullAccess and AmazonDynamoDBFullAccess attached to IAM Role '$ROLE_NAME'."

