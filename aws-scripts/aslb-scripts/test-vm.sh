#!/bin/bash

source config.sh

# Request an instance reboot
aws ec2 reboot-instances --instance-ids $(cat aslb-scripts/instance.id)
echo "Rebooting instance to test web server auto-start."

# Let the instance shutdown
sleep 5

# Wait for port 8000 to become available
while ! nc -z $(cat aslb-scripts/instance.dns) 8000; do
	echo "Waiting for $(cat aslb-scripts/instance.dns):8000..."
	sleep 0.5
done
