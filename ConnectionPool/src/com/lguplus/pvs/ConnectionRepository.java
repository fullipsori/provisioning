package com.lguplus.pvs;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Optional;

import com.lguplus.pvs.model.BWConnection;
import com.lguplus.pvs.model.Connectable;
import com.lguplus.pvs.model.ConnectionMode;
import com.lguplus.pvs.model.SocketConnection;
import com.lguplus.pvs.util.LogManager;

public class ConnectionRepository {
    private ConnectionRepository() {}

    private static ConnectionRepository instance = new ConnectionRepository();
    public static ConnectionRepository getInstance() { 
    	return instance; 
    }

    private Connectable createConnectable(ConnectionMode mode) {
        return mode.createInstance();
    }

    public Connectable requestConnectable(byte[] handle) {
        Connectable connectable = createConnectable(ConnectionMode.BW);
        if(connectable != null) {
            ((BWConnection)connectable).setHandle(handle);
        }
        return connectable;
    }
    
    public Connectable requestConnectable(Socket socket) {
        Connectable connectable = createConnectable(ConnectionMode.SOCKET);
        if(connectable != null) {
            ((SocketConnection)connectable).setHandle(socket);
        }
        return connectable;
    }
    
    public Connectable requestConnectable(String server, int port) throws Exception {
        Connectable connectable = createConnectable(ConnectionMode.SOCKET);
        if(server != null) {
            connectable.Open(server, port);
        }
        return connectable;
    }

    public void closeConnectable(Connectable connectable) {
        try{
            if(connectable != null) connectable.Close();
        }catch(Exception e) {
            LogManager.getInstance().error("Ignored : Exception:" + e.getMessage());
            return;
        }
    }

    public boolean openConnection(Connectable connectable, String server, int port) throws Exception {
        if(connectable == null || !(connectable instanceof SocketConnection)) return false;
        return connectable.Open(server, port);
    }
    
    public Connectable GetConnectable(ArrayList<Connectable> connections, Connectable handle) throws Exception {
    	if(connections == null) return null;
        Optional<Connectable> result = connections.stream().filter(conn -> conn.equals(handle)).findFirst();
        if(result.isPresent()) return result.get();
        return null;
    }

    public Connectable GetConnectionByHandle(ArrayList<Connectable> connections, byte[] handle) throws Exception {
    	return GetConnectable(connections, requestConnectable(handle));
    }

    public Connectable GetConnectionByHandle(ArrayList<Connectable> connections, Socket handle) throws Exception {
    	return GetConnectable(connections, requestConnectable(handle));
    }
}
