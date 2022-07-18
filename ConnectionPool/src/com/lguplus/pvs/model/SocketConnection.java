package com.lguplus.pvs.model;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Objects;

import com.lguplus.pvs.util.LogManager;

public class SocketConnection implements Connectable {
    private Socket connHandle = null;

    public SocketConnection() {}
    
    public Socket getHandle() {
    	return connHandle;
    }
    
    public void setHandle(Socket socket) {
    	connHandle = socket;
    }

    @Override
    public boolean Open(String server, int port, int timeout) throws Exception {
        // TODO Auto-generated method stub
    	try {
			if(connHandle != null) connHandle.close();
    	}catch(Exception e1) {}

    	SocketAddress socketAddress = new InetSocketAddress(server, port);
    	connHandle = new Socket();
    	
    	try {
    		connHandle.connect(socketAddress, timeout);
			return true;
    	} catch (Exception e2) {
    		try {
				connHandle.close();
				connHandle = null;
    		}catch(Exception e3) {}
    		throw new Exception(String.format("%s(%s:%d:%d)", e2.getMessage(), server, port, timeout));
    	}
    }

    @Override
    public void Close() throws Exception {
        // TODO Auto-generated method stub
    	try {
			if(connHandle != null) {
				connHandle.close();
				connHandle = null;
			}
    	}catch(Exception e) {}
    }

    /** Timeout 이 Exception 으로 떨어지는지 확인이 필요함 */
    @Override
    public int Read(byte[] receiveBuffer, int offset, int maxLength, int timeout) throws Exception {
        // TODO Auto-generated method stub
        connHandle.setSoTimeout(timeout);
        return connHandle.getInputStream().read(receiveBuffer, offset, maxLength);
    }

    @Override
    public int ReadN(byte[] receiveBuffer, int offset, int fixLength, int timeout) throws Exception{
        // TODO Auto-generated method stub
        int readn = 0;
        int remain = fixLength;
        if(remain <= 0) return 0;

        connHandle.setSoTimeout(timeout);
        while(remain > 0) {
            int received = connHandle.getInputStream().read(receiveBuffer, offset+readn, remain);
            if(received <= 0) break;
            readn += received;
            remain -= received;
        }
        return readn;
    }

    @Override
    public int Write(byte[] writeBuffer, int offset, int size, int timeout) throws Exception{
        // TODO Auto-generated method stub
        connHandle.setSoTimeout(timeout);
        connHandle.getOutputStream().write(writeBuffer, offset, size);
        return size;
    }

    @Override
    public int hashCode() {
    	if(connHandle == null) return 0;
    	else return Objects.hash(connHandle);
    }
    

    @Override
    public boolean equals(Object o) {
    	if(this == o) return true;
    	
		if(this.connHandle == null || o == null || !(o instanceof Socket)) {
			return false;
		}
		return this.connHandle.equals(o);
    }

    @Override
    public String toString() {
    	if(this.connHandle == null) return "Socket is null";
    	SocketAddress address = connHandle.getRemoteSocketAddress();
    	if(address != null) {
    		return String.format("Socket Server(%s):port(%d)", address.toString(), connHandle.getPort());
    	}else {
    		return String.format("Socket is not connected");
    	}
    }

	@Override
	public String getMode() {
		return ConnectionMode.SOCKET.getMode();
	}
}
