package pt.ulisboa.tecnico.cnv.as_lb_server;

public class RayRequestHandler extends RequestHandler{
    
    public RayRequestHandler(LoadBalancer lb) {
        super(lb);
    }

    public String[] getBestInstance() {
        int type = 1;     // value defined for RayTracing request
        return lb.getBestInstance(type);
    }
}
