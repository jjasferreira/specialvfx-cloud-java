#!/bin/bash

source config.sh

# Requesting an instance reboot.
aws ec2 reboot-instances --instance-ids $(cat workerScripts/instance.id)
echo "Rebooting instance to test web server auto-start."

# Letting the instance shutdown.
sleep 1

# Wait for port 8000 to become available.
while ! nc -z $(cat workerScripts/instance.dns) 8000; do
	echo "Waiting for $(cat workerScripts/instance.dns):8000..."
	sleep 0.5
done
