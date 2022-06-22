package com.lguplus.pvs.model;

import java.net.Socket;
import java.net.SocketAddress;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Objects;

import com.lguplus.pvs.util.LogManager;

public class SocketConnection implements Connectable {
    private Socket connHandle = null;

    public SocketConnection() {}

    public SocketConnection(String server, int port) {
        try{
            if(server != null && server.length() > 0){
                Open(server, port);
            }
        }catch(Exception e){
            LogManager.getInstance().info(String.format("Socket open error host(%s) port(%d)", server, port));
            connHandle = null;
        }
    }
    
    public Socket getHandle() {
    	return connHandle;
    }
    
    public void setHandle(Socket socket) {
    	connHandle = socket;
    }

    @Override
    public boolean Open(String server, int port) throws Exception {
        // TODO Auto-generated method stub
    	try {
			if(connHandle != null) connHandle.close();
    	}catch(Exception e) {}

        connHandle = new Socket(server, port);
        return true;
    }

    @Override
    public void Close() throws Exception {
        // TODO Auto-generated method stub
    	try {
			if(connHandle != null) {
				connHandle.close();
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
