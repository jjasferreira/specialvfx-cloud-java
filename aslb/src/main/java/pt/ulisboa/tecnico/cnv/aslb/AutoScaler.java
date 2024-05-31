package pt.ulisboa.tecnico.cnv.aslb;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoScaler {

    private static String AWS_REGION;
    private static String SEC_GROUP_ID;
    private static String AMI_ID;
    private static String KEY_NAME;
    private static String IAM_ROLE_NAME;
    private static final int SCALE_PERIOD = 300; // seconds
    private static final double MIN_THRESHOLD = 20.0; // CPU utilization %
    private static final double MAX_THRESHOLD = 70.0; // CPU utilization %
    private static final int MIN_INSTANCES = 1;
    private static final int MAX_INSTANCES = 20;

    private AmazonCloudWatch cloudWatch;
    private AmazonEC2 ec2;
    private LoadBalancer lb;
    private Set<String> decomissionedInstances;

    public AutoScaler(String awsRegion, String secGroupId, String amiId, String keyName, String roleName, LoadBalancer lb) {
        System.out.println("[AS] Launching Auto Scaler with " + SCALE_PERIOD + "s period, " + MIN_THRESHOLD + "% min threshold and " + MAX_THRESHOLD + "% max threshold");
        AWS_REGION = awsRegion;
        SEC_GROUP_ID = secGroupId;
        AMI_ID = amiId;
        KEY_NAME = keyName;
        IAM_ROLE_NAME = roleName;
        System.out.println("[AS] AWS Region: " + AWS_REGION + ", Security Group ID: " + SEC_GROUP_ID + ", Worker AMI ID: " + AMI_ID + ", Key Pair: " + KEY_NAME);
        this.cloudWatch = AmazonCloudWatchClientBuilder.standard()
            .withRegion(AWS_REGION)
            .withCredentials(new InstanceProfileCredentialsProvider(false))
            .build();
        this.ec2 = AmazonEC2ClientBuilder.standard()
            .withRegion(AWS_REGION)
            .withCredentials(new InstanceProfileCredentialsProvider(false))
            .build();
        this.lb = lb;
        this.decomissionedInstances = new HashSet<>();

        launchFirstInstance();

        startAutoScaling();
    }

    private void launchFirstInstance() {
        try {
            System.out.println("[AS] Launching the first instance...");
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(AMI_ID)
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(KEY_NAME)
                .withSecurityGroupIds(SEC_GROUP_ID)
                .withMonitoring(true)
                .withIamInstanceProfile(new IamInstanceProfileSpecification().withName(IAM_ROLE_NAME));

            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
            DescribeInstancesRequest request = new DescribeInstancesRequest();
            List<String> instanceIds = new ArrayList<String>();
            instanceIds.add(newInstanceId);
            request.setInstanceIds(instanceIds);

            System.out.println("Waiting until instance is operational...");
            try {
                Thread.sleep(60000);
            }
            catch (Exception e) {
                e.printStackTrace();
            }

            DescribeInstancesResult result = ec2.describeInstances(request);
            List<Reservation> reservations = result.getReservations();
            for (Reservation reservation  : reservations) {
                List<Instance> instances1 = reservation.getInstances();
                for (Instance instance : instances1) {
                    String newInstanceIP = instance.getPublicIpAddress();
                    System.out.println("[AS] New instance IP " + newInstanceIP);
                    InstanceStats stats = new InstanceStats(newInstanceIP);
                    lb.instanceStats.put(newInstanceId, stats);
                    lb.availableInstances.add(newInstanceId);
                }
            }

            System.out.println("[AS] Time has passed, system may start receiving requests");

        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    private void startAutoScaling() {
        Thread autoScalerThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(SCALE_PERIOD * 1000);
                    checkAndScale();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        System.out.println("[AS] Starting AutoScaling thread...");
        autoScalerThread.start();
    }

    private void checkAndScale() {
        System.out.println("[AS] Running AutoScaling check...");
        double averageCpuUtilization = getAverageCpuUtilization();
        System.out.println("[AS] Average CPU utilization: " + averageCpuUtilization + "%");
        int numRunningInstances = lb.availableInstances.size();
        System.out.println("[AS] Number of running instances: " + numRunningInstances);
        if (averageCpuUtilization < MIN_THRESHOLD && numRunningInstances > MIN_INSTANCES) {
            scaleIn();
        } else if (averageCpuUtilization > MAX_THRESHOLD && numRunningInstances < MAX_INSTANCES) {
            scaleOut();
        }
        cleanDecomissionedInstances();
    }

    private double getAverageCpuUtilization() {
        System.out.println("[AS] Checking CPU utilization...");
        double totalCpuUtilization = 0;
        int instanceCount = lb.availableInstances.size();

        for (String instanceId : lb.availableInstances) {
            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                .withStartTime(new Date(System.currentTimeMillis() - SCALE_PERIOD * 1500))
                .withNamespace("AWS/EC2")
                .withPeriod(SCALE_PERIOD)
                .withMetricName("CPUUtilization")
                .withStatistics("Average")
                .withDimensions(new Dimension().withName("InstanceId").withValue(instanceId))
                .withEndTime(new Date());

            System.out.println(new Date().getTime());

            GetMetricStatisticsResult result = cloudWatch.getMetricStatistics(request);
            List<Datapoint> datapoints = result.getDatapoints();

            if (!datapoints.isEmpty()) {
                double cpuUtilization = datapoints.get(0).getAverage();
                System.out.println("[AS] CPU utilization for instance " + instanceId + " = " + cpuUtilization);
                totalCpuUtilization += cpuUtilization;
            }
            else {
                System.out.println("[AS] No data points yet");
            }
        }
        return totalCpuUtilization / instanceCount;
    }

    private void scaleIn() {
        System.out.println("[AS] Scaling in...");
        String instanceToTerminate = null;
        int fewestRequests = Integer.MAX_VALUE;

        for (String instanceId : lb.availableInstances) {
            InstanceStats stats = lb.instanceStats.get(instanceId);
            int ongoingRequests = stats.getOngoingRequests();

            if (ongoingRequests == 0 && !decomissionedInstances.contains(instanceId)) {
                terminateInstance(instanceId);
                return;
            }
            if (ongoingRequests < fewestRequests && !decomissionedInstances.contains(instanceId)) {
                fewestRequests = ongoingRequests;
                instanceToTerminate = instanceId;
            }
        }
        if (instanceToTerminate != null) {
            System.out.println("[AS] Decomissioned instance: " + instanceToTerminate);
            lb.availableInstances.remove(instanceToTerminate);
            decomissionedInstances.add(instanceToTerminate);
        }
    }

    private void scaleOut() {
        try {
            lb.isOutscaling = true;
            System.out.println("[AS] Scaling out: starting a new instance...");
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(AMI_ID)
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(KEY_NAME)
                .withSecurityGroupIds(SEC_GROUP_ID)
                .withIamInstanceProfile(new IamInstanceProfileSpecification().withName(IAM_ROLE_NAME));
            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
            String newInstanceIP = runInstancesResult.getReservation().getInstances().get(0).getPublicIpAddress();
            InstanceStats stats = new InstanceStats(newInstanceIP);
            lb.instanceStats.put(newInstanceId, stats);
            lb.availableInstances.add(newInstanceId);
            lb.isOutscaling = false;
            System.out.println("[AS] Sucessfully launched new instance: " + newInstanceId);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    private void terminateInstance(String instanceId) {
        try {
            System.out.println("[AS] Terminating instance: " + instanceId);
            TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest().withInstanceIds(instanceId);
            ec2.terminateInstances(terminateRequest);
            lb.availableInstances.remove(instanceId);
            lb.instanceStats.remove(instanceId);
            System.out.println("[AS] Sucessfully terminated instance: " + instanceId);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    private void cleanDecomissionedInstances() {
        System.out.println("[AS] Cleaning decomissioned instances...");
        for (String instanceId : decomissionedInstances) {
            InstanceStats stats = lb.instanceStats.get(instanceId);
            if (stats.getOngoingRequests() == 0) {
                terminateInstance(instanceId);
                decomissionedInstances.remove(instanceId);
            }
        }
    }
}
