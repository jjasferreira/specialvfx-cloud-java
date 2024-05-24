package pt.ulisboa.tecnico.cnv.as_lb_server;

import java.util.TreeMap;

public class InstanceStats {
    private int ongoingRequests = 0;
    private double totalComplexity = 0;
    private TreeMap<Integer, Double> requestList = new TreeMap<Integer, Double>();   // stores <requestId, complexity> for ongoing requests
    private String publicIP;

    public InstanceStats(String ip) {
        publicIP = ip;
    }

    public void addRequest(Integer requestId, double complexity) {
        ongoingRequests += 1;
        totalComplexity += complexity;
        requestList.put(requestId, complexity);
    }

    public void closeRequest(Integer requestId) {
        ongoingRequests -= 1;
        totalComplexity -= requestList.get(requestId);
        requestList.remove(requestId);
    }

    public int getOngoingRequests() {
        return ongoingRequests;
    }

    public double getTotalComplexity() {
        return totalComplexity;
    }

    public TreeMap<Integer, Double> getRequestList() {
        return requestList;
    }

    public String getPublicIP() {
        return publicIP;
    }

}
