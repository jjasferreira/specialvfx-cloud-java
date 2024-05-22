package pt.ulisboa.tecnico.cnv.as_lb_server;

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
    
    abstract String[] getBestInstance();
    

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
        String[] bestInstanceInfo = getBestInstance();
        if (bestInstanceInfo[0].equals("")) {
            t.sendResponseHeaders(500, -1);
            return;
        }
        
        String requestBody = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
        try {
            String response = post(requestBody, bestInstanceInfo);

            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
        catch (Exception e) {
            t.sendResponseHeaders(500, -1);
            return;
        }
    }

    public String post(String requestBody, String[] urlInfo) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://" + urlInfo[0] + ":" + urlInfo[1] + "/" + urlInfo[2]))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

}
