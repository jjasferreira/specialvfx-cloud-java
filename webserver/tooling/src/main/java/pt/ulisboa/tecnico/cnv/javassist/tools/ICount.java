package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.s3.model.Metrics;
import javassist.CannotCompileException;
import javassist.CtBehavior;
import pt.ulisboa.tecnico.cnv.javassist.MetricsHelper;

public class ICount extends CodeDumper {

    /**
     * Number of executed basic blocks.
     */
    //private static long nblocks = 0;

    /**
     * Number of executed methods.
     */
    //private static long nmethods = 0;

    /**
     * Number of executed instructions.
     */
    private static long ninsts = 0;

    private static Map<Long, Long> ninstsmap = new HashMap<>();
    private static Map<Long, String> methodmap = new HashMap<>();

    // Store the request that are being instrumented, should be reset every time we write to Helper Class
    // ThreadId -> RequestType (String: "blur", "enhance", "raytrace")
    private static Map<Long, String> requestTypeMap = new HashMap<>();

    // Store the data we want to associate with the request, should be reset every time we write to Helper Class
    // ThreadId -> Data (String) comma separated list of values blur,enhance: size(widthxheight); raytrace: hash scene hash texturemap
    private static Map<Long, String> requestDataMap = new HashMap<>();

    private static Thread currentThread;

    private static MetricsHelper mh;

    public ICount(List<String> packageNameList, String writeDestination) throws Exception {
        super(packageNameList, writeDestination);

        // Check or wait until the table exists
        mh = new MetricsHelper();
    }

    public static void incBasicBlock(int position, int length) {
        long threadID = Thread.currentThread().getId();

        if (!ninstsmap.containsKey(threadID)) {
            StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
            System.out.println("Called from method: " + stackTraceElements[2] + " with thread ID: " + threadID);
        }
        ninstsmap.put(threadID, ninstsmap.getOrDefault(threadID, 0L) + length);
    }

    public static void incBasicBlock(int position, int length, String request) {
        //TODO Do something
    }

    public static void incBehavior(String name) {
        //nmethods++;
    }

    public static void setThreadId(Thread thread) {
        currentThread = thread;
    }

