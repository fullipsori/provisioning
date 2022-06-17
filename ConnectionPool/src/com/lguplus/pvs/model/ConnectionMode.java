package com.lguplus.pvs.model;

public enum ConnectionMode {
    BW("BW") {
        public Connectable createInstance() {
            return new BWConnection();
        }
    },
    SOCKET("SOCKET") {
        public Connectable createInstance() {
            return new SocketConnection();
        }
    }; 

    public abstract Connectable createInstance();
    final String mode;
    
    private ConnectionMode(String mode) {
    	this.mode = mode;
    }
    
    public String getMode() {
    	return mode;
    }
}
