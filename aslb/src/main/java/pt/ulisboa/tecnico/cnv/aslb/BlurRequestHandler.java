package pt.ulisboa.tecnico.cnv.aslb;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import com.sun.net.httpserver.HttpExchange;

import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LambdaException;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;


public class BlurRequestHandler extends RequestHandler {

    private static final int TYPE = 2;  // Value defined for Blur request
    private static final String LAMBDA_NAME = "blur-func";
    
    public BlurRequestHandler(LoadBalancer lb) {
        super(lb);
    }

    public String doLambdaCall(String result, URI requestedUri, Map<String, Object> b) {
        String[] resultSplits = result.split(",");
        String format = resultSplits[0].split("/")[1].split(";")[0];
        String body = resultSplits[1];
        String json = "{\"body\": \"" + body + "\", \"fileFormat\": \"" + format + "\"}";

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
    public String[] getCallArgs(String result, URI requestedUri, Map<String, Object> b) {
        String[] resultSplits = result.split(",");
        String encodedImage = resultSplits[1];
        byte[] decoded = Base64.getDecoder().decode(encodedImage);
        ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
        try {
            BufferedImage bi = ImageIO.read(bais);
            String[] args = new String[1];
            args[0] = String.valueOf(bi.getWidth() * bi.getHeight());
            return args;
        }
        catch(Exception e) {
            e.printStackTrace();
            return new String[2];
        }
    }
}
