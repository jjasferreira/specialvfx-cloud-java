# aws_scripts

This directory stores all scripts necessary to setup and deploy our webserver on the AWS cloud.

<!-- Before starting, be sure to change the paths in install-lb.sh and install-worker.sh, to properly copy the files onto the cloud. -->

### To properly deploy our load balancer/auto-scaler webserver on the cloud, follow the next steps:

- Copy your AWS `<my-key-pair>.pem` key file onto this directory;
- Fill [`config.sh`](config.sh) with the proper information regarding your AWS account;
- Change the permissions of all scripts and the key file:

```bash
find . -type f -name '*.sh' -exec chmod 777 {} +
chmod 400 <my-key-pair>.pem
```

- Execute [`setup.sh`](setup.sh) to setup necessary IAM role, VPC and security group;
- Compile the [`webserver`](../webserver/) project by running `mvn package` and execute [`create-worker-image.sh`](create-worker-image.sh) to register the Worker AMI;
- Update the constants of the code contained in [`as_lb`](../as_lb/) (worker AMI and security group);
- Compile the [`as_lb`](../as_lb/) project by running `mvn package` and execute [`create-lb-image.sh`](create-lb-image.sh) to register the AutoScaler and LoadBalancer AMI;
- Execute [`runServer.sh`](runServer.sh) to launch the AutoScaler and LoadBalancer in the cloud;

---

- When done, execute [`stopServer.sh`](stopServer.sh) to stop all running instances, or [`cleanup.sh`](cleanup.sh) to delete all instances, images, snapshots and volumes;
- Lastly, the script [`deleteSetup.sh`](deleteSetup.sh) deletes all things created during the `setup.sh` script.
