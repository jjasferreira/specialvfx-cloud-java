package pt.ulisboa.tecnico.cnv.javassist.tools;

import java.util.List;

import javassist.CannotCompileException;
import javassist.CtBehavior;

public class ICount extends CodeDumper {

    /**
     * Number of executed basic blocks.
     */
    private static long nblocks = 0;

    /**
     * Number of executed methods.
     */
    private static long nmethods = 0;

    /**
     * Number of executed instructions.
     */
    private static long ninsts = 0;

    public ICount(List<String> packageNameList, String writeDestination) {
        super(packageNameList, writeDestination);
    }

    public static void incBasicBlock(int position, int length) {
        nblocks++;
        ninsts += length;
    }

    public static void incBehavior(String name) {
        nmethods++;
    }

    public static void printStatistics() {
        System.out
                .println(String.format("[%s] Number of executed methods: %s", ICount.class.getSimpleName(), nmethods));
        System.out.println(
                String.format("[%s] Number of executed basic blocks: %s", ICount.class.getSimpleName(), nblocks));
        System.out.println(
                String.format("[%s] Number of executed instructions: %s", ICount.class.getSimpleName(), ninsts));
    }

    @Override
    protected void transform(CtBehavior behavior) throws Exception {
        super.transform(behavior);
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
        behavior.insertAfter(String.format("%s.printStatistics();", ICount.class.getName()));
    }

    @Override
    protected void transform(BasicBlock block) throws CannotCompileException {
        super.transform(block);
        block.behavior.insertAt(block.line, String.format("%s.incBasicBlock(%s, %s);", ICount.class.getName(),
                block.getPosition(), block.getLength()));
    }

}
