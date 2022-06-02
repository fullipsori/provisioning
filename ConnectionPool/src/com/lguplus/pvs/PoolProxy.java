package com.lguplus.pvs;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import com.lguplus.pvs.model.BWConnection;
import com.lguplus.pvs.model.Connectable;
import com.lguplus.pvs.util.LogManager;
import com.lguplus.pvs.util.LogManager.LEVEL;

// NE Agent 별도 컨네이너를 분리한다는 가정에서 Singleton 객체로 생성함
public class PoolProxy extends BasePoolProxy {

    public PoolProxy() {
    	super("warn");
    }
    
    /* debug : 개발 로그 , info : 기본 출력, warn : warning, error : 에러만 출력 */
    public PoolProxy(String logLevel) {
    	super(logLevel);
    }
    
    public boolean isPrintable(String printLevel, String level) {
    	return (LEVEL.getLevel(level).intLevel >= LEVEL.getLevel(printLevel).intLevel);
    }

    public boolean isPrintable(String level) {
    	return (LEVEL.getLevel(level).intLevel >= LogManager.getInstance().getLevel().intLevel);
    }
    
    // BW에서 연결된 Connection(byte 배열)을 Pool에 저장 처리
    public void addConnectionToPool(String connectionId, byte[] bwConnection) {
    	if(bwConnection == null) return;
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	addConnectionToPool(connectionId, connectable);
    }

    public String removeFromConnectionPool(byte[] bwConnection) {
    	if(bwConnection == null) return null;

    	Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
    			.map(connObj -> connObj.getConnection())
    			.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
    			.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
    			.findFirst();
    	if(found.isEmpty()) return null;
    	return removeFromConnectionPool(found.get());
    }

    public void removeFromConnectionPool(String connectionId, byte[] bwConnection) {
    	if(bwConnection == null) return;

    	Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
    			.map(connObj -> connObj.getConnection())
    			.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
    			.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
    			.findFirst();
    	if(found.isEmpty()) return ;
    	removeFromConnectionPool(connectionId, found.get());
    }

    public void invalidateConnection(byte[] bwConnection, String code, String reason) {
    	if(bwConnection == null) return;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return ;
		invalidateConnection(found.get(), code, reason);
    }

    public void requestSendEventMessage(String reason, byte[] bwConnection) {    	
    	if(bwConnection == null) return;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return ;
    	super.requestSendEventMessage(reason, found.get());
    }

    
    public int[] getReadWriteTimeOutAndRetryCount(byte[] bwConnection) {
    	if(bwConnection == null) return null;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return null;
    	return super.getReadWriteTimeOutAndRetryCount(found.get());
    }
    
    
    public void sendConnManagerEvent(byte[] bwConnection, String code, String reason) {
    	if(bwConnection == null) return;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return ;
		super.sendConnManagerEvent(found.get(), code, reason);
    }

    public void returnConnection(byte[] bwConnection) {
    	if(bwConnection == null) return;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return ;
    	returnConnection(found.get());
    }

    public void requestReconnect(byte[] bwConnection) {
    	if(bwConnection == null) return;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return ;
    	requestReconnect(found.get());
    }
    
    public String removeFromConnectionConfig(byte[] bwConnection) {
    	if(bwConnection == null) return null;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return null;
    	return removeFromConnectionConfig(found.get());
    }

    public void removeFromConnectionConfig(String connectionId, byte[] bwConnection) {
    	if(bwConnection == null) return;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return ;
    	removeFromConnectionConfig(connectionId, found.get());
    }

    public String[] getConnectionId(byte[] bwConnection) {
    	if(bwConnection == null) return null;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return new String[]{"",""};
    	return getConnectionId(found.get());
    }

    public byte[] getBWConnectionById(String connectionId) {
    	Connectable connectable = super.getConnectionById(connectionId);
    	if(connectable != null && connectable instanceof BWConnection) {
    		return ((BWConnection)connectable).getHandle();
    	}

    	// report to Monitor
    	try {
			String connectionGroupId = connectionId.split(";")[0];
			String connectionKey = connectionId.split(";")[2];

			// ConnectionManager 이벤트 메시지를 발송한다.
			String eventMessage = String.format("%s;%s;ERROR;[%s]로 부터 connection을 가져올 수 없습니다.", connectionGroupId, connectionKey, connectionId);
			Registry.getInstance().addEventSendRequest(eventMessage);
				
    	}catch(Exception e) {
    		// no action
    	}

		LogManager.getInstance().error("borrowConnectionById null");
        return null;
    }
    
    /** BW index 는 1 부터 시작함으로 java 함수를 호출할때는 index-1 값으로 호출하도록 한다. **/
    public byte[] getBWConnectionByIndex(int index) {    	
    	Connectable connectable = super.getConnectionByIndex(index-1);
    	if(connectable != null && connectable instanceof BWConnection) {
    		return ((BWConnection)connectable).getHandle();
    	}
		LogManager.getInstance().error("borrowConnectionByIndex null");
        return null;
    }
    
    public void setConnectionInfoByIndexWithServerType(int index, String serverType) {
    	Connectable connectable = ConnectionConfig.getInstance().setConnectionInfoByIndexWithServerType(index-1, serverType);
    	if(connectable != null) {
    		this.requestReconnect(connectable);
    	} else {
    		LogManager.getInstance().info(String.format("PoolProxy:setConnectionInfoByIndexWithServerType[%d][%s]\n", index, serverType));
    	}
    }
    /** BW index problem **/
    
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
    	/* 먼저 handle 값을 가져온다. */
    	Connectable connectable = super.removeConnection(groupId, connectionKey);
    	if(connectable != null && connectable instanceof BWConnection) {
    		return ((BWConnection)connectable).getHandle();
    	}
    	return null;
    }

    public void receivedHeartBeat(byte[] bwConnection) {
    	if(bwConnection == null) return;

		Optional<Connectable> found = ConnectionConfig.getInstance().getConnections().values().stream()
				.map(connObj -> connObj.getConnection())
				.filter(connectable -> (connectable != null) && (connectable instanceof BWConnection))
				.filter(connectable -> Arrays.equals(((BWConnection)connectable).getHandle(),bwConnection))
				.findFirst();
		if(found.isEmpty()) return ;
    	receivedHeartBeat(found.get());
    }
    
    public byte[] returnNull() {
    	return null;
    }
    
}
