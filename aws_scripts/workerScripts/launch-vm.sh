#!/bin/bash

source config.sh

# Run new instance.
aws ec2 run-instances \
	--image-id resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2 \
	--instance-type t2.micro \
	--key-name $AWS_KEYPAIR_NAME \
	--security-group-ids $AWS_SECURITY_GROUP \
	--monitoring Enabled=true | jq -r ".Instances[0].InstanceId" > workerScripts/instance.id
echo "New instance with id $(cat workerScripts/instance.id)."

# Wait for instance to be running.
aws ec2 wait instance-running --instance-ids $(cat workerScripts/instance.id)
echo "New instance with id $(cat workerScripts/instance.id) is now running."

# Extract DNS nane.
aws ec2 describe-instances \
	--instance-ids $(cat workerScripts/instance.id) | jq -r ".Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddresses[0].Association.PublicDnsName" > workerScripts/instance.dns
echo "New instance with id $(cat workerScripts/instance.id) has address $(cat workerScripts/instance.dns)."

# Wait for instance to have SSH ready.
while ! nc -z $(cat workerScripts/instance.dns) 22; do
	echo "Waiting for $(cat workerScripts/instance.dns):22 (SSH)..."
	sleep 0.5
done
echo "New instance with id $(cat workerScripts/instance.id) is ready for SSH access."

