package pt.ulisboa.tecnico.cnv.as_lb_server;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;


public class AS_LB_Server {
    public static void main(String[] args) throws Exception {
        LoadBalancer lb = new LoadBalancer();
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new RootHandler());
        server.createContext("/raytracer", new RayRequestHandler(lb));
        server.createContext("/blurimage", new BlurRequestHandler(lb));
        server.createContext("/enhanceimage", new EnhanceRequestHandler(lb));
        server.start();
    }
}
