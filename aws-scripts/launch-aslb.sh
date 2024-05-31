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
        --monitoring Enabled=true | jq -r ".Instances[0].InstanceId" > aslb-scripts/instance.id

echo "AutoScaler & LoadBalancer server instance has been launched with id $(cat aslb-scripts/instance.id)."

# Wait for instance to be running
aws ec2 wait instance-running --instance-ids $(cat aslb-scripts/instance.id)
echo "New instance with id $(cat aslb-scripts/instance.id) is now running."

# Extract DNS name
aws ec2 describe-instances \
	--instance-ids $(cat aslb-scripts/instance.id) | jq -r ".Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddresses[0].Association.PublicDnsName" > aslb-scripts/instance.dns
echo "New instance with id $(cat aslb-scripts/instance.id) has address $(cat aslb-scripts/instance.dns)."

# Wait for instance to have SSH ready
while ! nc -z $(cat aslb-scripts/instance.dns) 22; do
	echo "Waiting for $(cat aslb-scripts/instance.dns):22 (SSH)..."
	sleep 0.5
done
echo "New instance with id $(cat aslb-scripts/instance.id) is ready for SSH access."