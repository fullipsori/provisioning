package com.lguplus.pvs;

public class ConnectionFailoverPolicy {
    public enum FailOver {
        Port_Level,
        Host_Level
    }

    //HOST Fail-over가 발생하지 않기 위한 최소 connection 갯수
    private int minimumConnection = 1;
    // Failover 정책
    private FailOver policy;

    public ConnectionFailoverPolicy(FailOver policy) {
        this.policy = policy;
    }

    public boolean isPortbasedFailOver() {
        return this.policy == FailOver.Port_Level;
    }
    
    public boolean isHostbasedFailOver() {
    	return this.policy == FailOver.Host_Level;
    }
    

    public int getMinimumConnection() {
        return this.minimumConnection;
    }
}
