package pt.ulisboa.tecnico.cnv.aslb;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public abstract class RequestHandler implements HttpHandler {
    
    public LoadBalancer lb;
    
    abstract String[] getBestInstance(String[] args);
    abstract String[] getCallArgs(HttpExchange t);
    abstract boolean isLambdaCall(String[] args);
    abstract String doLambdaCall(HttpExchange t);
    

    public RequestHandler(LoadBalancer lb) {
        this.lb = lb;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        // Handling CORS
        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            t.sendResponseHeaders(204, -1);
            return;
        }

        InputStream stream = t.getRequestBody();
        String[] args = getCallArgs(t);
        System.out.println(args[0]);
        if (isLambdaCall(args)) {
            System.out.println("Doing lambda call");
            String response = doLambdaCall(t);
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
        else {
            System.out.println("Doing regular request");
            String[] bestInstanceInfo = getBestInstance(args);
            System.out.println(bestInstanceInfo[1]);
            if (bestInstanceInfo[0].equals("")) {
                t.sendResponseHeaders(500, -1);
                return;
            }
            
            String requestBody = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
            System.out.println(requestBody);
            try {
                int requestId = lb.registerRequest(bestInstanceInfo[0], Double.parseDouble(bestInstanceInfo[4]));
                String response = post(requestBody, bestInstanceInfo);
                System.out.println(response);
                lb.closeRequest(bestInstanceInfo[0], requestId);

                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
            catch (Exception e) {
                t.sendResponseHeaders(500, -1);
                e.printStackTrace();
                return;
            }
        }
    }

    public String post(String requestBody, String[] urlInfo) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://" + urlInfo[1] + ":" + urlInfo[2] + "/" + urlInfo[3]))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        System.out.println("http://" + urlInfo[1] + ":" + urlInfo[2] + "/" + urlInfo[3]);  
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

}
