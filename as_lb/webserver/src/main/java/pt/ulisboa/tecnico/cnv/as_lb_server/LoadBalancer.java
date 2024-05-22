package pt.ulisboa.tecnico.cnv.as_lb_server;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

public class LoadBalancer {
    private static String AWS_REGION = "eu-west-3";
    private static String AMI_ID = "ami-031bb426c5b085e5e";
    private static String KEY_NAME = "mykeypair";
    private static String SEC_GROUP_ID = "sg-00224d452594e9f2f";

    private List<String> instanceIds;

    public LoadBalancer() {
        System.out.println("Launching Load Balancer...");
        instanceIds = new ArrayList<String>();
        try {
            launchFirstInstance();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    
    public String[] getBestInstance(int requestType) {
        String bestId = instanceIds.get(0);
        String[] instanceInfo = {"", "", ""};
        AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new InstanceProfileCredentialsProvider(false)).build();
        Set<Instance> instances = new HashSet<Instance>();
        for (Reservation reservation : ec2.describeInstances().getReservations()) {
            instances.addAll(reservation.getInstances());
        }
        for (Instance instance : instances) {
            String instanceId = instance.getInstanceId();
            if (instanceId.equals(bestId)) {
                instanceInfo[0] = instance.getPublicIpAddress();
                instanceInfo[1] = "8000";
                switch (requestType) {
                    case 1: instanceInfo[2] = "raytracer"; break;
                    case 2: instanceInfo[2] = "blurimage"; break;
                    case 3: instanceInfo[2] = "enhanceimage"; break;
                    default: instanceInfo[2] = "";
                }
                break;
            }
        }
        return instanceInfo;
    }


    private void launchFirstInstance() throws Exception{
        try {
            AmazonEC2 ec2 = AmazonEC2ClientBuilder.standard().withRegion(AWS_REGION).withCredentials(new InstanceProfileCredentialsProvider(false)).build();
            
            System.out.println("Starting a new instance.");
            RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
            runInstancesRequest.withImageId(AMI_ID)
                               .withInstanceType("t2.micro")
                               .withMinCount(1)
                               .withMaxCount(1)
                               .withKeyName(KEY_NAME)
                               .withSecurityGroupIds(SEC_GROUP_ID);
            RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
            String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
            instanceIds.add(newInstanceId);
            
            Thread.sleep(60000);
            System.out.println("Wait time expired, instance is running");            
        } catch (AmazonServiceException ase) {
                System.out.println("Caught Exception: " + ase.getMessage());
                System.out.println("Reponse Status Code: " + ase.getStatusCode());
                System.out.println("Error Code: " + ase.getErrorCode());
                System.out.println("Request ID: " + ase.getRequestId());
        }
    }
}
