package pt.ulisboa.tecnico.cnv.as_lb_server;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.dynamodbv2.util.TableUtils;


public class LoadBalancer {
    // Constants
    private static String AWS_REGION = "eu-west-3";
    private static String AMI_ID = "ami-043a37e48ef248940";
    private static String KEY_NAME = "mykeypair";
    private static String SEC_GROUP_ID = "sg-02c357dfc6f12c746";
    private static double MAX_LAMBDA_COMPLEXITY = 20; 
    private static int ROUND_SCALE = 100;   // round request args to the hundreds, to group similar request args together
    private static double MAX_COMPLEXITY = 100;
    private static double REQUEST_TRIGGER = 100;   // every n requests, go get values at dynamoDB
    private static String TABLE_NAME ="request-complexity-table";
    private static Double LINE_COEF = 0.2;
    private static Double BLOCK_COEF = 0.5;
    private static Double FUNC_COEF = 0.7;
    private static Double EX_COEF = 0.5;
    private static Double TS_COEF = 0.1;

    private static AmazonDynamoDB dynamoDB;
    private List<String> availableInstances;        // contains available instances IDs
    public boolean isOutscaling = false;
    private HashMap<String, InstanceStats> instanceStats = new HashMap<String, InstanceStats>();
    private int requestCounter = 0;

    // The following ordered maps provide a "local" database representation of the DynamoDB
    private TreeMap<Integer, Double> blurEstimates = new TreeMap<Integer, Double>();         // <imageSize, complexity>
    private TreeMap<Integer, Double> enhanceEstimates = new TreeMap<Integer, Double>();      // <imageSize, complexity>
    private TreeMap<Integer, TreeMap<Integer, Double>> rayEstimates = new TreeMap<Integer, TreeMap<Integer, Double>>();    // <sceneSize, <windowSize, complexity>>



