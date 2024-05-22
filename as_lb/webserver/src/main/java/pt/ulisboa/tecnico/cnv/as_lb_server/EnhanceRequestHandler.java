package pt.ulisboa.tecnico.cnv.as_lb_server;


public class EnhanceRequestHandler extends RequestHandler {
    
    public EnhanceRequestHandler(LoadBalancer lb) {
        super(lb);
    }

    public String[] getBestInstance() {
        int type = 3;     // value defined for Enhance request
        return lb.getBestInstance(type);
    }
}
