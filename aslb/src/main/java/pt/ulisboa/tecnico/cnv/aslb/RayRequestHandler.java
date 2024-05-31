package pt.ulisboa.tecnico.cnv.aslb;

import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;

public class RayRequestHandler extends RequestHandler{

    private static final int TYPE = 1;  // Value defined for Ray Tracer request
    private static final String LAMBDA_NAME = "raytrace-func";
    
    public RayRequestHandler(LoadBalancer lb) {
        super(lb);
    }

    public String doLambdaCall(String requestBody, URI requestedUri, Map<String, Object> body) {
        String query = requestedUri.getRawQuery();
        Map<String, String> parameters = queryToMap(query);

        int scols = Integer.parseInt(parameters.get("scols"));
        int srows = Integer.parseInt(parameters.get("srows"));
        int wcols = Integer.parseInt(parameters.get("wcols"));
        int wrows = Integer.parseInt(parameters.get("wrows"));
        int coff = Integer.parseInt(parameters.get("coff"));
        int roff = Integer.parseInt(parameters.get("roff"));
        boolean aa = Boolean.parseBoolean(parameters.getOrDefault("aa", "false"));
        boolean multi = Boolean.parseBoolean(parameters.getOrDefault("multi", "false"));

        byte[] input = ((String) body.get("scene")).getBytes();
        byte[] texmap = null;
        if (body.containsKey("texmap")) {
            // Convert ArrayList<Integer> to byte[]
            ArrayList<Integer> texmapBytes = (ArrayList<Integer>) body.get("texmap");
            texmap = new byte[texmapBytes.size()];
            for (int i = 0; i < texmapBytes.size(); i++) {
                texmap[i] = texmapBytes.get(i).byteValue();
            }
        }
        Base64.Encoder encoder = Base64.getEncoder();
        String inputTxt = encoder.encodeToString(input);
        String json;
        if (texmap != null) {
            String texTxt = encoder.encodeToString(texmap);
            json = "{\"scols\": \"" + scols + "\", \"srows\": \"" + srows + "\", \"wcols\": \"" + wcols + "\", \"wrows\": \"" + wrows + "\", \"coff\": \"" + coff + "\", \"roff\": \"" + roff + "\", \"aa\": \"" + aa + "\", \"multi\": \"" + multi + "\", \"input\": \"" + inputTxt + "\", \"texmap\": \"" + texTxt + "\"}";
        } 
        else {
            json = "{\"scols\": \"" + scols + "\", \"srows\": \"" + srows + "\", \"wcols\": \"" + wcols + "\", \"wrows\": \"" + wrows + "\", \"coff\": \"" + coff + "\", \"roff\": \"" + roff + "\", \"aa\": \"" + aa + "\", \"multi\": \"" + multi + "\", \"input\": \"" + inputTxt + "\"}";
        }

        try {
            LambdaClient awsLambda = LambdaClient.builder().credentialsProvider(InstanceProfileCredentialsProvider.create()).build();
            SdkBytes payload = SdkBytes.fromUtf8String(json);
            InvokeRequest request = InvokeRequest.builder().functionName(LAMBDA_NAME).payload(payload).build();
    
            InvokeResponse res = awsLambda.invoke(request);
            String value = res.payload().asUtf8String() ;
            awsLambda.close();
            return value;
        }
        catch(LambdaException e) {
            System.err.println(e.getMessage());
            return "ERROR";
        }
    }

    public String[] getBestInstance(String[] args) {
        return lb.getBestInstance(TYPE, args);
    }

    public boolean isLambdaCall(String[] args) {
        return lb.isLambdaCall(TYPE, args);
    }

    // Return size of image
    public String[] getCallArgs(String requestBody, URI requestedUri, Map<String, Object> body) {
        String query = requestedUri.getRawQuery();

        byte[] input = ((String) body.get("scene")).getBytes();
        byte[] texmap = null;
        if (body.containsKey("texmap")) {
            // Convert ArrayList<Integer> to byte[]
            ArrayList<Integer> texmapBytes = (ArrayList<Integer>) body.get("texmap");
            texmap = new byte[texmapBytes.size()];
            for (int i = 0; i < texmapBytes.size(); i++) {
                texmap[i] = texmapBytes.get(i).byteValue();
            }
        }

        MessageDigest md = null;
        try {
            md = MessageDigest.getInstance("SHA-256");
        }
        catch (Exception e) {
            e.printStackTrace();
            return new String[2];
        }

        byte[] inputHash = md.digest(input);
        byte[] textHash = null;
        if (texmap != null) {
            textHash = md.digest(texmap);
        }

        String[] args = new String[2];
        args[0] = Base64.getEncoder().encodeToString(inputHash);
        if (textHash == null) {
            args[1] = "null";
        }
        else {
            args[1] = Base64.getEncoder().encodeToString(textHash);
        }
        return args;
    }

    public Map<String, String> queryToMap(String query) {
        if (query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
