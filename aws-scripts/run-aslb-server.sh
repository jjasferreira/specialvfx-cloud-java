#!/bin/bash

source config.sh

METADATA_OPTIONS='{"HttpTokens":"optional"}'

# Run new instance for the AutoScaler & LoadBalancer
aws ec2 run-instances \
        --image-id $(cat aslb-scripts/image.id) \
        --instance-type t2.micro \
        --key-name $AWS_KEYPAIR_NAME \
        --security-group-ids $AWS_SECURITY_GROUP \
	--metadata-options $METADATA_OPTIONS \
	--iam-instance-profile Name=ASLBInstanceProfile \
        --monitoring Enabled=true

echo "AutoScaler & LoadBalancer server instance has been launched, wait a moment"
