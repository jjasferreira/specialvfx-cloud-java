# aws-scripts

This directory stores all scripts necessary to setup and deploy our webserver on the AWS cloud.

### To properly deploy our AutoScaler & LoadBalancer server on the cloud, follow the next steps:

- Copy your AWS `mykeypair.pem` key file onto this directory;
- Fill [`config.sh`](config.sh) with the proper information regarding your AWS account;
- Change the permissions of all scripts and the key file:

```bash
find . -type f -name '*.sh' -exec chmod 777 {} +
chmod 400 mykeypair.pem
```

- Execute [`setup.sh`](setup.sh) to setup necessary IAM role, VPC and security group;
- Compile the [`specialvfx`](../specialvfx/) project by running `mvn package` and execute [`create-worker-image.sh`](create-worker-image.sh) to register the Worker AMI;
- Update the first constants of the code contained in file [ASLBServer.java](../aslb/src/main/java/pt/ulisboa/tecnico/cnv/aslb/ASLBServer.java);
- Compile the [`aslb`](../aslb/) project by running `mvn package` and execute [`create-aslb-image.sh`](create-aslb-image.sh) to register the AutoScaler & LoadBalancer AMI;
- Execute [`run-aslb-server.sh`](run-aslb-server.sh) to launch the AutoScaler & LoadBalancer in the cloud;

---

- When done, execute [`cleanup.sh`](cleanup.sh) to delete all instances, images, snapshots and volumes;
- Lastly, the script [`delete-setup.sh`](delete-setup.sh) deletes all things created during the `setup.sh` script.
