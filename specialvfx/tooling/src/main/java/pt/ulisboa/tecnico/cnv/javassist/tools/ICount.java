package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.s3.model.Metrics;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import pt.ulisboa.tecnico.cnv.javassist.MetricsHelper;

public class ICount extends CodeDumper {

       private static Map<Long, Long> ninstMap = new HashMap<>();

    // Store the request that are being instrumented, should be reset every time we write to Helper Class
    // ThreadId -> RequestType (String: "blur", "enhance", "raytrace")
    private static Map<Long, String> requestTypeMap = new HashMap<>();

    // Store the data we want to associate with the request, should be reset every time we write to Helper Class
    // ThreadId -> Data (String) comma separated list of values blur,enhance: size(widthxheight); raytrace: hash scene hash texturemap
    private static Map<Long, String> requestDataMap = new HashMap<>();

    private static MetricsHelper mh;

    public ICount(List<String> packageNameList, String writeDestination) throws Exception {
        super(packageNameList, writeDestination);

        // TODO: Check or wait until the table exists
        mh = new MetricsHelper();
        while (!mh.doesTableExist()) {
            System.out.println("Waiting for table creation");
            Thread.sleep(5000L);
        }
        System.out.println("Table Created!");
    }

    public static void incBasicBlock(int position, int length) {
        long threadID = Thread.currentThread().getId();

        if (!ninstMap.containsKey(threadID)) {
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
            System.out.println("Called from method: " + stackTraceElements[2] + " with thread ID: " + threadID);
        }

        ninstMap.put(threadID, ninstMap.getOrDefault(threadID, 0L) + length);
    }

    public static void printStatistics(long threadId) throws Exception {

        String dbKey = requestTypeMap.getOrDefault(threadId, "null") + ";" + requestDataMap.getOrDefault(threadId, "null");
        mh.addEntry(dbKey, ninstMap.getOrDefault(threadId, -1L));

        if (ninstMap.get(threadId) != null){

            long value = ninstMap.get(threadId);

            System.out.println(
                    String.format("[Thread #%d] Number of executed instructions: %s", threadId, value)
            );

            System.out.println(
                    String.format("[Thread #%s] RequestType %s", threadId, requestTypeMap.getOrDefault(threadId, "null"))
            );

            System.out.println(
                    String.format("[Thread #%s] RequestData %s", threadId, requestDataMap.getOrDefault(threadId, "null"))
            );
        }

        requestTypeMap.remove(threadId);
        requestDataMap.remove(threadId);
        ninstMap.remove(threadId);

        System.out.println("\033[31mCleared Hash Maps!\033[0m");
        System.out.println("---------------------------");
    }

    public static void setRequestType(long threadId, String requestType) {

        if (requestType.equals("raytrace")) {
            requestType = "1";
        }
        if (requestType.equals("blur")) {
            requestType = "2";
        }
        if (requestType.equals("enhance")) {
            requestType = "3";
        }

        if (requestTypeMap.putIfAbsent(threadId, requestType) != null) { // Returns null when successfully inserts
            System.out.println("[\033[31mERROR\033[0m] There's already a request in this thread!");
        }

    }

    public static void setImageProcRequestData(long threadId, int width, int height) {

        String requestData = String.valueOf(width * height);

        if (requestDataMap.putIfAbsent(threadId, requestData) != null) { // Returns null when successfully inserts
            System.out.println("[\033[31mERROR\033[0m] There's already a request in this thread!");
        }

    }

    public static void setRaytracerRequestData(long threadId, String input_hash, String texmap_hash) {

        String requestData = input_hash;
        if (texmap_hash != null) {
            requestData += ";" + texmap_hash;
        } else {
            requestData += ";null";
        }

        if (requestDataMap.putIfAbsent(threadId, requestData) != null) { // Returns null when successfully inserts
            System.out.println("[\033[31mERROR\033[0m] There's already a request in this thread!");
        }
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {

        /*
         * I am on handle then verify the class that called the method and store on that on a map thread: request type
         * Get the data to identify the request add it to a map tread: data (imageproc: image size, ray tracer: hash of scene hash of texture)
         * In the printStatistics (change the name) send the data to HelperClass that agregates it into some kind of list then when it reaches 10 sends it to dynamodb
         * */

        if (behavior.getName().equals("handle")) {

            // Before the handle method is executed
            behavior.insertBefore(String.format("switch(this.getClass().getSimpleName()) {\n" +
                            " case \"BlurImageHandler\": %s.setRequestType(Thread.currentThread().getId(), \"blur\"); break; \n" +
                            " case \"EnhanceImageHandler\": %s.setRequestType(Thread.currentThread().getId(), \"enhance\"); break; \n" +
                            " case \"RaytracerHandler\": %s.setRequestType(Thread.currentThread().getId(), \"raytrace\"); break;\n" +
                            " default: break;\n" +
                            " }",
                    ICount.class.getName(), ICount.class.getName(), ICount.class.getName()));

            if (behavior.getDeclaringClass().getSimpleName().equals("RaytracerHandler")) {
                behavior.insertAfter(String.format("%s.setRaytracerRequestData(Thread.currentThread().getId(), input_hash_b64, texmap_hash_b64);", ICount.class.getName()));
            }

            behavior.insertAfter(String.format("%s.printStatistics(Thread.currentThread().getId());", ICount.class.getName()));
        }

        // Adds the data in the blur/enhance cases
        if (behavior.getName().equals("process")) {
            if (behavior.getDeclaringClass().getSimpleName().equals("BlurImageHandler")) {
                behavior.insertBefore(String.format("%s.setImageProcRequestData(Thread.currentThread().getId(), $1.getWidth(), $1.getHeight());", ICount.class.getName()));
            } else if (behavior.getDeclaringClass().getSimpleName().equals("EnhanceImageHandler")) {
                behavior.insertBefore(String.format("%s.setImageProcRequestData(Thread.currentThread().getId(), $1.getWidth(), $1.getHeight());", ICount.class.getName()));
            }
        }

        super.transform(behavior);
    }



    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);

        // Ignore some methods that are throwing exception (and the constructor methods)
        if (block.behavior.getLongName().equals("boofcv.misc.BoofMiscOps.sleep(long)") ||
            block.behavior.getLongName().equals("boofcv.misc.BoofMiscOps.getJavaVersion()") ||
            block.behavior.getLongName().equals("pt.ulisboa.tecnico.cnv.raytracer.RaytracerHandler.<clinit>()") ||
            block.behavior.getLongName().equals("pt.ulisboa.tecnico.cnv.raytracer.RaytracerHandler()") ||
            block.behavior.getLongName().equals("pt.ulisboa.tecnico.cnv.imageproc.BlurImageHandler()") ||
            block.behavior.getLongName().equals("pt.ulisboa.tecnico.cnv.imageproc.EnhanceImageHandler()") ||
            block.behavior.getLongName().equals("pt.ulisboa.tecnico.cnv.imageproc.ImageProcessingHandler()")) {
            return;
        }

        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", ICount.class.getName(),
                block.getPosition(), block.getLength()));
    }

    /*protected void transform(BasicBlock block, String request) throws CannotCompileException {
        super.transform(block);

        // Ignore some methods that are throwing exception
        if (block.behavior.getLongName().equals("boofcv.misc.BoofMiscOps.sleep(long)") ||
                block.behavior.getLongName().equals("boofcv.misc.BoofMiscOps.getJavaVersion()")) {
            return;
        }

        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s, %s);", ICount.class.getName(),
                block.getPosition(), block.getLength(), request));

    }*/

}
