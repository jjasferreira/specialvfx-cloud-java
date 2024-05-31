package pt.ulisboa.tecnico.cnv.aslb;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LoadBalancer {
    // Constants
    private static String AWS_REGION;
    private static String SEC_GROUP_ID;
    private static String AMI_ID;
    private static String KEY_NAME;
    private static String IAM_ROLE_NAME;
    private static String TABLE_NAME ="request-complexity-table";
    private static final int ROUND_SCALE = 100;   // round request args to the hundreds, to group similar request args together
    private static final double MAX_LAMBDA_COMPLEXITY = 1500; 
    private static final double MAX_COMPLEXITY = 40000;
    private static final double DEFAULT_RAYTRACE = 1000;
    private static final double DEFAULT_TEX_RAYTRACE = 3000;
    private static final double DEFAULT_BLUR = 7500;
    private static final double DEFAULT_ENHANCE = 4300;
    private static final double REQUEST_TRIGGER = 2;   // every n requests, go get values at dynamoDB
    private static final double LINE_COEF = 0.000001;
    private static final double EX_COEF = 0.5;
    private static final double TS_COEF = 0.1;

    private static AmazonDynamoDB dynamoDB;
    public List<String> availableInstances;        // contains available instances IDs
    public boolean isOutscaling = false;
    public HashMap<String, InstanceStats> instanceStats = new HashMap<String, InstanceStats>();
    private int requestCounter = 0;

    // The following ordered maps provide a "local" database representation of the DynamoDB
    private TreeMap<Integer, Double> blurEstimates = new TreeMap<Integer, Double>();         // <imageSize, complexity>
    private TreeMap<Integer, Double> enhanceEstimates = new TreeMap<Integer, Double>();      // <imageSize, complexity>
    private TreeMap<String, Double> rayEstimates = new TreeMap<String, Double>();    // <hash1;hash2,complexity>>



    public LoadBalancer(String awsRegion, String secGroupId, String amiId, String keyName, String roleName) {
        System.out.println("[LB] Launching Load Balancer...");
        AWS_REGION = awsRegion;
        SEC_GROUP_ID = secGroupId;
        AMI_ID = amiId;
        KEY_NAME = keyName;
        IAM_ROLE_NAME = roleName;
        availableInstances = new ArrayList<String>();
        try {
            System.out.println("[LB] Initializing DynamoDB...");
            initializeDynamoDB();
            System.out.println("[LB] Load Balancer is currently operational");
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
        double comp;
        switch (type) {
            case 1: return getComplexityRayTracer(args[0], args[1]);
            case 2: roundedArgs.add(roundArg(args[0]));
                    comp = getComplexityImageProc(blurEstimates, roundedArgs);
                    if (comp == 0) {
                        return DEFAULT_BLUR;
                    }
                    else {
                        return comp;
                    }
            case 3: roundedArgs.add(roundArg(args[0])); 
                    comp = getComplexityImageProc(enhanceEstimates, roundedArgs);
                    if (comp == 0) {
                        return DEFAULT_ENHANCE;
                    }
                    else {
                        return comp;
                    }
            default: System.err.println("Type not recognized"); return -1;
        }
    }

    public int roundArg(String arg) {
        int argument = Integer.parseInt(arg);
        argument = Math.round(argument/ROUND_SCALE) * ROUND_SCALE;
        return argument;
    }

    public double getComplexityRayTracer(String sceneHash, String textHash) {
        String key = sceneHash + textHash;
        if (rayEstimates.containsKey(key)) {
            return rayEstimates.get(key);
        }
        else {
            if (textHash.equals("null")) {
                return DEFAULT_RAYTRACE;
            }
            else {
                return DEFAULT_TEX_RAYTRACE;
            }
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

    public double calculateComplexity(String nLines) {
        long lines = Long.parseLong(nLines);
        double complexity = lines * LINE_COEF;
        return complexity;
    }

    
    public String[] getBestInstance(int requestType, String[] args) {
        double complexity = estimateComplexity(requestType, args);
        System.out.println("[LB] Complexity: " + complexity);
        if (!notFitForAny(complexity)) {
            System.out.println("[LB] Fit for some");
            String lowerId = "";
            double lowerComplexity = MAX_COMPLEXITY;
            for (String instanceId : availableInstances) {
                double instanceComplexity = instanceStats.get(instanceId).getTotalComplexity();
                System.out.println("[LB] Total Complexity of instance " + instanceId + " is " + instanceComplexity);
                if (instanceComplexity <= lowerComplexity) {
                    lowerId = instanceId;
                    lowerComplexity = instanceComplexity;
                }
            }
            System.out.println("[LB] Best instance is " + lowerId);
            return getInstanceInformation(requestType, lowerId, complexity);
        }
        else {
            System.out.println("[LB] Not fit for any");
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
        System.out.println("[LB] The best instance info is: id " + instanceInfo[0] + " ; ip " + instanceInfo[1] + " ; port " + instanceInfo[2] + " ; type " + instanceInfo[3] + " ; complexity " + complexity);
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
            System.out.println("[LB] Accessing DB for fresh results");
            ScanRequest scanRequest = new ScanRequest().withTableName(TABLE_NAME);
            ScanResult scanResult = dynamoDB.scan(scanRequest);
            List<Map<String,AttributeValue>> results = scanResult.getItems();
            if (results.size() == 0) {
                System.out.println("[LB] No results in DB");
            }
            for (Map<String, AttributeValue> row : results) {
                String key = row.get("type-args").getS();
                String attr = row.get("line").getN();
                if (key == null) {
                    System.out.println("[LB] Key is null");
                    continue;
                }
                String[] keySplit = key.split(";");
                int type = Integer.parseInt(keySplit[0]);
                Integer[] args = new Integer[2];
                if (type != 1) {
                    for (int i = 1; i < keySplit.length; i++) {
                        args[i-1] = Integer.parseInt(keySplit[i]);
                    }
                }
                double complexity = calculateComplexity(attr);
                switch (type) {
                    case 1: 
                        rayEstimates.put(keySplit[1]+keySplit[2], complexity);
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
            System.out.println("[LB] Deleting old table");
            TableUtils.deleteTableIfExists(dynamoDB, new DeleteTableRequest().withTableName(tableName));
            Thread.sleep(20000);
            System.out.println("[LB] Creating new table");

            // Table has "type;arg1;arg2;..." as key and "lines;blocks;functions" as values
            CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                        .withKeySchema(new KeySchemaElement().withAttributeName("type-args").withKeyType(KeyType.HASH))
                        .withAttributeDefinitions(new AttributeDefinition().withAttributeName("type-args").withAttributeType(ScalarAttributeType.S))
                        .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            TableUtils.waitUntilActive(dynamoDB, tableName);            
            System.out.println("[LB] DynamoDB Table is active");
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
