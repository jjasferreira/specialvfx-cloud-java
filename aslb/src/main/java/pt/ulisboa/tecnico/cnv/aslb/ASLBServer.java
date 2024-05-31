package pt.ulisboa.tecnico.cnv.aslb;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;


public class ASLBServer {

    private static final String AWS_REGION = "eu-west-3";
    private static final String SEC_GROUP_ID = "sg-05f33914aae1d945e";
    private static final String AMI_ID = "ami-073e5626523f76801";
    private static final String KEY_NAME = "mykeypair";
    private static final String IAM_ROLE_NAME = "WorkerInstanceProfile";

    public static void main(String[] args) throws Exception {
        System.out.println();
        System.out.println("[ASLB] Starting new ASLB Server...");
        LoadBalancer lb = new LoadBalancer(AWS_REGION, SEC_GROUP_ID, AMI_ID, KEY_NAME, IAM_ROLE_NAME);
        AutoScaler as = new AutoScaler(AWS_REGION, SEC_GROUP_ID, AMI_ID, KEY_NAME, IAM_ROLE_NAME, lb);
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new RootHandler());
        server.createContext("/raytracer", new RayRequestHandler(lb));
        server.createContext("/blurimage", new BlurRequestHandler(lb));
        server.createContext("/enhanceimage", new EnhanceRequestHandler(lb));
        server.start();
    }
}
