package com.lguplus.pvs.model;

import java.util.Arrays;
import java.util.Base64;

public class BWConnection implements Connectable{
    private byte[] connHandle = null;

    public BWConnection() {}

    public BWConnection(byte[] handle) {
    	connHandle = handle;
    }

    public void setHandle(byte[] handle) {
    	connHandle = handle;
    }
    
    public byte[] getHandle() {
    	return this.connHandle;
    }

    @Override
    public boolean Open(String server, int port, int timeout) throws Exception {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void Close() {
        connHandle = null;
    }

    @Override
    public int Read(byte[] receiveBuffer, int offset, int maxLength, int timeout) throws Exception {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int ReadN(byte[] receiveBuffer, int offset, int fixLength, int timeout) throws Exception{
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int Write(byte[] writeBuffer, int offset, int size, int timeout) throws Exception{
        // TODO Auto-generated method stub
        return 0;
    }
    
    @Override
    public int hashCode() {
    	if(connHandle == null) return 0;
    	else return Arrays.hashCode(connHandle);
    }
    
    @Override
    public boolean equals(Object o) {
    	if(this == o) return true;
    	
		if(o == null || !(o instanceof BWConnection)) {
			return false;
		}

		if(this.connHandle == null) {
			return false;
		}

		return Arrays.equals(this.connHandle, ((BWConnection)o).getHandle());
    }
    
    @Override
    public String toString() {
    	if(this.connHandle == null) {
    		return "BWSocket is null";
    	}else {
    		return String.format("BWSocket:%s", Base64.getEncoder().encodeToString(connHandle));
    	}
    }

	@Override
	public String getMode() {
		return ConnectionMode.BW.getMode();
	}
}
