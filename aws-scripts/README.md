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

- Execute [`create-vpc-security-group.sh`](create-vpc-security-group.sh) to setup VPC and security group;
- Update the `AWS_REGION` variable in the file [AmazonDynamoDBHelper.java](../specialvfx/tooling/src/main/java/pt/ulisboa/tecnico/cnv/javassist/AmazonDynamoDBHelper.java) with the value you chose for the `AWS_DEFAULT_REGION` variable in the [`config.sh`](config.sh) file
- Compile the [`specialvfx`](../specialvfx/) project by running `mvn clean package`;
- Go to the [`faas-scripts`](./faas-scripts/) directory and execute [`register-functions.sh`](./faas-scripts/register-functions.sh) to register the functions in the AWS Lambda service;
- Execute [`create-worker-image.sh`](create-worker-image.sh) to register the Worker AMI;
- Update the first constants of the code contained in file [ASLBServer.java](../aslb/src/main/java/pt/ulisboa/tecnico/cnv/aslb/ASLBServer.java) with the values obtained from the previous steps;
- Compile the [`aslb`](../aslb/) project by running `mvn clean package`;
- Execute [`create-aslb-image.sh`](create-aslb-image.sh) to register the AutoScaler & LoadBalancer AMI;
- Execute [`launch-aslb.sh`](launch-aslb.sh) to run the AutoScaler & LoadBalancer server in an instance of the cloud;

---

- When done, execute [`cleanup.sh`](cleanup.sh) to delete all instances, images, snapshots, volumes, the VPC and the security group;
- Also, go to the [`faas-scripts`](./faas-scripts/) directory and execute [`deregister-functions.sh`](./faas-scripts/deregister-functions.sh).