    public static void printStatistics(long threadId) throws Exception {
        //Thread.sleep(1500L);
        //System.out.printf("CurrentThread: %d", currentThread.getId());
        /*System.out
                .println(String.format("[%s] Number of executed methods: %s", ICount.class.getSimpleName(), nmethods));
        System.out.println(
                String.format("[%s] Number of executed basic blocks: %s", ICount.class.getSimpleName(), nblocks));*/

        String dbKey = requestTypeMap.getOrDefault(threadId, "null") + "," + requestDataMap.getOrDefault(threadId, "null");
        mh.addEntry(dbKey, ninstsmap.getOrDefault(threadId, -1L));

        /*System.out.println("Print Statistics");

        for (long key : ninstsmap.keySet()) {
            System.out.println("Key: " + key + " Value: " + ninstsmap.get(key));
        }*/

        /*for (Map.Entry<Long, Long> entry : ninstsmap.entrySet()) {

            if (entry.getValue() != null){
                System.out.println(
                        String.format("[Handle #%d][Thread #%d] Number of executed instructions: %s",
                                currentThread.getId(), entry.getKey(), entry.getValue())
                );

            }
            System.out.println(
                    String.format("Thread #%s, RequestType %s", entry.getKey(), requestTypeMap.getOrDefault(entry.getKey(), "null"))
            );

        }*/

        if (ninstsmap.get(threadId) != null){

            long value = ninstsmap.get(threadId);

            System.out.println(
                    String.format("[Handle #%d][Thread #%d] Number of executed instructions: %s",
                            currentThread.getId(), threadId, value)
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
        ninstsmap.remove(threadId);

        System.out.println("\033[31mCleared Hash Maps!\033[0m");
        System.out.println("---------------------------");
    }

    public static void setRequestType(long threadId, String requestType) {

        if (requestTypeMap.putIfAbsent(threadId, requestType) != null) { // Returns null when successfully inserts
            System.out.println("[\033[31mERROR\033[0m] There's already a request in this thread!");
        }

    }

    public static void setImageProcRequestData(long threadId, int width, int height) {

        String requestData = width + "x" + height;

        if (requestDataMap.putIfAbsent(threadId, requestData) != null) { // Returns null when successfully inserts
            System.out.println("[\033[31mERROR\033[0m] There's already a request in this thread!");
        }

    }

    public static void setRaytracerRequestData(long threadId, String input_hash, String texmap_hash, int swidth, int sheight, int coff, int roff) {

        String requestData = input_hash;
        if (texmap_hash != null) {
            requestData += "," + texmap_hash;
        }
        requestData += "," + swidth + "," + sheight + "," + coff + "," + roff;
        System.out.println("setRaytracerRequestData");
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

            /*switch(this.getClass().getSimpleName()) {
                case "BlurImageHandler": %s.setRequestType(Thread.currentThread().getId(), "blur"); break;
                case "EnhanceImageHandler": %s.setRequestType(Thread.currentThread().getId(), "enhance"); break;
                case "RaytracerHandler": %s.setRequestType(Thread.currentThread().getId(), "raytrace"); break;
                default: break;
            }*/

            // Before the handle method is executed
            behavior.insertBefore(String.format("switch(this.getClass().getSimpleName()) {\n" +
                            " case \"BlurImageHandler\": %s.setRequestType(Thread.currentThread().getId(), \"blur\"); break; \n" +
                            " case \"EnhanceImageHandler\": %s.setRequestType(Thread.currentThread().getId(), \"enhance\"); break; \n" +
                            " case \"RaytracerHandler\": %s.setRequestType(Thread.currentThread().getId(), \"raytrace\"); break;\n" +
                            " default: break;\n" +
                            " }",
                    ICount.class.getName(), ICount.class.getName(), ICount.class.getName()));

            behavior.insertBefore(String.format("%s.setThreadId(Thread.currentThread());", ICount.class.getName()));
            behavior.insertAfter(String.format("%s.incBehavior(\"%s\");", ICount.class.getName(), behavior.getLongName()));


            // Change "main" to "handle" from the Raytracer Handler or the ImageProcc
            // handler
            // if (name=="handle") {
            // b.insertBefore("cap(x,y)");
            // b.inserAfter("printStatistics()");
            // }
            //
            // Can we do this to insert the capture method to the function - No -> The
            // compiler transfer local
            // variables to relative positions
            //
            // But javassist provides special parameters ->
            // $0 -> this
            // $1, $2 -> parameters of the function call (Better)
            // $$ -> expands all the parameters (Can also be useful)
            // so cap($1, $2) -> uses the first and second parameter of the handle method if
            // the handle method has two parameters

            if (behavior.getDeclaringClass().getSimpleName().equals("RaytracerHandler")) {
                behavior.insertAfter(String.format("%s.setRaytracerRequestData(Thread.currentThread().getId(), input_hash_b64, texmap_hash_b64, scols, srows, coff, roff);", ICount.class.getName()));
            }
            behavior.insertAfter(String.format("%s.printStatistics(Thread.currentThread().getId());", ICount.class.getName()));
        }


        if (behavior.getName().equals("process")) {
            if (behavior.getDeclaringClass().getSimpleName().equals("BlurImageHandler")) {
                behavior.insertBefore(String.format("%s.setImageProcRequestData(Thread.currentThread().getId(), $1.getWidth(), $1.getHeight());", ICount.class.getName()));
            } else if (behavior.getDeclaringClass().getSimpleName().equals("EnhanceImageHandler")) {
                behavior.insertBefore(String.format("%s.setImageProcRequestData(Thread.currentThread().getId(), $1.getWidth(), $1.getHeight());", ICount.class.getName()));
            }
        }

        super.transform(behavior);
        //ninstsmap = new HashMap<>();
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
