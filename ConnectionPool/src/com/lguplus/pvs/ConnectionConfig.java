package com.lguplus.pvs;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.lguplus.pool.Pool;
import com.lguplus.pvs.model.Connectable;
import com.lguplus.pvs.util.LogManager;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;
import java.io.StringReader;
// import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionConfig {

    private static ConnectionConfig ourInstance = new ConnectionConfig();

    public static ConnectionConfig getInstance() {
        return ourInstance;
    }

    private String NEA_PODNAME = null;
    private HeartbeatPolicy heartbeatPolicy = null;
    private ConnectionFailoverPolicy failoverPolicy = null;
    private List<String> connectionGroupList = new ArrayList<>();
    
    // 호스트 단위 연결을 위하여 추가적인 정보 설정 - 별도의 명령어가 내려오기전까지는 단일 서버에 접속이 될 때까지 시도한다.
    private String autoFailOverYN = "Y";
    private String connectionFailOverMode = "A"; // A(Auto), M(anul)
    private String connectionServerType = "A"; // A(ctive), B(ackup), D(isaster recovery) <= 최초 시작시 Active 부터 시작한다.
    private String connectionStatus = "D"; // D(isconnected), U(pdate) 정보 변경 중, R(eady to connect), C(onnected more than one connections) - 타켓서버 정보를 설정한다. (나중에 사용할 수 있다).
    private boolean connectionFirstTime = true; // 첫 연결 시도인 경우 - 모두 끊어져 있던 상태에서 시작되기 때문에 구분해준다.

    // 재연결 시도 주기(단위=초) - 
    private int reconnectionTryIntervalSec = 10;
    private int borrowTimeOut = 5*1000; 
    private int borrowTimeOutRetryCount = 3;
    private int readTimeOut = 5*1000; 
    private int readTimeOutRetryCount = 3;
    private int writeTimeOut = 5*1000; 
    private int writeTimeOutRetryCount = 3;
    

    // Map< ConnectionGroupName, Pool<ConnecitonObject> > Connection Group별 connection pool을 갖는다. 
    private Map<String, Pool<ConnectionObject>> poolMap = new HashMap<>();

    // 모든 설정된 Connection, KEY=컨넥션ID, Value=컨넥션객체
    private ConcurrentHashMap<String, ConnectionObject> connections = new ConcurrentHashMap<>();
    
    private final LogManager logManager;
    
    private ConnectionConfig() {
    	logManager = LogManager.getInstance();
    }
    
    private int stringToInteger(String value) {
    	try {
	    	if(value != "" || (value != null && !value.equalsIgnoreCase("NULL"))) {
	    		return Integer.parseInt(value);
	    	} else {
	    		return -1;
	    	}
    	}catch(Exception ex) {
    		logManager.error(String.format("Unexpected value - please check the DB Value [%s]\n", value));
    		return -1;
    	}
    }
    
    private long stringToLong(String value) {
    	try {
	    	if(value != "" || (value != null && !value.equalsIgnoreCase("NULL"))) {
	    		return Long.parseLong(value);
	    	} else {
	    		return -1;
	    	}
    	}catch(Exception ex) {
    		logManager.error(String.format("Unexpected value - please check the DB Value [%s]\n", value));
    		return -1;
    	}
    }
    
    // 정보 보여기 위하여 사용하는 것
    public String displayServerType(String serverType) {
    	return ConnectionObject.getServerType(serverType);
    }
    
    public String displayFailOverMode(String failoverMode) {    	
    	return ConnectionObject.getFailOverMode(failoverMode);
    }
    
    public void displayConnectionObjectInfo(Connectable connectable) {
    	if(connectable != null && this.getConnectionIdByConnectable(connectable) != null) {
    		String objLog = this.getConnectionObjectByConnectable(connectable).logConnectionObjectInfo();
    		logManager.info(objLog);
    	}
    }
    
    public void displayConnectionObjectInfo(String connectionId) {
    	
	}
    
    public void displayConnectionObjectInfo() {   	    	
    	int idx = 0;    	
    	for(ConnectionObject connObj : this.connections.values()) {
		   logManager.info(String.format("[%d][%s][%s][%s][객체상태: %s] 현재상태 [%s] 서버 주소/포트 [%s][%d][접속서버유형: %s][연결시도횟수: %d회][처리 메시지: %d개]\n", 
				idx++, connObj.getConnectionType(), connObj.getConnectionGroupId(), connObj.getConnectionKey(), 
				connObj.getConnectionObjectStatus(), connObj.getConnectionStatus(), connObj.getCurrentServerIp(), 
				connObj.getCurrentServerPort(), connObj.getCurrentServerType(), connObj.getFailoverTryCount(), connObj.getHandledMsgCount()));
    	}
    	logManager.info("===========================================================================================================");
    }
    
    public String getAutoFailOverYN() { return this.autoFailOverYN; }  

    public ConnectionObject getConnectionObjectFromPool(String connectionGroupId, String connectionId) {
    	if(this.poolMap.containsKey(connectionGroupId)) {
			return this.poolMap.get(connectionGroupId).getObjectById(connectionId);
    	}else {
			logManager.warn(String.format("connectionGroupId error:", connectionGroupId));
    		return null;
    	}
    }
    
    /** connectable 로 부터 ConnectionObject 를 구한다. **/
    public ConnectionObject getConnectionObjectByConnectable(Connectable connectable) {    	
		Optional<ConnectionObject> connectionObject = this.connections.values().stream().filter(conn -> conn.getConnection().equals(connectable)).findFirst();
		if(connectionObject.isPresent()) connectionObject.get();
    	return null;
    }
    
    
    public String getConnectionInfo(String connectionGroupId, String connectionKey, String code, String reason) {
    	
    	boolean bSkip = false;
    	int idx = 0;
    	int numOfConns = 0;
    	int numOfDisconns = 0;
    	StringBuffer connInfo = new StringBuffer();
    	
    	Date now = new Date();   
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");  
        String evenTime = dateFormat.format(now);
    	
    	connInfo.append(String.format("{\"EVENTTIME\": \"%s\",", evenTime));    	
    	connInfo.append(String.format(" \"NUM_OF_CONNS\": @NUM_OF_CONNS@,")); // 전체 연결객체 수를 파악한다.
    	connInfo.append(String.format(" \"NUM_OF_DISCONNS\": @NUM_OF_DISCONNS@,"));	// 연결객체 중 연결이 되어있지 않은 것에 객체의 수를 알려준다. 
    	connInfo.append(String.format(" \"NEA_PODNAME\": \"%s\",", this.NEA_PODNAME));
    	connInfo.append(" \"CONNECTIONS\": [");
    	
    	for(String connectionId : this.connections.keySet()) {
    		ConnectionObject connObj = this.connections.get(connectionId);
    		numOfConns++;
    		if(connObj.getConnectionStatus() == false) {
    			numOfDisconns++;
    		}
    		
    		// groupID가 ALL인 경우 모든 connection 객체 값을 가져온다. ALL이 아니면서 
    		 if(connectionGroupId.equalsIgnoreCase("ALL") || 
    		   (connectionGroupId.equalsIgnoreCase(connObj.getConnectionGroupId()) && connectionKey.equalsIgnoreCase(connObj.getConnectionKey()) ||
    		   (connectionGroupId.equalsIgnoreCase(connObj.getConnectionGroupId())) && connectionKey.equalsIgnoreCase("ALL"))) {    			
    			idx++;
    			
    			// 정보 로그
    			connObj.logConnectionObjectInfo();
    		
	    		if(idx!=1) { // 첫번째만 스킵하고, 나머지 연결시 앞부분에 ,를 넣어준다.
	    			connInfo.append(",");
	    		}
	    		
	    		String connObjInfo = connObj.getConnectionObjectInfo();
	    		if(connObj.getConnectionCount() == 1) {
	    			bSkip = true;
	    			int connRealCount = connObj.getConnectionStatus() ? 1 : 0;  
	    			connObjInfo = connObjInfo.replace("@CONN_REAL_COUNT@", String.format("%d", connRealCount));
	    		}
	    		
	    		connInfo.append(connObjInfo);	
    		}
    	}    	
    	connInfo.append("]}");
    	
    	String result = connInfo.toString();
    	result = result.replace("@NUM_OF_CONNS@", String.format("%d", numOfConns));
    	result = result.replace("@NUM_OF_DISCONNS@", String.format("%d", numOfDisconns));
    	result = result.replace("@CODE@", code);
    	result = result.replace("@REASON@", reason);
    	
    	if(bSkip == false) {
    		// 연결객체수가 복수개인 경우 - 1개인 경우 for loop 내에서 개별적으로 처리함 
    		result = result.replace("@CONN_REAL_COUNT@", String.format("%d", numOfConns-numOfDisconns));
    	}
    	
    	logManager.info(String.format("/// [현재 총 연결객체 수: %d개 / 총 연결객체 정보: %d개]\n", (numOfConns-numOfDisconns), numOfConns));
    	
    	return result;
    }
    
    public int[] getBorrowTimeOutAndRetryCount() {
    	int[] retVal = {this.borrowTimeOut, this.borrowTimeOutRetryCount, this.readTimeOut, this.readTimeOutRetryCount, this.writeTimeOut, this.writeTimeOutRetryCount};
    	return retVal;
    }
    
    public int[] getReadWriteTimeOutAndRetryCount(Connectable connectable) {
    	// ENUM default 값 설정으로 간다.
    	int[] retVal = {30000, 3, 30000, 3}; // 30초 3번을 기본값으로 설정해준다. 
    	ConnectionObject connObj = this.getConnectionObjectByConnectable(connectable);
    	
    	if(connObj != null) {
    		retVal[0] = connObj.getReadTimeOut();
    		retVal[1] = connObj.getReadTimeOutRetryCount();
    		retVal[2] = connObj.getWriteTimeOut();
    		retVal[3] = connObj.getWriteTimeOutRetryCount();
    		logManager.info(String.format("[ReadTimeOut: %d초, RetryCount: %d회][WriteTimeOut: %d초, RetryCount: %d회]\n", retVal[0], retVal[1], retVal[2], retVal[3]));
    	}
    		 
    	return retVal;
    }
    
    
    public Connectable setConnectionInfoByIndexWithServerType(int index, String serverType) {
    	int position = 0;
    	Connectable connectable = null;
    	
    	for(String connectionId : this.connections.keySet()) {
    		if(index == position) {
    			ConnectionObject connObj = this.connections.get(connectionId);
    			connectable = connObj.getConnection(); // BW Connection Binary 값을 가져온다.
    			connObj.resetByServerType(serverType);
    			
    			logManager.info(String.format("[%d][요청-인덱스:%d][%s][%s] 현재 접속 상태 [%s][주소/포트: %s][%d][접속서버유형: %s][처리 메시지: %d개]\n", position, index,
    									connObj.getConnectionGroupId(), connObj.getConnectionKey(), connObj.getConnectionStatus(), connObj.getCurrentServerIp(),
    									connObj.getCurrentServerPort(), connObj.getCurrentServerType(), connObj.getHandledMsgCount()));
    			break;
    		}else {
    			position++;
    		}
    	}    	
    	return connectable;
    }

    public Connectable getConnectionByIndex(int index) {    	

    	// 순차적으로 현재 연결된 모든 연결객체를 강제 종료하기 위하여 사용한다.    	
    	Connectable connectable = null;
    	int position = 0;
    	
    	for(String connectionId : this.connections.keySet()) {
    		ConnectionObject connObj = this.connections.get(connectionId);
    		if(index == position) {
    			if(connObj.getConnectionType().equalsIgnoreCase("POOL")) {
	    			logManager.info(String.format("[%d][req-index:%d][%s][%s] 현재 접속 상태 [%s][주소/포트: %s][%d][접속서버유형: %s][처리 메시지: %d개][연결객체 유형: %s]\n", position, index,
							connObj.getConnectionGroupId(), connObj.getConnectionKey(), connObj.getConnectionStatus(), connObj.getCurrentServerIp(),
							connObj.getCurrentServerPort(), connObj.getCurrentServerType(), connObj.getHandledMsgCount(), connObj.getConnectionType()));
	    			
	    			// 연결객체 상태가 true: 접속 상태인 경우만 종료 대상이다.
	    			if(connObj.getConnectionStatus()) {
	    				connectable = connObj.getConnection();
	    			}
    			} else {
    				logManager.info("///////////////////////////////////////////////////////////////");
    				logManager.info(String.format("[%d][req-index:%d][%s][%s] 현재 접속 상태 [%s][주소/포트: %s][%d][접속서버유형: %s][처리 메시지: %d개][연결객체 유형: %s]\n", position, index,
							connObj.getConnectionGroupId(), connObj.getConnectionKey(), connObj.getConnectionStatus(), connObj.getCurrentServerIp(),
							connObj.getCurrentServerPort(), connObj.getCurrentServerType(), connObj.getHandledMsgCount(), connObj.getConnectionType()));
    				logManager.info("///////////////////////////////////////////////////////////////");    				
    			}
    			break;
    		}else {
				position++;
    		}
    	}
    	return connectable;
    }

    public String addNewConnection(String connectionGroupId, String connectionKey, String serverIPsAndServerPorts) {    	
    	String connectionId= String.format(ConnectionObject.CID_FORMAT, connectionGroupId, connectionKey);
    	String serverType = "";
    	String serverIp = ""; 
    	int serverPort = 0;
    	long neConnId = -1;
    	
    	String[] connectionList = serverIPsAndServerPorts.split(";");
    	
    	if(this.connections.containsKey(connectionId)) {
    		String retMsg = String.format("[ERROR] 이미 존재하는 연결객체 입니다. - AddNewConnection [%s][%s][%s]", connectionGroupId, connectionKey, serverIPsAndServerPorts);
    		logManager.info(retMsg);
    		return retMsg;
    	} else {    		
    		logManager.info("연결객체의 서버유형은 총 "+connectionList.length+"개 입니다.");
    		
    		// connectionObject의 키 값으로는 다음과 같다.
			ConnectionObject connObj = new ConnectionObject(connectionGroupId, connectionKey);
			
			if(failoverPolicy.isPortbasedFailOver()) {
				connObj.setFailOverPolicy("Port");
			} else {
				connObj.setFailOverPolicy("Host");
			}
    		
    		for(int i=0; i < connectionList.length; i++) {
    			
    			serverType = connectionList[i].split(",")[0];
    			serverIp = connectionList[i].split(",")[1];
    			serverPort = Integer.parseInt(connectionList[i].split(",")[2]);
    			neConnId = Long.parseLong(connectionList[i].split(",")[3]);
    			
    			// 기본값들을 설정해준다.
    			if(serverType.equalsIgnoreCase("Active")) {
	                // active server
    				connObj.setActiveIp(serverIp);
    				connObj.setActivePort(serverPort);
	            	
	            	// 한번만 수행하면 된다. - 첫번째 접속은 무조건 Active 서버로 접속하도록 설정해준다.
    				connObj.setCurrentServerIp(connObj.getActiveIp());		
    				connObj.setCurrentServerPort(connObj.getActivePort());
    				connObj.setCurrentServerType("A"); // A(ctive), B(ackup), D(isaster Recovery)
    				connObj.setConnectionFirstTime(true);
    				connObj.setNEConnId(neConnId);
	                
	            } else if (serverType.equalsIgnoreCase("Backup")) {
		            // backup server
	            	connObj.setBackupIp(serverIp);
	            	connObj.setBackupPort(serverPort);
	            } else if (serverType.equalsIgnoreCase("DR")) {
		            // DR server
	            	connObj.setDrIp(serverIp);
	            	connObj.setDrPort(serverPort);
	            }
    			logManager.info(String.format("신규 연결객체 [%s] 를 생성하겠습니다. - AddNewConnection [%s][%s][%s][%d][NEConnecion Id: %d]\n", serverType, connectionGroupId, connectionKey, serverIp, serverPort, neConnId));
    			
    		}
			// connections 에 넣어준다 생성된 객체를 넣어준다.
    		connObj.setLastUsedTime(System.currentTimeMillis());
			this.connections.put(connObj.getConnectionId(), connObj);
			
    		return connectionId;
    	}
    }
    
    public void setConnectionFailOverMode(String failOverMode) {
    	connectionFailOverMode = failOverMode;
    	// 모든 connectionObjce의 연결 모드도 자동에서 수동으로 변환해준다.
    	for(String connectionId : this.connections.keySet()) {
    		ConnectionObject connObj = this.connections.get(connectionId);
    		this.connections.get(connectionId).setFailOverMode(failOverMode);
    		logManager.info(String.format("[%s][%s][%s][%d][서버유형: %s][failOver모드: %s]\n",connObj.getConnectionGroupId(), 
    								connObj.getConnectionKey(),connObj.getCurrentServerIp(), connObj.getCurrentServerPort(), 
    								connObj.getCurrentServerType(), connObj.getFailOverMode()));
    	}
    }
    
    public void resetConnectionObjectInfoByForcedDisconnected(String connectionId) {
    
    	if(this.connections.containsKey(connectionId)) {
    		logManager.info("["+connectionId+"]의 정보를 초기화 하겠습니다.");
    		ConnectionObject connObj = this.connections.get(connectionId);
    		connObj.setConnectionFirstTime(true);
    		connObj.setConnectionReset(true);
    		connObj.setConnectionStatus(false);
    		connObj.setFailOverTryCount(0);
    		connObj.closeSession();
    	} else {
    		logManager.warn(String.format("[%s] 는 존재하지 않는 연결객체 입니다. 다시 한번 확인이 필요합니다.\n", connectionId));
    	}
    }
    
    public String getConnectionFailOverMode() {
    	return this.connectionFailOverMode;
    }
    
    public void setConnectionStatus(String connectionStatus) {
    	this.connectionStatus = connectionStatus;
    }
    
    public String getConnectionStatus() {
    	return this.connectionStatus;
    }
    
    public int changeAllConnectionObjectsTargetServer(String connectionServerType) {    	
    	
    	// 서버타입: A(ctive), B(ackup)    	
    	int numOfAffectedObjects = 0;
    	for(String connectionId : this.connections.keySet()) {
    		ConnectionObject connObj = this.connections.get(connectionId);
    		if(!connObj.getConnectionObjectStatus().equalsIgnoreCase("MC")) {
	    		numOfAffectedObjects++;
	    		// System.out.printf("[%d] before [%s][%d][%s][%s]\n", numOfAffectedObjects, connObj.getCurrentServerIp(), connObj.getCurrentServerPort(), connObj.getCurrentServerType(), connObj.getFailoverTryCount());
	    		connObj.setCurrentServerIp(connObj.getCurrentServerType() != "A" ? connObj.getActiveIp() : connObj.getBackupIp());
	    		connObj.setCurrentServerPort(connObj.getCurrentServerType() != "A" ? connObj.getActivePort() : connObj.getBackupPort());
	    		connObj.setCurrentServerType(connectionServerType);
	    		connObj.setFailOverTryCount(0); // 0으로 초기화 해준다.
	    		logManager.info(String.format("[%d] after  [%s][%d][%s][%s]\n", numOfAffectedObjects, connObj.getCurrentServerIp(), connObj.getCurrentServerPort(), connObj.getCurrentServerType(), connObj.getFailoverTryCount()));
    		}
    	}
    	
    	if(numOfAffectedObjects > 0 ) {
    		String prevConnectionServerType = this.connectionServerType;
    		this.connectionServerType = connectionServerType;
    		logManager.info(String.format("/// 이전 서버 유형 (%s)에서 (%s)로 변경되었습니다.\n", prevConnectionServerType, this.connectionServerType));
    	}
    	
    	return numOfAffectedObjects;
    }
    
    public void setPoolMap(Map<String, Pool<ConnectionObject>> poolMap) {
    	this.poolMap = poolMap;
    }
    
    public String getConnectionServerType() {
    	return connectionServerType;
    }
    
    public boolean isFirst() {    	
    	// 첫번째인지 확인한 후 false로 설정한다. (최초 연결 시도시를 위하여 사용한다) 하나라도 연결되면 - Toggling 개념이 아님 최초 접근 여부 처리(앱기동시)
    	if(connectionFirstTime) { 
    		connectionFirstTime = false; 
    		return true;
    	}
    	return connectionFirstTime;
    }

    public List<String> getConnectionGroupList() {
        return connectionGroupList;
    }

    public HeartbeatPolicy getHeartbeatPolicy() {
        return heartbeatPolicy;
    }

    public ConnectionFailoverPolicy getFailoverPolicy() {
        return failoverPolicy;
    }

    public int getReconnectionTryIntervalSec() {
        return reconnectionTryIntervalSec;
    }
    
    public int getNumberOfConnectionGroups() {
    	return this.connectionGroupList.size();
    }
    
    public int getNumberOfConnectedObjectsInPools() {    	
    	int numOfPoolObjects = 0;        
        for(String connectionGroupId : this.connectionGroupList) {
            // Pool size는 설정된 개수 *3배 만큼 여유있게 함, 나중에 추가될 수 있으므로        	
        	numOfPoolObjects += poolMap.get(connectionGroupId).getPoolObjectCount();
        }
        return numOfPoolObjects;
    }

    public int getConnectionCount() {
        return this.connections.size();
    }
    
    public int getPoolConnectionCount() {
    	int countPool = 0;
    	int countInfo = 0;
    	for(String connectionId : connections.keySet()) {
    		if(connections.get(connectionId).getConnectionType().equalsIgnoreCase("POOL")) {
    			countPool++;
    		} else {
    			countInfo++;
    		}
    	}
    	logManager.info(String.format("//// 서버유형 [%s] 총 %d의 연결객체는 POOL유형 객체 %d개와 INFO유형 객체 %d개로 이루어져 있습니다.\n", 
    				ConnectionObject.getServerType(connectionServerType), connections.size(), countPool, countInfo));
    	return countPool;
    }

    public ConcurrentHashMap<String, ConnectionObject> getConnections() {
        return connections;
    }
    
    public void updateConfigWithDB(ArrayList<String> NEConfigInfo) {
    	// 해당 정보를 가져와서 설정한다. XML대신 List에 있는 DB 데이터 정보를 기준으로 한다.    	
        // 비교 후 정리작업을 수행한다. - 일단 모든 것을 멈춰준다. - 그리고 다음으로 넣어준다.
    	// 기존의 것을 모두 정리한 후 다시 기동한다.
    	connectionStatus = "U"; // 업데이트 중으로 표시한다.
    	logManager.info("updateConfigWithDB() - 데이터베이스 내용을 읽은 정보를 기반으로 연결객체 정보를 재정립힙니다.");    	
    	this.checkUpdateConfigInfo(NEConfigInfo); // 초기화 한 후 재설정을 요청한다.    	    	
    	connectionStatus = "D"; // 업데이트 완료 후 표시한다. - Disconnected로 설정해준다.
    }
    
    public void checkUpdateConfigInfo(ArrayList<String> NEConfigInfo) {
    	
    	logManager.info("************************************************************************");
    	logManager.info("ConnectionConfig.checkUpdateConfigInfo 설정해야할 NE 갯수 ["+NEConfigInfo.size()+"]");
    	logManager.info("************************************************************************");

    	// 기본 연결객체 정보를 설정해준다.
    	String connectionIds = this.setConnectionObjectBasicInformation(NEConfigInfo);
    	
    	// Insert, Update, delete 작업을 수행한다.
    	logManager.info("//////////////////////////////////////////////////////////////////////////////////////////////////////////");
    	logManager.info("** 현재 UpdateConfig에 의해서 설정된 ConnectionId 목록 기준으로 Add, Udpate, Delete 업무 수행 예정: "+connectionIds);    
    	logManager.info("//////////////////////////////////////////////////////////////////////////////////////////////////////////");
    	for(String connectionId : this.connections.keySet()) {
    		ConnectionObject connObj = this.connections.get(connectionId);    		
    		if(connectionIds.contains(connectionId)) {
    			if(connObj.getConnectionObjectStatus().equalsIgnoreCase("NC") 
					|| connObj.getConnectionObjectStatus().equalsIgnoreCase("SC")
					|| connObj.getConnectionObjectStatus().equalsIgnoreCase("UC")) {
    				logManager.info(String.format("이번 UpdateConfig에 신규로 포함된 연결객체 [%s] 입니다. - Connection 요청이 필요합니다.\n", connectionId));
                	// 한번만 수행하면 된다. - 첫번째 접속은 무조건 Active 서버로 접속하도록 설정해준다.
    				// 신규로 생성된 경우 어디로 붙는게 맞을 것인가?
    				if(failoverPolicy.isPortbasedFailOver()) {
    					logManager.info("포트 기반 절체의 경우 Active Ip, Port 설정 후 연결처리에 맡긴다.");
    					// initDefaultServerInfo(connObj); // 정책을 최초 설정된 것으로 할 경우 - 기본은 지금 다른 연결객체가 설정된 것으로 그대로 간다. 	          
    					connObj.initServerInfoByServerType(connectionServerType);
    				} else if (failoverPolicy.isHostbasedFailOver()) {
    					logManager.info("호스트 기반 절체의 경우 기본 연결된 serverType에 맞춰서 연결해준다.");
    					connObj.initServerInfoByServerType(connectionServerType);
    				}    				
    				putConnectionRequestQueue(connectionId);
    				
    			} else if (connObj.getConnectionObjectStatus().equalsIgnoreCase("EC")) {
    				// 기존의 경우 현재 접속한 서버의 값이 변경되었는지 확인하고 값이 동일한 경우 스킵, 변경된 경우 종류 후 재연결을 위한 절차를 밟습니다.
    				if(connObj.isSameConnectedServerInfoWithNewConfigInfo()) {
    					// 기존과 같은 값이면 그대로 유지한다.
    					logManager.info(String.format("[%s] 내 접속 정보가 동일하므로 별도의 조치 없이 현재 상태를 유지합니다.\n", connectionId));
    				} else {
    					// 값이 다르므로 정리하고 새롭게 연결을 요청한다.
    					initDefaultServerInfo(connObj);
    					logManager.info(String.format("[%s] 변경된 접속 정보 [%s][%d] : 신규 정보 기준으로 이전 상태를 종료시키고 새롭게 연결을 시도합니다.\n",
    											connectionId, connObj.getCurrentServerIp(), connObj.getCurrentServerPort()));
    					connObj.setConnectionObjectStatus("UC"); // 접속 정보가 변경되었으므로 현재 접속을 종료하고 신규 접속을 시도해야 합니다.
    					putDisconnAndConnectionRequestQueue(connectionId);
    				}
    			}
    		} else {
    			logManager.info(String.format("이번 UpdateConfig에 포함되지 않은 연결객체입니다 [%s]는 삭제대상으로 지금 제거합니다\n.", connectionId));
				connObj.setConnectionObjectStatus("DC"); // 삭제 대상으로 마킹하고 삭제 요청큐에 넣어줍니다. - 작업이 끝나고 해야 하지 않을까 생각됩니다. - 무엇을 기준으로
				putDisconnectionRequestQueue(connectionId);
    		}
    	}
    	
    	// 접속객체 정보를 보여준다.
    	this.displayConnectionObjectInfo();	    	
    }
    
    public void updateConfigWithDBAndReconnect(ArrayList<String> NEConfigInfo) {
    	/*
    	 * 해당 정보를 가져와서 설정한다. XML대신 List에 있는 DB 데이터 정보를 기준으로 한다.
    	 * 비교 후 정리작업을 수행한다. - 일단 모든 것을 멈춰준다. - 그리고 다음으로 넣어준다.
		 * 기존의 것을 모두 정리한 후 다시 기동한다.    	
    	 */
    	connectionStatus = "U"; // 업데이트 중으로 표시한다.
    	logManager.info("updateConfigWithDBAndReconnect() - 데이터베이스 내용을 읽은 정보를 기반으로 연결객체 정보를 재정립힙니다.");    	
    	this.checkUpdateConfigInfoAndReconnect(NEConfigInfo); // 초기화 한 후 재설정을 요청한다.    	    	
    	connectionStatus = "D"; // 업데이트 완료 후 표시한다. - Disconnected로 설정해준다.
    }
    
    public void checkUpdateConfigInfoAndReconnect(ArrayList<String> NEConfigInfo) {
    	
    	logManager.info("************************************************************************");
    	logManager.info(String.format("ConnectionConfig.checkUpdateConfigInfoAndReconnect 설정해야할 NE 갯수 [%d]\n", NEConfigInfo.size()));
    	logManager.info("************************************************************************");    	    	
    	
    	// 기본 연결객체 정보를 설정해준다.
    	String connectionIds = this.setConnectionObjectBasicInformation(NEConfigInfo);
    	
    	// Insert, Update, delete 작업을 수행한다.
    	logManager.info(String.format("** 현재 UpdateConfig에 의해서 설정된 ConnectionId 목록 기준으로 Add, Udpate, Delete 업무 수행 예정 [대상 연결객체: %s]\n",connectionIds));    	
    	for(String connectionId : this.connections.keySet()) {
    		ConnectionObject connObj = this.connections.get(connectionId);    		
    		if(connectionIds.contains(connectionId)) {
    			if(connObj.getConnectionObjectStatus().equalsIgnoreCase("NC")) {
    				logManager.info(String.format("이번 UpdateConfig에 신규로 포함된 연결객체 [%s] 입니다. - Connection 요청이 필요합니다.\n", connectionId));
                	// 한번만 수행하면 된다. - 첫번째 접속은 무조건 Active 서버로 접속하도록 설정해준다.
    				// 신규로 생성된 경우 어디로 붙는게 맞을 것인가?
    				if(failoverPolicy.isPortbasedFailOver()) {
    					logManager.info("포트 기반 절체의 경우 Active Ip, Port 설정 후 연결처리에 맡긴다.");    					
    					initDefaultServerInfo(connObj);    					
    				} else if (failoverPolicy.isHostbasedFailOver()) {
    					logManager.info("호스트 기반 절체의 경우 기본 연결된 serverType에 맞춰서 연결해준다.");

    					// DB 정보 갱신 후 업데이트 이전의 연결 서버 유형으로 접속하도록 설정한다.
    					// connObj.initServerInfoByServerType(this.connectionServerType);
    					
    					// 기본인 Active 서버로 접속을 시도하게 설정한다.
    					this.connectionServerType = "A"; // Active를 기본으로 설정한다. <= 어떻게 할지 정책으로 결정
    					initDefaultServerInfo(connObj);
    				}
    				putConnectionRequestQueue(connectionId);   				
    				
    			} else if (connObj.getConnectionObjectStatus().equalsIgnoreCase("EC") 
    						|| connObj.getConnectionObjectStatus().equalsIgnoreCase("SC")
    						|| connObj.getConnectionObjectStatus().equalsIgnoreCase("UC")) {
    				// 기존의 경우의 모두 종류 후 새롭게 연결해준다.- 값이 다르므로 정리하고 새롭게 연결을 요청한다.
    				// Active 서버에 접속하도로 변경한 
    				this.connectionServerType = "A"; // Active를 기본으로 설정한다. <= 어떻게 할지 정책으로 결정
					initDefaultServerInfo(connObj);
					
					logManager.info(String.format("[%s] 변경된 접속 정보 [%s][%d] : 갱신된 정보 기준으로 연결종류 후 재연결을 수행합니다\n.", 
											connectionId, connObj.getCurrentServerIp(), connObj.getCurrentServerPort()));
					connObj.setConnectionObjectStatus("UC"); // 접속 정보가 변경되었으므로 현재 접속을 종료하고 신규 접속을 시도해야 합니다.					
					putDisconnAndConnectionRequestQueue(connectionId);
    			} 
					
    		} else {
    			logManager.info(String.format("이번 UpdateConfig에 포함되지 않은 연결객체입니다 [%s]는 삭제대상으로 지금 제거합니다.\n", connectionId));
    			connObj.setConnectionObjectStatus("DC"); // 삭제 대상으로 마킹하고 삭제 요청큐에 넣어줍니다. - 작업이 끝나고 해야 하지 않을까 생각됩니다. - 무엇을 기준으로    			
    			putDisconnectionRequestQueue(connectionId);
    		}
    	}
    	
    	// 접속객체 정보를 보여준다.
    	this.displayConnectionObjectInfo();
    }

	// deprecated: 더 이상 사용하지 않는다.
    public void updateConfigWithXML(String xmlString) {
    	// 해당 정보를 가져와서 설정한다. XML대신 List에 있는 DB 데이터 정보를 기준으로 한다.
    	this.connections.clear();
    	this.connectionGroupList.clear();
        this.parseXml(xmlString);;
    }

    public void initConfigWithXML(String xmlString) {
    	// XML 파서로 무엇을 사용할지 명시적으로 지정해준다. - 지정해주지 않는 경우 다른 파서를 로딩하므로써 문제 발생
    	System.setProperty("javax.xml.parsers.DocumentBuilderFactory", "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl" );
    	this.connections.clear();
    	this.connectionGroupList.clear();
        this.parseXml(xmlString);
    }
    
    
    public void initConfigWithDB(ArrayList<String> NEConfigInfo) {
    	
    	logManager.info("************************************************************************");
    	logManager.info(String.format("ConnectionConfig.initConfigWithDB 설정해야할 NE 갯수 [%d] 2022-01-06 Refactoring work\n", NEConfigInfo.size()));
    	logManager.info("************************************************************************");
    	    	
    	this.connections.clear();
    	this.connectionGroupList.clear();
    	
    	this.setConnectionObjectBasicInformation(NEConfigInfo);
    	this.initDefaultServerInfoInConnectionObjects(); // ConnectionConfig 내에 있는 ConnectionObjects의 currentServerIp & Port 번호를 A(ctive) 서버로 초기화 해준다.
    	
    	// 연결객체의 기본 정보를 보여준다.
    	this.displayConnectionObjectInfo();	    	
    }

    
    private String setConnectionObjectBasicInformation(ArrayList<String> NEConfigInfo) {
    	
    	String connectionIds = "";
    	
    	// 1) ConnectionConfig 기본설정
    	String[] cols = NEConfigInfo.get(0).toString().split(";");
    	setBasicConnectionConfigInformation(cols);

    	// 2) ConnectionObject 개별 설정
    	int idx = 1;
    	int numOfGroups = 0;
    	
    	for(String elem : NEConfigInfo) {
    		cols = elem.split(";");
    		int connCount = stringToInteger(cols[NECONN.CONN_COUNT.idx]);
    		logManager.info(String.format("[%d][neconn_id: %d][연결객체 생성 개수: %d개][%s;;%s][%s][%d]-[%s]\n", idx++,
    																		stringToLong(cols[NECONN.NECONN_ID.idx]),
    																		connCount,
																			cols[NECONN.CONN_GROUPNAME.idx], 
																			cols[NECONN.CONN_KEY.idx], 
																			cols[NECONN.CONN_IP_A.idx], 
																			stringToInteger(cols[NECONN.CONN_PORT_A.idx]), 
																			cols[NECONN.FAILOVER_POLICY.idx]));
    		
    		
    		String connectionGroupId = cols[NECONN.CONN_GROUPNAME.idx]; // 컨넥션 풀의 그룹명 가져오기
    		if(!this.connectionGroupList.contains(connectionGroupId)) {
    			numOfGroups++;
    			logManager.info(String.format("** 새로운 그룹 [%s] 를 등록하겠습니다. 총 그룹수 %d개 입니다.\n", connectionGroupId, numOfGroups));
    			this.connectionGroupList.add(connectionGroupId);
    		}
    		
    		if(connCount < 1) {
    			logManager.info("////////////////////////////////////////////////////////////////////////////////");
    			logManager.info(String.format("[NECONNP_ID: %s]의 CONN_COUNT 값에 문제가 있습니다. TB_PVSM_NECONN CONN_COUNT의 값이 최소 1이어야 합니다.\n", cols[NECONN.NECONN_ID.idx]));
    			logManager.info("////////////////////////////////////////////////////////////////////////////////");
    		}
    		
    		for(int i=0; i < connCount; i++) {
    		
	    		ConnectionObject connObj = null;
	    		String connectionKey = String.format("%s-%d",cols[NECONN.CONN_KEY.idx], i);
	    		String connectionId = String.format(ConnectionObject.CID_FORMAT, connectionGroupId, connectionKey);    		
	    		connectionIds += connectionId+",";
	    		
	    		if(!this.connections.containsKey(connectionId)) {
	    			connObj = new ConnectionObject(connectionGroupId, connectionKey); // connectionObject의 키 값으로는 다음과 같다.
	    			this.connections.put(connObj.getConnectionId(), connObj);
	    			logManager.info(String.format("[%d/%d][%s] 는 신규 ConnectionObject 입니다. - connections 맵에 등록하였습니다. [%s]\n", i, connCount, connectionId, this.connections.containsKey(connectionId)));
	    			
	    		} else {
	    			connObj = connections.get(connectionId);
	    			logManager.info(String.format("[%d/%d][%s] 는 이미 등록되어 있습니다. 정보를 필요한 추가 정보를 등록하겠습니다.\n", i, connCount, connObj.getConnectionId()));
	    		}
	    		
	    		connObj.setNEConnId(stringToLong(cols[NECONN.NECONN_ID.idx])); // NECONN_ID 유일값
	    		connObj.setConnectionCount(connCount);
	            connObj.setActiveIp(cols[NECONN.CONN_IP_A.idx]);	// Active Server IP	            
	            connObj.setActivePort(stringToInteger(cols[NECONN.CONN_PORT_A.idx])); // Active Server Port            
	            connObj.setBackupIp(cols[NECONN.CONN_IP_B.idx]);	// Backup Server IP
	            connObj.setBackupPort(stringToInteger(cols[NECONN.CONN_PORT_B.idx])); // Backup Server Port
	            connObj.setDrIp(cols[NECONN.CONN_IP_D.idx]);	// Server IP
	            connObj.setDrPort(stringToInteger(cols[NECONN.CONN_PORT_D.idx])); // Backup Server Port
	    	        	    	
	    		connObj.setConnectionType(cols[NECONN.CONN_TYPE.idx]);    	    	
		    	connObj.setFailOverPolicy(cols[NECONN.FAILOVER_POLICY.idx]); // FailOverPolicy: Port 또는 Host
	    		        		
	    	    connObj.setReadTimeOut(stringToInteger(cols[NECONN.READ_TIMEOUT.idx])*1000);
	    	    connObj.setReadTimeOutRetryCount(stringToInteger(cols[NECONN.READ_TIMEOUT_RETRY_COUNT.idx]));
	    	    connObj.setWriteTimeOut(stringToInteger(cols[NECONN.WRITE_TIMEOUT.idx])*1000);
	    	    connObj.setWriteTimeOutRetryCount(stringToInteger(cols[NECONN.WRITE_TIMEOUT_RETRY_COUNT.idx]));
	    	    connObj.setHeartBeatInterval(stringToInteger(cols[NECONN.HEARTBEAT_INTERVAL.idx])*1000);
	        	connObj.setHeartBeatTryCount(stringToInteger(cols[NECONN.HERATBEAT_TRY_COUNT.idx]));
	        	connObj.setHeartBeatFailSeconds(connObj.getHeartBeatInterval() * connObj.getHeartBeatTryCount());
	        	connObj.SetFailOverMaxTryCountToSendEvent(stringToInteger(cols[NECONN.FAILOVER_RETRY_COUNT.idx]));
	    	    connObj.setAutoFailOverYN(cols[NECONN.AUTO_FAILOVER_YN.idx]);
	    	    
	    	    connObj.setHeartBeatMessage(cols[NECONN.HERATBEAT_MESSAGE.idx]); // HeartBeat Message 
	    		connObj.setNEManager(cols[NECONN.NE_MANAGEMENT.idx]); // NE 관리 담당자
	    		connObj.setDescription(cols[NECONN.DESCRIPTION.idx]);
		    	    
		    	if(connObj.getConnectionType().equalsIgnoreCase("INFO")) {
		    		connObj.setConnectionObjectStatus("IC");
		    		this.initDefaultServerInfo(connObj);
		    	}
		    	logManager.info(String.format("[%d][%s]", i, connectionIds));
    		} 
    	}
    	
    	return connectionIds;
    }

    private void parseXml(String xmlString) {
        
    	// DB 생성이 안되었을 경우 임시로 사용한다.
    	DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        // parse XML file
        DocumentBuilder db = null;
        Document doc = null;
        try {
            db = dbf.newDocumentBuilder();
            doc = db.parse(new InputSource(new StringReader(xmlString)));
            //   doc = db.parse(xmlString);
        } catch (SAXException e) {
            throw new RuntimeException(e.toString());
        } catch (IOException e) {
            throw new RuntimeException(e.toString());
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e.toString());
        }
        // optional, but recommended
        doc.getDocumentElement().normalize();

        this.NEA_PODNAME = doc.getElementsByTagName("tns:TargetName").item(0).getTextContent();
        String failOverPolicy = doc.getElementsByTagName("tns:FailoverPolicy").item(0).getTextContent();
        logManager.info(String.format("[지정된 Failover 정책: %s]\n", failOverPolicy));        
        this.reconnectionTryIntervalSec = Integer.parseInt(doc.getElementsByTagName("tns:ConnectionRetryIntervalSec").item(0).getTextContent());
        
        //--- start parse & config FailOverPolicy
        if("Port".equalsIgnoreCase(failOverPolicy)){
        	logManager.info("* PORT 단위 절체 정책 사용");
            failoverPolicy = new ConnectionFailoverPolicy(ConnectionFailoverPolicy.FailOver.Port_Level);
        } else if("Host".equalsIgnoreCase(failOverPolicy)){
        	logManager.info("* HOST 단위 절체 정책 사용");
        	failoverPolicy = new ConnectionFailoverPolicy(ConnectionFailoverPolicy.FailOver.Host_Level);
        } else {
        	logManager.warn("* 알 수 없는 절체 정책 사용 - 확인 필요");
        }

        //--- start parse & config FailOverPolicy
        Element hbpolicy = (Element)doc.getElementsByTagName("tns:HeartbeatPolicy").item(0);
        if("false".equalsIgnoreCase(hbpolicy.getElementsByTagName("tns:Use").item(0).getTextContent()))
            this.heartbeatPolicy = new HeartbeatPolicy(false);
        else {
            this.heartbeatPolicy = new HeartbeatPolicy(true);
            this.heartbeatPolicy.setIntervalSeconds(Integer.parseInt(hbpolicy.getElementsByTagName("tns:Interval").item(0).getTextContent()));
            this.heartbeatPolicy.setHeartbeatFailSeconds(Integer.parseInt(hbpolicy.getElementsByTagName("tns:Timeout").item(0).getTextContent()));
        }


        // --- start parse & config ConnectionGroup
        NodeList grpList = doc.getElementsByTagName("tns:ConnectionGroup");
        this.connectionGroupList = new ArrayList<>(grpList.getLength());

        for(int grpIdx=0; grpIdx<grpList.getLength(); grpIdx++) {
            Node grpNode = grpList.item(grpIdx);
            String grpId = grpNode.getAttributes().item(0).getNodeValue();
            this.connectionGroupList.add(grpId);
            logManager.info("/// ConnectionGroup=" + grpId);

            // --- start parse & config Connections
            NodeList list = grpNode.getChildNodes();
            for(int idx=0; idx<list.getLength();idx++) {
                Node node = list.item(idx);
                if(node.getNodeType() == Node.ELEMENT_NODE) {
                    Element connElement = (Element) node;
                    // connection-id
                    ConnectionObject connObj = new ConnectionObject(grpId, connElement.getElementsByTagName("tns:ConnectionId").item(0).getTextContent());
                    this.connections.put(connObj.getConnectionId(), connObj);
                    logManager.info("/// ConnectionID=" + connObj.getConnectionId());
                    
                    connObj.setFailOverPolicy(failOverPolicy); 

                    // active server
                    Element serverInfo = (Element) connElement.getElementsByTagName("tns:ActiveServer").item(0);
                    connObj.setActiveIp(serverInfo.getElementsByTagName("tns:IP").item(0).getTextContent());
                    connObj.setActivePort(Integer.parseInt(serverInfo.getElementsByTagName("tns:Port").item(0).getTextContent()));

                    // backup server
                    serverInfo = (Element) connElement.getElementsByTagName("tns:BackupServer").item(0);
                    connObj.setBackupIp(serverInfo.getElementsByTagName("tns:IP").item(0).getTextContent());
                    connObj.setBackupPort(Integer.parseInt(serverInfo.getElementsByTagName("tns:Port").item(0).getTextContent()));
                    
                    // DR server
                    serverInfo = (Element) connElement.getElementsByTagName("tns:DRServer").item(0);
                    connObj.setDrIp(serverInfo.getElementsByTagName("tns:IP").item(0).getTextContent());
                    connObj.setDrPort(Integer.parseInt(serverInfo.getElementsByTagName("tns:Port").item(0).getTextContent()));

                    // 첫번째 접속은 무조건 Active 서버로 접속하도록 설정해준다.
                    initDefaultServerInfo(connObj);
                }
            }
        }
    }
    
    
    /*
     * BW의 JavaStarter에게 이벤트를 전달하기 위한 목적별 Queue에 값을 넣어준다.
     */
    // 신규 연결객체 생성 후 연결이 필요한 경우 요청 - PVSCommon application - PVSConnManager.StarterTCPConnection.bwp
    public void putConnectionRequestQueue(String connectionId) {
		try {
			// 확인 필요함 : if(!Registry.getInstance().connectionRequestQueue.equals(connectionId)) {
			if(connections.get(connectionId).getConnectionType().equalsIgnoreCase("POOL")) {
				if(Registry.getInstance().putConnRequest(connectionId)) {
					logManager.info(String.format("신규 객체의 연결요청을 큐에 [%s]를 추가 하였습니다.\n", connectionId));
				}
			} else {
				logManager.info(String.format("[%s]는 연결객체 유형이 [%s]로 연결이 필요없는 객체입니다.\n", connectionId, connections.get(connectionId).getConnectionType()));
			}
		}catch(Exception ex) {
			logManager.error(String.format("["+connectionId+"] 연결요청 큐에 넣는 중에 오류가 발생하였으므로 합당한 조치를 취하여 주십시요!!!"));
		}
	}
    
    // 현 연결객첵의 연결 종류 후 새롭게 연결이 필요한 경우 요청 - PVSCommon application - PVSConnManager.StarterDisconnectAndConnectionAgain.bwp
    public void putDisconnAndConnectionRequestQueue(String connectionId) {					
		try {
			if(connections.get(connectionId).getConnectionType().equalsIgnoreCase("POOL")) {
				if(Registry.getInstance().putDisconnAndConnRequest(connectionId)) {
					logManager.info("정보가 갱신되 연결객체에 대해서 disconnAndConnReq 큐에 ["+connectionId+"]을 추가 하였습니다.");
				}
			} else {
				logManager.info(String.format("[%s]는 연결객체 유형이 [%s]로 연결이 필요없는 객체입니다.\n", connectionId, connections.get(connectionId).getConnectionType()));
			}
		}catch(Exception ex) {
			logManager.error(String.format("["+connectionId+"] 연결요청 큐에 넣는 중에 오류가 발생하였으므로 합당한 조치를 취하여 주십시요!!!"));
		}
	}
    
    // 현 연결객첵의 연결 종류 후 연결객체 ConfigMap에서 완전히 제거를 해야하는 경우 요청 - PVSCommon application - PVSConnManager.StarterDisconnectAndRemoveFromConfig.bwp
    public void putDisconnectionRequestQueue(String connectionId) {
		try {
			if(connections.get(connectionId).getConnectionType().equalsIgnoreCase("POOL") && !connections.get(connectionId).getConnectionObjectStatus().equalsIgnoreCase("SC")) {
				if(Registry.getInstance().putDisconnRequest(connectionId)) {
					logManager.info("지정 객체의 삭제요청을 연결종료 요청 큐에 ["+connectionId+"]를 추가 하였습니다..");
				}
			} else {
				// ConnectionTpye "INFO"인 경우로 connections 연결객체 MAP에서만 삭제한다.
				connections.remove(connectionId);
				logManager.info(String.format("[%s]는 연결객체 유형이 [%s]로 삭제 후 재연결이 필요 없는 객체입니다.\n", connectionId, connections.get(connectionId).getConnectionType()));
			}
		}catch(Exception ex) {
			logManager.error(String.format("["+connectionId+"] 연결종료 요청 큐에 넣는 중에 오류가 발생하였으므로 합당한 조치를 취하여 주십시요!!!"));
		}
	}
    
    /*
     * 각종 정보 설정에 필요한 반복 호출 함수 모음
     */
	public void initDefaultServerInfo(ConnectionObject connObj) {
        connObj.setCurrentServerIp(connObj.getActiveIp());
        connObj.setCurrentServerPort(connObj.getActivePort());
        connObj.setCurrentServerType("A"); // A(ctive), B(ackup), D(isaster Recovery)
        connObj.setConnectionFirstTime(true);
	}
	
    void setBasicConnectionConfigInformation(String[] cols) {   
	    
    	int HBInterval = Integer.parseInt(cols[NECONN.HEARTBEAT_INTERVAL.idx]);
		int HBTryCount = Integer.parseInt(cols[NECONN.HERATBEAT_TRY_COUNT.idx]);
    	
		this.NEA_PODNAME = cols[NECONN.NEA_PODNAME.idx]; // POD명 - Key로 사용한다.
		this.reconnectionTryIntervalSec = Integer.parseInt(cols[NECONN.RECONNECTION_TRY_INTERVAL_SEC.idx]);
		
		this.autoFailOverYN = cols[NECONN.AUTO_FAILOVER_YN.idx];
		this.connectionFailOverMode = autoFailOverYN.equalsIgnoreCase("Y") ?  "A" : "M"; // 자동 혹은 오토 모드인지 전환여부 확인해주기
		
    	
    	if("Port".equalsIgnoreCase(cols[NECONN.FAILOVER_POLICY.idx])) {
    		logManager.info("* PORT 단위 절체 정책 사용");
            this.failoverPolicy = new ConnectionFailoverPolicy(ConnectionFailoverPolicy.FailOver.Port_Level);
    	} else if("Host".equalsIgnoreCase(cols[NECONN.FAILOVER_POLICY.idx])) {
    		logManager.info("* HOST 단위 절체 정책 사용");
        	this.failoverPolicy = new ConnectionFailoverPolicy(ConnectionFailoverPolicy.FailOver.Host_Level);
    	} else {
    		logManager.warn("* 알 수 없는 절체 정책 사용 - 확인 필요");
    	}
    	
    	if("N".equalsIgnoreCase(cols[NECONN.HEARTBEAT_YN.idx])) {
    		this.heartbeatPolicy = new HeartbeatPolicy(false);
    		logManager.info("* 하트비트 체크를 사용하지 않습니다. ["+cols[NECONN.HEARTBEAT_YN.idx]+"]");
    	} else {
    		this.heartbeatPolicy = new HeartbeatPolicy(true);
            this.heartbeatPolicy.setIntervalSeconds(HBInterval);
            this.heartbeatPolicy.setHeartbeatFailSeconds(HBTryCount*HBInterval);
            logManager.info(String.format("* 하트비트 체크를 사용 [%s] 합니다. [HB 간격: %d초][HB 실패 최대시간: %d초]\n", cols[NECONN.HEARTBEAT_YN.idx], HBInterval, HBTryCount));
    	}
    	
    	// NE Agent 레벨의 설정값으로 SharedVariable을 통해서 설정이 가능하도록 한다.
    	// ConnectionObject 레벨로 변경해준다.
    	// this.heartbeatInterval = stringToInteger(cols[NECONN.HEARTBEAT_INTERVAL.idx]);
    	// this.heartbeatTryCount = stringToInteger(cols[NECONN.HERATBEAT_TRY_COUNT.idx]);
    	
    	this.borrowTimeOut = stringToInteger(cols[NECONN.BORROW_WAIT_TIMEOUT.idx]) * 1000;
    	this.borrowTimeOutRetryCount = stringToInteger(cols[NECONN.BORROW_WAIT_TIMEOUT_RETRY_COUNT.idx]);
    	
    	this.readTimeOut = stringToInteger(cols[NECONN.READ_TIMEOUT.idx]) * 1000;
    	this.readTimeOutRetryCount = stringToInteger(cols[NECONN.READ_TIMEOUT_RETRY_COUNT.idx]);
    	
    	this.writeTimeOut = stringToInteger(cols[NECONN.WRITE_TIMEOUT.idx]) * 1000;
    	this.writeTimeOutRetryCount = stringToInteger(cols[NECONN.WRITE_TIMEOUT_RETRY_COUNT.idx]);
    	
    	// NE Agent 동작에 중요한 각종 설정값을 보여준다.
    	logManager.info(String.format("* POD 명: %s\n", NEA_PODNAME));
    	logManager.info(String.format("* 하트비트 인터벌: %d초\n", HBInterval));
    	logManager.info(String.format("* 하트비트 최대 시도 횟수: %d회\n", HBTryCount));
    	logManager.info(String.format("* Borrow 타임아웃: %d초\n", this.borrowTimeOut/1000));
    	logManager.info(String.format("* Borrow 타임아웃 발생 시 최대 재시도 횟수: %d회\n", this.borrowTimeOutRetryCount));
    	logManager.info(String.format("* Read 타임아웃: %d초\n", this.readTimeOut/1000));
    	logManager.info(String.format("* Read 타임아웃 발생 시 최대 재시도 횟수: %d회\n", this.readTimeOutRetryCount));
    	logManager.info(String.format("* 재연결 시도 간격: %d초\n", reconnectionTryIntervalSec));
    	logManager.info(String.format("* 서버 절체 모드 [%s]\n", ConnectionObject.getFailOverMode(connectionFailOverMode)));
    	logManager.info("********************************************************************************************");
	}
    
    public boolean setConnectionServerType(String connectionServerType) {    	
    	boolean bValid = true;
    	    	
    	logManager.info(String.format("1. [before: %s][request Type: %s]\n", this.connectionServerType, connectionServerType));    	
    	// 적용하기 전에 서버 유형으로 지정이 가능한지 확인한다.    	
    	for(String connectionId : this.connections.keySet()) {
    		ConnectionObject connObj = this.connections.get(connectionId);
    		if(!connObj.validServerTypeWithConnectionInfo(connectionServerType)) {
    			logManager.warn(String.format("Invalid serverType: [%s] 해당 서버관련 접속 정보를 가지고 있지 않습니다.\n", connectionServerType));
    			bValid = false;
    			break;
    		}
    	}
    	
    	if(bValid) {
    		this.connectionServerType =  connectionServerType;
    		logManager.info(String.format("2. 연결객체의 서버 타입이 정상적으로 변경되었습니다. [%s]\n", this.connectionServerType));
    	}else {
    		logManager.info(String.format("[Valid: %s][after: %s] 이전 값을 유지합니다.\n", bValid, displayServerType(this.connectionServerType)));
    	}
    	return bValid;
    }
    
    /*
     * ConnectionConfig 내에 있는 ConnectionObjects의 currentServerIp & Port 번호를 A(ctive) 서버로 초기화 해준다.
     */
    private void initDefaultServerInfoInConnectionObjects() {    
    	for(String connectionId : connections.keySet()) {
    		ConnectionObject connObj = connections.get(connectionId);
    		this.initDefaultServerInfo(connObj);
    	}
    }
    
    
    public String getConnectionIdByConnectable(Connectable connectable) {
    	Optional<String> connectionId = this.connections.entrySet().stream()
    					.filter(conn-> (conn.getValue() != null))
    					.filter(conn-> connectable.equals(conn.getValue().getConnection()))
    					.map(conn->conn.getKey())
    					.findFirst();
    	if(connectionId.isPresent()) return connectionId.get();
    	return null;
    }

    /**
     * ConnectionConfig 에서 connectionId 를 삭제하는 역할을 한다.
     * 주의 : 하지만 여기서는 connectable 을 close 하지 않고 반환되는 곳에서 close 를 시도해야 한다.
     * 다시 체크 필요함.
     * @param connectionGroupId
     * @param connectionKey
     * @return
     */
    public Connectable removeConnection(String connectionGroupId, String connectionKey) {
    	Connectable connectable = null;    	
    	logManager.info(String.format("ConnectionConfig.disconnectConnection [%s;;%s] 연결 객체를 connection pool에서 삭제하도록 하겠습니다.\n", connectionGroupId, connectionKey));
    	String connectionId = String.format(ConnectionObject.CID_FORMAT, connectionGroupId, connectionKey);
    	
    	if(this.connections.containsKey(connectionId)) {
    		ConnectionObject connObj = this.connections.remove(connectionId); // connection Object 객체 정보를 삭제를 해준다.
    		if(connObj != null) {
				connectable = connObj.getConnection();
				connObj.closeSession();
				logManager.info(String.format("[%s] 연결객체를 삭제하도록 하겠습니다. \n", connObj.getConnectionId()));
    		}
    	} else {
    		logManager.warn(String.format("[%s] 연결객체가 ConnectionManager 내에 없습니다. 삭제 전에 다시 한번 확인부탁드립니다.\n", connectionId));
    	}
    	
    	return connectable;
    }
    
}
