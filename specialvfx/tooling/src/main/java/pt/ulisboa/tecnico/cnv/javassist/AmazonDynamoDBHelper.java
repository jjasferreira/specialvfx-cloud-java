package pt.ulisboa.tecnico.cnv.javassist;

import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This sample demonstrates how to perform a few simple operations with the
 * Amazon DynamoDB service.
 */
public class AmazonDynamoDBHelper {

    // TODO - fill fields with correct values.
    private static String AWS_REGION = "eu-west-3";

    private static AmazonDynamoDB dynamoDB;

    private static final String tableName = "request-complexity-table";
    //private static final String tableName = "test-table";

    private static Map<String, List<WriteRequest>> requestItems = new HashMap<>();
    public static List<WriteRequest> writeRequests = new ArrayList<>();


    public AmazonDynamoDBHelper() {
        dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials(new InstanceProfileCredentialsProvider(false))
                //.withCredentials(new EnvironmentVariableCredentialsProvider())
                .withRegion(AWS_REGION)
                .build();
    }

    private static Map<String, AttributeValue> newItem(String request, long complexity) {
        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
        item.put("type-args", new AttributeValue(request));
        item.put("line", new AttributeValue().withS(Long.toString(complexity)));
        return item;
    }

    public static void addEntry(String key, Long value) {
        // Add an item
        dynamoDB.putItem(new PutItemRequest(tableName, newItem(key, value)));

        /*writeRequests.add(new WriteRequest().withPutRequest(new PutRequest(
                newItem(key, value)
        )));*/
        //requestItems.put(tableName, writeRequests);
    }

    public static boolean doesTableExist() {
        try {
            dynamoDB.describeTable(tableName);
            return true;
        } catch (ResourceNotFoundException e) {
                        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                        .withKeySchema(new KeySchemaElement().withAttributeName("type-args").withKeyType(KeyType.HASH))
                        .withAttributeDefinitions(new AttributeDefinition().withAttributeName("type-args").withAttributeType(ScalarAttributeType.S))
                        .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

            TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
            return true;
        }
    }

//    String key = row.get("type-args").getS();
//    String attr = row.get("line-block-func").getS();

    /*public static void sendRequests() {
        requestItems.put(tableName, writeRequests);
        BatchWriteItemRequest batchWriteItemRequest = new BatchWriteItemRequest().withRequestItems(requestItems);
        dynamoDB.batchWriteItem(batchWriteItemRequest);
    }*/

}
