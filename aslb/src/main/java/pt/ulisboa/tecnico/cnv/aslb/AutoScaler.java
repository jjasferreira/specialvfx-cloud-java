package pt.ulisboa.tecnico.cnv.aslb;

import com.amazonaws.AmazonServiceException;
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
    private static final int SCALE_PERIOD = 60; // seconds
    private static final double MIN_THRESHOLD = 20.0; // CPU utilization %
    private static final double MAX_THRESHOLD = 70.0; // CPU utilization %

    private AmazonCloudWatch cloudWatch;
    private AmazonEC2 ec2;
    private LoadBalancer loadBalancer;
    private Set<String> decomissionedInstances;

    public AutoScaler(String awsRegion, String secGroupId, String amiId, String keyName, LoadBalancer lb) {
        System.out.println("Launching Auto Scaler...");
        AWS_REGION = awsRegion;
        SEC_GROUP_ID = secGroupId;
        AMI_ID = amiId;
        KEY_NAME = keyName;
        this.cloudWatch = AmazonCloudWatchClientBuilder.standard()
            .withRegion(AWS_REGION)
            .withCredentials(new InstanceProfileCredentialsProvider(false))
            .build();
        this.ec2 = AmazonEC2ClientBuilder.standard()
            .withRegion(AWS_REGION)
            .withCredentials(new InstanceProfileCredentialsProvider(false))
            .build();
        this.loadBalancer = lb;
        this.decomissionedInstances = new HashSet<>();

        launchFirstInstance();

        startAutoScaling();
    }

    private void launchFirstInstance() {
        try {
            System.out.println("Starting a new instance.");
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(AMI_ID)
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(KEY_NAME)
                .withSecurityGroupIds(SEC_GROUP_ID);

            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
            String newInstanceIP = runInstancesResult.getReservation().getInstances().get(0).getPublicIpAddress();
            InstanceStats stats = new InstanceStats(newInstanceIP);
            loadBalancer.instanceStats.put(newInstanceId, stats);
            loadBalancer.availableInstances.add(newInstanceId);

            System.out.println("Initial instance launched: " + newInstanceId);

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
        autoScalerThread.start();
    }

    private void checkAndScale() {
        double averageCpuUtilization = getAverageCpuUtilization();
        if (averageCpuUtilization < MIN_THRESHOLD) {
            scaleIn();
        } else if (averageCpuUtilization > MAX_THRESHOLD) {
            scaleOut();
        }
        cleanDecomissionedInstances();
    }

    private double getAverageCpuUtilization() {
        double totalCpuUtilization = 0;
        int instanceCount = loadBalancer.availableInstances.size();

        for (String instanceId : loadBalancer.availableInstances) {
            GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                .withStartTime(new Date(System.currentTimeMillis() - SCALE_PERIOD * 1000))
                .withNamespace("AWS/EC2")
                .withPeriod(SCALE_PERIOD)
                .withMetricName("CPUUtilization")
                .withStatistics("Average")
                .withDimensions(new Dimension().withName("InstanceId").withValue(instanceId))
                .withEndTime(new Date());

            GetMetricStatisticsResult result = cloudWatch.getMetricStatistics(request);
            List<Datapoint> datapoints = result.getDatapoints();

            if (!datapoints.isEmpty()) {
                double cpuUtilization = datapoints.get(0).getAverage();
                System.out.println("CPU utilization for instance " + instanceId + " = " + cpuUtilization);
                totalCpuUtilization += cpuUtilization;
            }
        }
        return instanceCount == 0 ? 0 : totalCpuUtilization / instanceCount;
    }

    private void scaleIn() {
        String instanceToTerminate = null;
        int fewestRequests = Integer.MAX_VALUE;

        for (String instanceId : loadBalancer.availableInstances) {
            InstanceStats stats = loadBalancer.instanceStats.get(instanceId);
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
            decomissionedInstances.add(instanceToTerminate);
        }
    }

    private void scaleOut() {
        try {
            System.out.println("Starting a new instance.");
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withImageId(AMI_ID)
                .withInstanceType("t2.micro")
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(KEY_NAME)
                .withSecurityGroupIds(SEC_GROUP_ID);

            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
            String newInstanceIP = runInstancesResult.getReservation().getInstances().get(0).getPublicIpAddress();
            InstanceStats stats = new InstanceStats(newInstanceIP);
            loadBalancer.instanceStats.put(newInstanceId, stats);
            loadBalancer.availableInstances.add(newInstanceId);

            System.out.println("Scaled out: New instance launched: " + newInstanceId);

        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    private void terminateInstance(String instanceId) {
        try {
            System.out.println("Terminating instance: " + instanceId);
            TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest().withInstanceIds(instanceId);
            ec2.terminateInstances(terminateRequest);
            loadBalancer.availableInstances.remove(instanceId);
            loadBalancer.instanceStats.remove(instanceId);
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
    }

    private void cleanDecomissionedInstances() {
        for (String instanceId : decomissionedInstances) {
            InstanceStats stats = loadBalancer.instanceStats.get(instanceId);
            if (stats.getOngoingRequests() == 0) {
                terminateInstance(instanceId);
                decomissionedInstances.remove(instanceId);
            }
        }
    }
}