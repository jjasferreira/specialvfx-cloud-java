#!/bin/bash

source config.sh

# Run new instance.
aws ec2 run-instances \
	--image-id resolve:ssm:/aws/service/ami-amazon-linux-latest/amzn2-ami-hvm-x86_64-gp2 \
	--instance-type t2.micro \
	--key-name $AWS_KEYPAIR_NAME \
	--security-group-ids $AWS_SECURITY_GROUP \
	--iam-instance-profile Name=WORKERInstanceProfile \
	--monitoring Enabled=true | jq -r ".Instances[0].InstanceId" > worker-scripts/instance.id
echo "New instance with id $(cat worker-scripts/instance.id)."

# Wait for instance to be running.
aws ec2 wait instance-running --instance-ids $(cat worker-scripts/instance.id)
echo "New instance with id $(cat worker-scripts/instance.id) is now running."

# Extract DNS nane.
aws ec2 describe-instances \
	--instance-ids $(cat worker-scripts/instance.id) | jq -r ".Reservations[0].Instances[0].NetworkInterfaces[0].PrivateIpAddresses[0].Association.PublicDnsName" > worker-scripts/instance.dns
echo "New instance with id $(cat worker-scripts/instance.id) has address $(cat worker-scripts/instance.dns)."

# Wait for instance to have SSH ready.
while ! nc -z $(cat worker-scripts/instance.dns) 22; do
	echo "Waiting for $(cat worker-scripts/instance.dns):22 (SSH)..."
	sleep 0.5
done
echo "New instance with id $(cat worker-scripts/instance.id) is ready for SSH access."

