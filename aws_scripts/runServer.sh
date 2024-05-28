#!/bin/bash

source config.sh

METADATA_OPTIONS='{"HttpTokens":"optional"}'

# Run new instance.
aws ec2 run-instances \
        --image-id $(cat lbScripts/image.id) \
        --instance-type t2.micro \
        --key-name $AWS_KEYPAIR_NAME \
        --security-group-ids $AWS_SECURITY_GROUP \
	--metadata-options $METADATA_OPTIONS \
	--iam-instance-profile Name=LoadBalancerInstanceProfile \
        --monitoring Enabled=true

echo "Load Balancer web server instance has been launched, wait a moment"
