package com.lguplus.pvs;

import java.util.concurrent.TimeoutException;

// NE Agent 별도 컨네이너를 분리한다는 가정에서 Singleton 객체로 생성함
public class PoolProxy extends BasePoolProxy {

    public PoolProxy() {
    	super("warn");
    }
    
    /* debug : 개발 로그 , info : 기본 출력, warn : warning, error : 에러만 출력 */
    public PoolProxy(String logLevel) {
    	super(logLevel);
    }
    
    // BW에서 연결된 Connection(byte 배열)을 Pool에 저장 처리
    public void addConnectionToPool(String connectionId, byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	addConnectionToPool(connectionId, connectable);
    }

    public String removeFromConnectionPool(byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	return removeFromConnectionPool(connectable);
    }

    public void removeFromConnectionPool(String connectionId, byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	removeFromConnectionPool(connectionId, connectable);
    }

    public void invalidateConnection(byte[] bwConnection, String code, String reason) {
    	if(bwConnection != null) {
		   Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
		   invalidateConnection(connectable, code, reason);
    	}
    }

    public void requestSendEventMessage(String reason, byte[] bwConnection) {    	
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	super.requestSendEventMessage(reason, connectable);
    }

    
    public int[] getReadWriteTimeOutAndRetryCount(byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	return super.getReadWriteTimeOutAndRetryCount(connectable);
    }
    
    
    public void sendConnManagerEvent(byte[] bwConnection, String code, String reason) {
		Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
		super.sendConnManagerEvent(connectable, code, reason);
    }

    public void returnConnection(byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	returnConnection(connectable);
    }

    public void requestReconnect(byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	requestReconnect(connectable);
    }
    
    public String removeFromConnectionConfig(byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	return removeFromConnectionConfig(connectable);
    }

    public void removeFromConnectionConfig(String connectionId, byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	removeFromConnectionConfig(connectionId, connectable);
    }

    public String[] getConnectionId(byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	return getConnectionId(connectable);
    }

    public byte[] getBWConnectionById(String connectionId) {
    	Connectable connectable = super.getConnectionById(connectionId);
    	if(connectable != null && connectable instanceof BWConnection) {
    		return ((BWConnection)connectable).getHandle();
    	}
		LogManager.getInstance().error("borrowConnectionById null");
        return null;
    }
    
    public byte[] getBWConnectionByIndex(int index) {    	
    	Connectable connectable = super.getConnectionByIndex(index);
    	if(connectable != null && connectable instanceof BWConnection) {
    		return ((BWConnection)connectable).getHandle();
    	}
		LogManager.getInstance().error("borrowConnectionByIndex null");
        return null;
    }
    
    public byte[] borrowConnection(String connectionGroupId, long waitTimeSeconds) throws TimeoutException {
    	Connectable connectable = super.borrowConnectable(connectionGroupId, waitTimeSeconds);
    	if(connectable != null && connectable instanceof BWConnection) {
    		return ((BWConnection)connectable).getHandle();
    	}
		LogManager.getInstance().error("borrowConnection null");
        return null;
    }
    
    /*
     * 지정한 connection을 ConnectionManager에서 삭제한다. 
     */
    public byte[] disconnectConnection(String groupId, String connectionKey) {
    	Connectable connectable = super.removeConnection(groupId, connectionKey);
    	if(connectable != null && connectable instanceof BWConnection) return ((BWConnection)connectable).getHandle();
    	return null;
    }

    public void receivedHeartBeat(byte[] bwConnection) {
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	receivedHeartBeat(connectable);
    }
    
}
