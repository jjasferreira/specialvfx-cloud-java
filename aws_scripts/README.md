This directory stores all scripts necessary to setup and deploy our webserver on the aws cloud
Before starting, be sure to change the paths in install-lb.sh and install-worker.sh, to properly copy the files onto the cloud
To properly deploy our load balancer/auto-scaler webserver on the cloud, follow the next steps:
- Copy your aws .pem key file onto this directory;
- Fill config.sh with the proper information regarding your account;
- Run setup.sh, to setup necessary IAM role, VPC and security group;
- Compile the webserver project, run create-worker-image to register the worker AMI;
- Change in the LoadBalancer/AutoScaler code the necessary constants (worker AMI and created security group)
- Compile the as_lb project, run create-lb-image to register the lb image;
- Run runServer to run the lb/as webserver in the cloud;

When done, run stopServer to stop all running instances, or cleanup to delete all instances, images, snapshots and volumes;
Lastly, the script deleteSetup deletes all things created during the setup.sh script