    public LoadBalancer() {
        System.out.println("Launching Load Balancer...");
        availableInstances = new ArrayList<String>();
        try {
            System.out.println("Initializing DynamoDB...");
            initializeDynamoDB();
            launchFirstInstance();
            System.out.println("Load Balancer is currently operational");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int registerRequest(String instance, double complexity) {
        InstanceStats stats = instanceStats.get(instance);
        requestCounter += 1;
        stats.addRequest(requestCounter, complexity);
        if (requestCounter % REQUEST_TRIGGER == 0) {
            pullDynamoDB();
        }
        return requestCounter;
    }

    public void closeRequest(String instance, int requestId) {
        InstanceStats stats = instanceStats.get(instance);
        stats.closeRequest(requestId);
    }

    public boolean isLambdaCall(int type, String[] args) {
        double complexity = estimateComplexity(type, args);
        if (complexity > MAX_LAMBDA_COMPLEXITY) {
            return false;
        }
        else if (isOutscaling || notFitForAny(complexity)) {
            return true;
        }
        else {
            return false;
        }
    }

    public boolean notFitForAny(double complexity) {
        for (InstanceStats stats : instanceStats.values()) {
            if (stats.getTotalComplexity() + complexity < MAX_COMPLEXITY) {
                return false;
            }
        }
        return true;
    }

    public double estimateComplexity(int type, String[] args) {
        List<Integer> roundedArgs = new ArrayList<>();
        switch (type) {
            case 1: roundedArgs.add(roundArg(args[0])); 
                    roundedArgs.add(roundArg(args[0])); 
                    return getComplexityRayTracer(roundedArgs);
            case 2: roundedArgs.add(roundArg(args[0]));
                    return getComplexityImageProc(blurEstimates, roundedArgs);
            case 3: roundedArgs.add(roundArg(args[0])); 
                    return getComplexityImageProc(enhanceEstimates, roundedArgs);
            default: System.err.println("Type not recognized"); return -1;
        }
    }

    public int roundArg(String arg) {
        int argument = Integer.parseInt(arg);
        argument = Math.round(argument/ROUND_SCALE) * ROUND_SCALE;
        return argument;
    }

    public double getComplexityRayTracer(List<Integer> roundedArgs) {
        TreeMap<Integer, Double> lowerMap = new TreeMap<Integer, Double>();
        int lowerScene = 0;
        for (Map.Entry<Integer, TreeMap<Integer, Double>> entry: rayEstimates.entrySet()) {
            if (entry.getKey() < roundedArgs.get(0)) {
                lowerMap = entry.getValue();
                lowerScene = entry.getKey();
            }
            else if (entry.getKey() == roundedArgs.get(0)) {
                lowerMap = entry.getValue();
                lowerScene = 1;
                break;
            }
            else {
                if (lowerScene == 0) {
                    lowerMap = entry.getValue();
                    lowerScene = 1;
                    break;
                }
                else {
                    if (Math.abs(entry.getKey() - roundedArgs.get(0)) < Math.abs(lowerScene - roundedArgs.get(0))) {
                        lowerMap = entry.getValue();
                        break;
                    }
                    else {
                        break;
                    }
                }
            }
        }
        if (lowerScene == 0) {
            return 0;
        }
        else {
            List<Integer> window = new ArrayList<>();
            window.add(roundedArgs.get(1));
            return getComplexityImageProc(lowerMap, window);
        }
    }

    public double getComplexityImageProc(TreeMap<Integer, Double> complexityMap, List<Integer> roundedArgs) {
        double lowerComp = 0;
        int lowerSize = 0;
        for (Map.Entry<Integer, Double> entry : complexityMap.entrySet()) {
            if (entry.getKey() < roundedArgs.get(0)) {
                lowerSize = entry.getKey();
                lowerComp = entry.getValue();
            }
            else if (entry.getKey() == roundedArgs.get(0)) {
                return entry.getValue();
            }
            else if (entry.getKey() > roundedArgs.get(0)) {
                double higherComp = entry.getValue();
                int higherSize = entry.getKey();
                if (lowerSize == 0) {
                    return higherComp;
                }
                else {
                    double m = (higherComp - lowerComp) / (higherSize - lowerSize);
                    return m * roundedArgs.get(0);
                }
            }
        } 
        return lowerComp;  
    }

    public double calculateComplexity(String nLines, String nBlocks, String nFuncs) {
        int lines = Integer.parseInt(nLines);
        int blocks = Integer.parseInt(nBlocks);
        int funcs = Integer.parseInt(nFuncs);
        double complexity = lines * LINE_COEF + blocks * BLOCK_COEF + funcs * FUNC_COEF;
        return complexity;
    }

    
    public String[] getBestInstance(int requestType, String[] args) {
        double complexity = estimateComplexity(requestType, args);
        if (!notFitForAny(complexity)) {
            String lowerId = "";
            double lowerComplexity = MAX_COMPLEXITY;
            for (String instanceId : availableInstances) {
                double instanceComplexity = instanceStats.get(instanceId).getTotalComplexity();
                if (instanceComplexity < lowerComplexity) {
                    lowerId = instanceId;
                    lowerComplexity = instanceComplexity;
                }
            }
            return getInstanceInformation(requestType, lowerId, complexity);
        }
        else {
            String bestId = bestFutureCase(complexity);
            return getInstanceInformation(requestType, bestId, complexity);
        }
    }


    public String[] getInstanceInformation(int type, String bestId, double complexity) {
        String[] instanceInfo = {"", "", "", "", ""};
        InstanceStats stats = instanceStats.get(bestId);
        instanceInfo[0] = bestId;
        instanceInfo[1] = stats.getPublicIP();
        instanceInfo[2] = "8000";
        switch (type) {
            case 1: instanceInfo[3] = "raytracer"; break;
            case 2: instanceInfo[3] = "blurimage"; break;
            case 3: instanceInfo[3] = "enhanceimage"; break;
            default: instanceInfo[3] = "";
        }
        instanceInfo[4] = String.valueOf(complexity);
        return instanceInfo;
    }

    public String bestFutureCase(double complexity) {
        String bestId = "";
        double bestCoef = -100;
        for (String instanceId : availableInstances) {
            InstanceStats stats = instanceStats.get(instanceId);
            double minLoad = 0;
            double minTS = 0;
            for (Map.Entry<Integer, Double> entry : stats.getRequestList().entrySet()) {
                minTS = requestCounter - entry.getKey();
                minLoad += entry.getValue();
                if (minLoad >= complexity) {
                    break;
                }
            }
            double bestFutureIndex = minTS*TS_COEF - (minLoad - complexity) * EX_COEF;         // describes how likely the instance is to get rid of the
            if (bestFutureIndex > bestCoef) {                                                  // necessary load faster than the rest
                bestCoef = bestFutureIndex;
                bestId = instanceId;
            }
        }                           
        return bestId;                                                           
    }



    private void pullDynamoDB() {
        Thread dbThread = new Thread( () -> {
            System.out.println("Accessing DB for fresh results");
            ScanRequest scanRequest = new ScanRequest().withTableName(TABLE_NAME);
            ScanResult scanResult = dynamoDB.scan(scanRequest);
            List<Map<String,AttributeValue>> results = scanResult.getItems();
            for (Map<String, AttributeValue> row : results) {
                String key = row.get("type-args").getS();
                String attr = row.get("line-block-func").getS();
                String[] keySplit = key.split(";");
                String[] attrSplit = attr.split(";");
                int type = Integer.parseInt(keySplit[0]);
                Integer[] args = new Integer[2];
                for (int i = 1; i < keySplit.length; i++) {
                    args[i-1] = Integer.parseInt(keySplit[i]);
                }
                double complexity = calculateComplexity(attrSplit[0], attrSplit[1], attrSplit[2]);
                switch (type) {
                    case 1: 
                        if (rayEstimates.containsKey(args[0])) {
                            rayEstimates.get(args[0]).put(args[1], complexity);
                        }
                        else {
                            TreeMap<Integer, Double> newMap = new TreeMap<>();
                            newMap.put(args[1], complexity);
                            rayEstimates.put(args[0], newMap);
                        }
                        break;
                    case 2:
                        blurEstimates.put(args[0], complexity);
                        break;
                    case 3:
                        enhanceEstimates.put(args[0], complexity);
                        break;
                    default: break;
                }

            }
        });
        dbThread.start();
    }

    private void initializeDynamoDB() {
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
            .withCredentials(new InstanceProfileCredentialsProvider(false))
            .withRegion(AWS_REGION)
            .build();

        try {
            String tableName = TABLE_NAME;

            // Delete table if it exists, to clear old values
            TableUtils.deleteTableIfExists(dynamoDB, new DeleteTableRequest().withTableName(tableName));


            // Table has "type;arg1;arg2;..." as key and "lines;blocks;functions" as values
            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                        .withKeySchema(new KeySchemaElement().withAttributeName("type-args").withKeyType(KeyType.HASH))
                        .withAttributeDefinitions(new AttributeDefinition().withAttributeName("type-args").withAttributeType(ScalarAttributeType.S))
                        .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            TableUtils.waitUntilActive(dynamoDB, tableName);            
            System.out.println("DynamoDB Table is active");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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
            String newInstanceIP = runInstancesResult.getReservation().getInstances().get(0).getPublicIpAddress();
            InstanceStats stats = new InstanceStats(newInstanceIP);
            instanceStats.put(newInstanceId, stats);
            availableInstances.add(newInstanceId);

            
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
