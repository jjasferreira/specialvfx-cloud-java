package pt.ulisboa.tecnico.cnv.javassist;

import java.util.ArrayList;
import java.util.List;

public class MetricsHelper {

    private static List<Pair<String, Long>> metrics = new ArrayList<>();

    public static AmazonDynamoDBHelper dbHelper = new AmazonDynamoDBHelper();

    public MetricsHelper() throws Exception {
        //AmazonDynamoDBHelper.createTable();
    }

    public void addEntry(String entryId, long metric) {

        Pair<String, Long> entry = new Pair<>(entryId, metric);
        metrics.add(entry);

        if (metrics.size() >= 5) {

            // Send to DynamoDB
            for (Pair<String, Long> pair : metrics) {
                AmazonDynamoDBHelper.addEntry(pair.getFirst(), pair.getSecond());
            }
            //AmazonDynamoDBHelper.sendRequests();

            metrics.clear();
            //System.out.println("Cleared metrics!");
        }

    }

}
