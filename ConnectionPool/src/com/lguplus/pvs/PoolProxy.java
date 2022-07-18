package com.lguplus.pvs;

import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import com.lguplus.pvs.model.BWConnection;
import com.lguplus.pvs.model.Connectable;
import com.lguplus.pvs.model.ConnectionMode;
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
    
    public String getConnectionIdFromGroupInfo(String groupId, String key) {
    	if(groupId == null || groupId.isEmpty() || key == null || key.isEmpty())
    		return "";
		return ConnectionObject.getConnectionIdFromGroupInfo(groupId, key);
    }

    public void invalidateConnection(byte[] bwConnection, String code, String reason) {
    	if(bwConnection == null) return;

    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	Optional<String> connectionId = ConnectionConfig.getInstance().getConnections().entrySet().stream()
    					.filter(conn-> (conn.getValue() != null))
    					.filter(conn-> connectable.equals(conn.getValue().getConnection()))
    					.map(conn->conn.getKey())
    					.findFirst();
    	if(connectionId.isPresent()) {
			super.invalidateConnection(connectionId.get(), code, reason);
    	}
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

    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(bwConnection);
    	String connectionId = ConnectionConfig.getInstance().getConnectionIdByConnectable(connectable);
		requestReconnect(connectionId);
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
    	if(connectionId == null || connectionId.isEmpty()) return null;
    	
    	// report to Monitor
    	try {
			Connectable connectable = super.getConnectionById(connectionId);
			if(connectable != null && connectable instanceof BWConnection) {
				return ((BWConnection)connectable).getHandle();
			}

			String connectionGroupId = ConnectionObject.getGroupIdFromConnectionId(connectionId);
			String connectionKey = ConnectionObject.getKeyFromConnectionId(connectionId);

			// ConnectionManager 이벤트 메시지를 발송한다.
			String eventMessage = String.format("%s;%s;ERROR;[%s]로 부터 connection을 가져올 수 없습니다.", connectionGroupId, connectionKey, connectionId);
			Registry.getInstance().addEventSendRequest(eventMessage);
				
    	}catch(Exception e) {
    		// no action
			LogManager.getInstance().error("getBWConnectionById Exception:" + e.getMessage());
    	}

        return null;
    }
    
    public void setConnectionInfoByIndexWithServerType(int index, String serverType) throws Exception {
    	if(index <= 0) throw new IndexOutOfBoundsException(index);

    	String connectionId = ConnectionConfig.getInstance().setConnectionInfoByIndexWithServerType(index-1, serverType);
    	requestReconnect(connectionId);
    }
    
    public byte[] borrowConnection(String connectionGroupId, long waitTimeSeconds) {
    	try {
			if(connectionGroupId == null || connectionGroupId.isEmpty()) return null;
			Connectable connectable = super.borrowConnectable(connectionGroupId, waitTimeSeconds);
			if(connectable != null && connectable instanceof BWConnection) {
				return ((BWConnection)connectable).getHandle();
			}
			LogManager.getInstance().error("borrowConnection error groupId: " + connectionGroupId);
			return null;
    	}catch(Exception e) {
			LogManager.getInstance().error(String.format("borrowConnection Exception[%s]: error groupId:[%s] ", e.getMessage(), connectionGroupId));
			return null;
    	}
    }
    
    /*
     * 지정한 connection을 ConnectionManager에서 삭제한다. 
     */
    public byte[] disconnectConnection(String connectionId) {
    	if(connectionId == null || connectionId.isEmpty()) return null;

    	/* 먼저 handle 값을 가져온다. */
    	Connectable connectable = super.removeConnection(connectionId);
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
    
    /**** for socket mode *****/
    // BW에서 연결된 Connection(byte 배열)을 Pool에 저장 처리
    public boolean OpenSocketConnection(String connectionId, String server, int port) throws Exception {
		LogManager.getInstance().info("OpenSocketConnection request:" + connectionId + " server:" + server + " port:" + port);
    	if(server==null || server.isEmpty() || port <= 0) {
    		throw new Exception("Parameter Error");
    	}
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(server, port, 0);
    	if(connectable == null) {
			LogManager.getInstance().error("OpenConnection failed:" + connectionId);
    		throw new Exception("requestConnectable Error");
    	}

		//socket 모드에서는 BW handle 이 없기 때문에 connectable 을 pool에 저장해 놓아야 한다.
		try {
			addConnectionToPool(connectionId, connectable);
		}catch(Exception e) {
			connectable.Close();
			throw new Exception(String.format("%s(%s,%s,%d)", e.getMessage(), connectionId, server, port));
		}
    	return true;
    }

    public boolean OpenSocketConnection(String connectionId, String server, int port, int timeout) throws Exception {
		LogManager.getInstance().info("OpenSocketConnection request:" + connectionId + " server:" + server + " port:" + port);
    	if(server==null || server.isEmpty() || port <= 0) {
    		throw new Exception("Parameter Error");
    	}
    	Connectable connectable = ConnectionRepository.getInstance().requestConnectable(server, port, timeout);
    	if(connectable == null) {
			LogManager.getInstance().error("OpenConnection failed:" + connectionId);
    		throw new Exception("requestConnectable Error");
    	}

		//socket 모드에서는 BW handle 이 없기 때문에 connectable 을 pool에 저장해 놓아야 한다.
		try {
			addConnectionToPool(connectionId, connectable);
		}catch(Exception e) {
			connectable.Close();
			throw new Exception(String.format("%s(%s,%s,%d,%d)", e.getMessage(), connectionId, server, port, timeout));
		}
    	return true;
    }

    public boolean CloseSocketConnection(String connectionId) throws Exception {
		LogManager.getInstance().info("CloseSocketConnection:" + connectionId);

    	if(connectionId == null || connectionId.isEmpty()) return false;

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null) return true;
    	if(connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("CloseSocketConnection not socket type:" + connectionId);
    		return false;
    	}
    	Connectable found = connObj.getConnection();
    	ConnectionRepository.getInstance().closeConnectable(found);
    	return true;
    }

    public String ReadSocket(String connectionId, int maxLength, int timeout) throws Exception {
    	if(connectionId == null || connectionId.isEmpty() || maxLength <= 0) {
    		LogManager.getInstance().error(String.format("ReadSocket parameter error [%s] [%d] [%d]", connectionId, maxLength, timeout));
    		return "";
    	}

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null || connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("ConnectionObject Error" + connectionId);
			return "";
    	}

    	byte[] receivedBuffer = new byte[maxLength];
    	Connectable found = connObj.getConnection();
    	int count = found.Read(receivedBuffer, 0, maxLength, timeout);
    	if(count > 0) {
			return new String(receivedBuffer, 0, count);
    	}else {
    		return "";
    	}
    }

	public String ReadNSocket(String connectionId, int fixLength, int timeout) throws Exception {
    	if(connectionId == null || connectionId.isEmpty() || fixLength <= 0) {
    		LogManager.getInstance().error(String.format("ReadNSocket parameter error [%s] [%d] [%d]", connectionId, fixLength, timeout));
    		return "";
    	}

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null || connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("ConnectionObject Error" + connectionId);
			return "";
    	}

    	byte[] receivedBuffer = new byte[fixLength];
    	Connectable found = connObj.getConnection();
    	int count = found.ReadN(receivedBuffer, 0, fixLength, timeout);
    	if(count > 0) {
			return new String(receivedBuffer, 0, count);
    	}else {
    		return "";
    	}
    }

	public int WriteSocket(String connectionId, String writeBuffer, int size, int timeout) throws Exception {
		if(connectionId == null || connectionId.isEmpty() || writeBuffer == null || writeBuffer.isEmpty()) {
    		LogManager.getInstance().error(String.format("WriteSocket parameter error [%s] [%s] [%d] [%d]", connectionId, writeBuffer, size, timeout));
			return 0;
		}

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null || connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("ConnectionObject Error" + connectionId);
			return 0;
    	}
    	int rsize = writeBuffer.length();
    	if(rsize <= 0) {
			LogManager.getInstance().warn("WriteSocket data is null");
			return 0;
    	}
    	int slength;
    	if(size == 0) slength = rsize;
    	else {
    		slength = (size <= rsize)? size : rsize;
    	}

    	Connectable found = connObj.getConnection();
    	return found.Write(writeBuffer.getBytes(), 0, slength, timeout);
    }

	/** for binary data transmission **/ 
	/**
	 * output : tib:base64-to-string
	 */
    public String ReadBase64(String connectionId, int maxLength, int timeout) throws Exception {
    	if(connectionId == null || connectionId.isEmpty() || maxLength <= 0) {
    		LogManager.getInstance().error(String.format("ReadBase64 parameter error [%s] [%d] [%d]", connectionId, maxLength, timeout));
    		return "";
    	}
    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null || connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("ConnectionObject Error" + connectionId);
			return "";
    	}

    	byte[] receivedBuffer = new byte[maxLength];
    	Connectable found = connObj.getConnection();
    	int count = found.Read(receivedBuffer, 0, maxLength, timeout);
    	if(count > 0) {
    		byte[] countBuffer = new byte[count];
    		System.arraycopy(receivedBuffer, 0, countBuffer, 0, count);
			return Base64.getEncoder().encodeToString(countBuffer);
    	}else {
    		return "";
    	}
    }

	/**
	 * output : tib:base64-to-string
	 */
	public String ReadNBase64(String connectionId, int fixLength, int timeout) throws Exception {
    	if(connectionId == null || connectionId.isEmpty() || fixLength <= 0) {
    		LogManager.getInstance().error(String.format("ReadNBase64 parameter error [%s] [%d] [%d]", connectionId, fixLength, timeout));
    		return "";
    	}
    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null || connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("ConnectionObject Error" + connectionId);
			return "";
    	}
    	byte[] receivedBuffer = new byte[fixLength];
    	Connectable found = connObj.getConnection();
    	int count = found.ReadN(receivedBuffer, 0, fixLength, timeout);
    	if(count > 0) {
    		byte[] countBuffer = new byte[count];
    		System.arraycopy(receivedBuffer, 0, countBuffer, 0, count);
			return Base64.getEncoder().encodeToString(countBuffer);
    	}else {
    		return "";
    	}
    }

	/**
	 * input : tib:string-to-base64
	 */
	public int WriteBase64(String connectionId, String base64Encoded, int size, int timeout) throws Exception {
    	if(connectionId == null || connectionId.isEmpty() || base64Encoded == null || base64Encoded.isEmpty()) {
    		LogManager.getInstance().error(String.format("WriteBase64 parameter error [%s] [%s] [%d] [%d]", connectionId, base64Encoded, size, timeout));
    		return 0;
    	}
		byte[] writeBuffer = Base64.getDecoder().decode(base64Encoded);
		int rsize = writeBuffer.length;

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null || connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("ConnectionObject Error" + connectionId);
			return 0;
    	}

    	int slength;
    	if(size == 0) slength = rsize;
    	else {
    		slength = (size <= rsize)? size : rsize;
    	}
    	Connectable found = connObj.getConnection();
    	return found.Write(writeBuffer, 0, slength, timeout);
    }

	/** support charset encoding **/
    public String ReadSocket(String connectionId, int maxLength, int timeout, String charsetName) throws Exception {
    	if(connectionId == null || connectionId.isEmpty() || maxLength <= 0) {
    		LogManager.getInstance().error(String.format("ReadSocket parameter error [%s] [%d] [%d]", connectionId, maxLength, timeout));
    		return "";
    	}

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null || connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("ConnectionObject Error" + connectionId);
			return "";
    	}

    	byte[] receivedBuffer = new byte[maxLength];
    	Connectable found = connObj.getConnection();
    	int count = found.Read(receivedBuffer, 0, maxLength, timeout);
    	String charset = (charsetName == null || charsetName.isEmpty())? "UTF-8" : charsetName.toUpperCase();
    	if(count > 0) {
			return new String(receivedBuffer, 0, count, charset);
    	}else {
    		return "";
    	}
    }

	public String ReadNSocket(String connectionId, int fixLength, int timeout, String charsetName) throws Exception {
    	if(connectionId == null || connectionId.isEmpty() || fixLength <= 0) {
    		LogManager.getInstance().error(String.format("ReadNSocket parameter error [%s] [%d] [%d]", connectionId, fixLength, timeout));
    		return "";
    	}

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null || connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("ConnectionObject Error" + connectionId);
			return "";
    	}

    	byte[] receivedBuffer = new byte[fixLength];
    	Connectable found = connObj.getConnection();
    	int count = found.ReadN(receivedBuffer, 0, fixLength, timeout);
    	String charset = (charsetName == null || charsetName.isEmpty())? "UTF-8" : charsetName.toUpperCase();
    	if(count > 0) {
			return new String(receivedBuffer, 0, count, charset);
    	}else {
    		return "";
    	}
    }
	
	public int WriteSocket(String connectionId, String writeBuffer, int size, int timeout, String charsetName) throws Exception {
		if(connectionId == null || connectionId.isEmpty() || writeBuffer == null || writeBuffer.isEmpty()) {
    		LogManager.getInstance().error(String.format("WriteSocket parameter error [%s] [%s] [%d] [%d]", connectionId, writeBuffer, size, timeout));
			return 0;
		}

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null || connObj.getConnectionMode() != ConnectionMode.SOCKET) {
			LogManager.getInstance().error("ConnectionObject Error" + connectionId);
			return 0;
    	}
    	int rsize = writeBuffer.length();
    	if(rsize <= 0) {
			LogManager.getInstance().warn("WriteSocket data is null");
			return 0;
    	}
    	int slength;
    	if(size == 0) slength = rsize;
    	else {
    		slength = (size <= rsize)? size : rsize;
    	}

    	Connectable found = connObj.getConnection();
    	String charset = (charsetName == null || charsetName.isEmpty())? "UTF-8" : charsetName.toUpperCase();
    	return found.Write(writeBuffer.getBytes(charset), 0, slength, timeout);
    }
	
    public String getConnectionIdByIndex(int index) throws Exception {    	
    	if(index <= 0) throw new IndexOutOfBoundsException(index);
    	Connectable connectable = super.getConnectionByIndex(index-1);
    	if(connectable != null) {
			return ConnectionConfig.getInstance().getConnectionIdByConnectable(connectable);
    	}else {
    		return null;
    	}
    }
    
    public String getConnectionMode(String connectionId) throws Exception {
    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null) throw new Exception("Not Founded ConnectionObject:" + connectionId);
    	
    	return connObj.getConnectionMode().getMode();
    }

    public void invalidateConnection(String connectionId, String code, String reason) {
    	super.invalidateConnection(connectionId, code, reason);
    }

    public void requestSendEventMessage(String reason, String connectionId) {    	
    	if(connectionId == null || connectionId.isEmpty()) return;
    	
    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null) return;
    	Connectable found = connObj.getConnection();
    	if(found == null) return;
    	super.requestSendEventMessage(reason, found);
    }

    
    public int[] getReadWriteTimeOutAndRetryCount(String connectionId) {
    	if(connectionId == null || connectionId.isEmpty()) return new int[]{30000,3,30000,3};

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null) return new int[]{30000,3,30000,3};
    	Connectable found = connObj.getConnection();
    	return super.getReadWriteTimeOutAndRetryCount(found);
    }
    

    public void returnConnection(String connectionId) {
    	if(connectionId == null || connectionId.isEmpty()) return;

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null) return;
    	Connectable found = connObj.getConnection();
    	if(found == null) return;
    	returnConnection(found);
    }

    public void requestReconnect(String connectionId) {
    	super.requestReconnect(connectionId);
    }
    
    public String[] getConnectionId(String connectionId) {
    	if(connectionId == null || connectionId.isEmpty()) return new String[] {"",""};

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null) return new String[] {"",""};
    	Connectable found = connObj.getConnection();
    	if(found == null) return new String[] {"",""};
    	return getConnectionId(found);
    }

    
    public String borrowSocketConnection(String connectionGroupId, long waitTimeSeconds) {
    	try {
			if(connectionGroupId == null || connectionGroupId.isEmpty()) return null;
			
			Connectable connectable = super.borrowConnectable(connectionGroupId, waitTimeSeconds);
			if(connectable == null) return null;
			String connectionId = ConnectionConfig.getInstance().getConnectionIdByConnectable(connectable);
			if(connectionId != null) return connectionId;
			LogManager.getInstance().error("borrowSocketConnection null");
			return null;
    	}catch(Exception e) {
			LogManager.getInstance().error(String.format("borrowConnection Exception[%s]: error groupId:[%s] ", e.getMessage(), connectionGroupId));
			return null;
    	}
    }
    
    public void receivedHeartBeat(String connectionId) {
    	if(connectionId == null || connectionId.isEmpty()) return;

    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null) return;
    	Connectable found = connObj.getConnection();
    	if(found == null) return;
    	receivedHeartBeat(found);
    }

    public void addOpeningVector(String connectionId) {
    	if(Registry.getInstance().addOpening(connectionId)) {
			//LogManager.getInstance().warn("addOpeningVector:" + connectionId);
    	}
    }

    public void removeOpeningVector(String connectionId) {
    	if(Registry.getInstance().removeOpening(connectionId)) {
			//LogManager.getInstance().warn("removeOpeningVector:" + connectionId);
    	}
    }
}
