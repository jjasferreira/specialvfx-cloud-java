package pt.ulisboa.tecnico.cnv.aslb;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;


public class ASLBServer {

    private static final String AWS_REGION = "eu-west-3";
    private static final String SEC_GROUP_ID = "sg-xxxx";
    private static final String AMI_ID = "ami-xxxxxxxx";
    private static final String KEY_NAME = "mykeypair";

    public static void main(String[] args) throws Exception {
        LoadBalancer lb = new LoadBalancer(AWS_REGION, SEC_GROUP_ID, AMI_ID, KEY_NAME);
        AutoScaler as = new AutoScaler(AWS_REGION, SEC_GROUP_ID, AMI_ID, KEY_NAME, lb);
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new RootHandler());
        server.createContext("/raytracer", new RayRequestHandler(lb));
        server.createContext("/blurimage", new BlurRequestHandler(lb));
        server.createContext("/enhanceimage", new EnhanceRequestHandler(lb));
        server.start();
    }
}
