package pt.ulisboa.tecnico.cnv.as_lb_server;

public class BlurRequestHandler extends RequestHandler {
    
    public BlurRequestHandler(LoadBalancer lb) {
        super(lb);
    }

    public String[] getBestInstance() {
        int type = 2;     // value defined for Blur request
        return lb.getBestInstance(type);
    }
}
