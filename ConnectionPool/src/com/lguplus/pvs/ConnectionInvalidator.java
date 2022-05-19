package com.lguplus.pvs;

import com.lguplus.pool.Pool;
import com.lguplus.pool.Validator;

public class ConnectionInvalidator implements Validator<ConnectionObject> {
    private ConnectionFailoverPolicy failoverPolicy;

    public ConnectionInvalidator(ConnectionFailoverPolicy failoverPolicy) {
        this.failoverPolicy = failoverPolicy;
    }

    public void setFailoverPolicy(ConnectionFailoverPolicy failoverPolicy) {
        this.failoverPolicy = failoverPolicy;
    }

    @Override
    public void invalidate(ConnectionObject connectionObject, Pool<ConnectionObject> pool) {
        if(failoverPolicy.isPortbasedFailOver()) {
            // todo, port fail-over
        }
        else if(pool.getPoolObjectCount() >= failoverPolicy.getMinimumConnection()) {
            // todo, port re-connection
        }
        else {
            // todo, Host fail-over
        }
    }
}
