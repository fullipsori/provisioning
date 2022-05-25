package com.lguplus.pvs;

import com.lguplus.pvs.model.Connectable;
import com.lguplus.pvs.util.LogManager;

public class ConnectionObject {
    // ConnectionID 포맷 = ConctionGroup + delimeter(";;") + ConnectionId
    public static String CID_FORMAT = "%s;;%s";
    public static String CID_DELIMETER = ";;";
    
    public static String getServerType(String serverType) {
    	if(serverType.equalsIgnoreCase("A")) {
    		return "Active";
    	} else if(serverType.equalsIgnoreCase("B")) {
    		return "Backup";
    	} else if(serverType.equalsIgnoreCase("D")) {
    		return "DR";
    	}
    	return "Unknown";
    }
    
    public static String getFailOverMode(String failoverMode) {
    	if(failoverMode.equalsIgnoreCase("A")) {
    		return "Auto";
    	} else if(failoverMode.equalsIgnoreCase("M")) {
    		return "Manual";
    	}
    	return "Unknown";
    } 
    
    public static String getObjectStatus(String connectionObjectStatus) {    	
    	if(connectionObjectStatus.contains("NC")) {
    		return "New Connection"; // 새롭게 생성된 연결 객체
    	} else if(connectionObjectStatus.contains("EC")) {
    		return "Exist Connection"; // 이미 연결되어 있는 상태의 객체
    	} else if(connectionObjectStatus.contains("DC")) {
    		return "Delete Connection"; // 연결 종료 후 삭제할 대상 객체
    	} else if(connectionObjectStatus.contains("UC")) {
    		return "Update Connection"; // 금번 Config 업데이트 시 변경된 연결 객체
    	} else if(connectionObjectStatus.contains("IC")) {
    		return "Info Connection"; // 연결 정보만 가지고 있는 객체 - (IP, Port) 정보만 제공
    	} else if(connectionObjectStatus.contains("SC")) {
    		return "Suspended Connection"; // 현재 일심 정지 상태의 연결 객체
    	}    	
    	return "Unknown";
    }

    // connection을 유일한게 식별할 ID, DB 기본정보에서도 Unique Key 로 관리될 것으로 예상됨
    private long neConnId = -1;		// 설정하지 않았을 경우 기본값은 -1 이며, 0이상의 양수의 값을 가진다.  TB_PVSM_NECONN의 순차적 증가 ID
    private String currentServerType = ""; 	// A(ctive), B(ackup), D(isaster Recovery)
    private String currentServerIp = "";	// 현재 접속 중 서버의 IP 주소
    private int currentServerPort = 0;		// 현재 접속 중 서버의 Port 번호
    private int connectionCount = 1; 		// 기본 하나이다.
    
    private String connectionId = null;     // TB_PVSM_NECONN 테이블의 GroupId + connectionKey 결합 예) HSS;;C-0 VMS;;D-0 등
    private String failoverPolicy = ""; 	// Host, Port 단위
    private String failoverMode = "A";		// A(uto), M(anul) 자동 수동 모드에 따라서 동작이 달라진다. 
    
    private String connectionKey = "";		// connectionKey 예) A-Z
    private String connectionGroupId = "";	// groupID 예) HSS-01 <- NEA_PODNAME과 동일한 항목이다. 
    private int handledMsgCount = 0;		// 처리한 메시지 개수 - 재연결시점에서 0으로 재설정된다.
    
    private boolean borrowed = false;		// 현재 ConnectionObject를 사용되고 있다는 의미
    private boolean connectionStatus = false; 	// 연결상태 true | 종료상태 false
    private boolean connectionFirstTime = true;	// 설정 후 첫번째 연결임을 확인하기 위하여 사용한다.
    private boolean connectionReset = false; // 연결객체 reset 여부 표시 - Reset 시도시 true 설정 - 연결 후 바로 false로 변경
    private String  connectionObjectStatus = "NC"; // 최초 연결 전에는 신규 NC - 첫번째 연결 후에는 EC - 삭제 대상이되면 DC로 해준다. IU 정보만 유지한다.(동일하게 사용한다.)
    private String  connectionType = "POOL"; // 기본값: POOL - 연결 후 connection pool에 넣어놓는다.  INFO: 연결 정보만 저장하고 있는다.
 
    // Server Information
    private String activeIp ="";	// Active IP 주소
    private int activePort=0; 		// Active Port 번호
    
    private String backupIp =""; // Backup IP 주소
    private int backupPort=0; 		// Backup Port 번호
    
    private String drIp = ""; // DR IP 주소: Disaster Recovery Server
    private int drPort=0; 		 // DR Port 번호: Disaster Recovery Server

    // Fail-over 시 연결시도를 Active or Backup 중 어느곳으로 해야할 지 판단하기 위해 관리하는 숫자
    private int failoverTryCount = 0; // 현재까지 연결 시도 횟수: 최소 5회 
    
    // BW에서 생성한 TCP/IP Connection 객체, byte array 형태 임
    private Connectable connectableSession = null;
    
    // BW에서 마지막으로 사용된 시간, 최초는 Pool에 추가된 시간, 이후는 사용되고 Pool에 return 되는 시간으로 갱신 됨
    private long lastUsedTime;

    // TCP 읽기, 쓰기, 가져오기 등의 일을 할 때 사용할 것들
    private String autoFailOverYN = "Y"; // 기본값: "Y"
    private String heartbeatMessage = "dummy message"; // HeartBeat시 사용할 메시지
    private String neManager = "PVS팀 문의"; // NE 장비 관리자 연락처
    private String description = "";
        
    private int readTimeOut = 30000;		// 기본 30초 
    private int readTimeOutRetryCount = 3;  // 촏 3회 반복     
    private int writeTimeOut = 30000;		// 기본 30초 
    private int writeTimeOutRetryCount = 3;  // 촏 3회 반복
    private int heartbeatInterval = 30000;	// 하트비트 간격 30초
    private int heartbeatTryCount = 6;		// 최대 재시도 횟수 6회
    private int heartbeatFailSeconds = 30000 * 6; // 180초 => 기본 3분     
    private int failoverMaxTryCountToSendEvent = 12; // 12회까지 반복 후 오류 이벤트를 보내준다.

    private final LogManager logManager;
    public ConnectionObject(String connectionGroupId, String connectionKey) {
    	this.logManager = LogManager.getInstance();
        this.connectionId = String.format(CID_FORMAT, connectionGroupId, connectionKey);
        this.connectionGroupId = connectionGroupId;
        this.connectionKey = connectionKey;        
        logManager.info(String.format("/// >>>%s, [NEW-ConnectionObject][%s]\n",Thread.currentThread().getName(), this.connectionId));
    }
    
    public boolean isBorrowed() { return borrowed; }
    public void borrowConnection() { borrowed = true; }
    public void releaseConnection() { borrowed = false; }
    
    public String logConnectionObjectInfo() {
    	return String.format("[%s][%s] 현재 접속 여부 [%s] 서버 주소/포트 [%s][%d][접속서버유형: %s][연결시도횟수: %d회][처리 메시지: %d개][연결객체 상태:%s]\n", 
    					this.connectionGroupId, this.connectionKey, this.connectionStatus, this.currentServerIp, this.currentServerPort, 
    					getServerType(this.currentServerType), this.getFailoverTryCount(), this.handledMsgCount, this.connectionObjectStatus);
	}    
  
    public String getConnectionObjectInfo() {
    	StringBuffer connInfo = new StringBuffer();    	
    	String connStatus = connectionStatus ? "1" : "0"; // 1: connected, 2: disconnected
    	
    	connInfo.append("{");
    	connInfo.append(String.format("\"NECONN_ID\":\"%d\"", neConnId));
    	connInfo.append(",");
    	connInfo.append(String.format("\"CONN_GROUPNAME\":\"%s\"", connectionGroupId));
    	connInfo.append(",");
    	connInfo.append(String.format("\"CONN_KEY\":\"%s\"", connectionKey));
    	connInfo.append(",");
    	connInfo.append(String.format("\"PORT\":%d", currentServerPort));
    	connInfo.append(",");
    	connInfo.append(String.format("\"CONN_IP\":\"%s\"", currentServerIp));
    	connInfo.append(",");
    	connInfo.append(String.format("\"CONN_IP_TYPE\":\"%s\"", currentServerType));
    	connInfo.append(",");
    	connInfo.append(String.format("\"CONN_STATUS\":\"%s\"", connStatus));
    	connInfo.append(",");
    	connInfo.append(String.format("\"CONN_OBJECT_STATUS\":\"%s\"", connectionObjectStatus));
    	connInfo.append(",");    	
    	connInfo.append(String.format("\"FAILOVER_POLICY\":\"%s\"", this.failoverPolicy));
    	connInfo.append(",");
    	connInfo.append(String.format("\"DESCRIPTION\":\"%s\"", this.description));
    	connInfo.append(",");
    	connInfo.append(String.format("\"CODE\": \"@CODE@\""));
    	connInfo.append(",");
    	connInfo.append(String.format("\"REASON\": \"@REASON@\""));
    	connInfo.append(",");
    	connInfo.append(String.format("\"CONN_COUNT\": %d", connectionCount));
    	connInfo.append(",");
    	connInfo.append(String.format("\"CONN_REAL_COUNT\": @CONN_REAL_COUNT@"));
    	connInfo.append("}");
    	
    	return connInfo.toString();
    }
    
    public String[] getConnectionConfigInfo() {    	
    	StringBuffer connConfig = new StringBuffer();
    	
    	// BW NE 에이전트 TCP 업무 수행에 필요한 정보르 넘겨준다.
    	connConfig.append(this.readTimeOut);
    	connConfig.append(";");
    	connConfig.append(this.readTimeOutRetryCount);
    	connConfig.append(";");
    	connConfig.append(this.writeTimeOut);
    	connConfig.append(";");
    	connConfig.append(this.writeTimeOutRetryCount);
    	
    	return connConfig.toString().split(";");
    }
    
    public void resetByServerType(String serverType) {
    	
    	if(serverType.equals("A")) {
    		currentServerIp = activeIp;
    		currentServerPort = activePort;
    	} else if (serverType.equals("B")) {
    		currentServerIp = backupIp;
    		currentServerPort = backupPort;
    	} else if(serverType.equals("D")) {
    		currentServerIp = drIp;
    		currentServerPort = drPort;
    	} else {
    		logManager.warn("* 지정하신 ["+serverType+"] 타입은 존재하지는 않는 유형입니다. 다시 한번 확인해주십시요. A(ctive)/ B(ackup)/ D(isaster Recovery)");
    		return;
    	}
    	
    	// 대부분의 값을 초기화 해준다.
    	failoverTryCount = 0;
    	lastUsedTime = System.currentTimeMillis();
    	// handledMsgCount = 0; - 전체 처리량 확인을 위하여 초기화를 하지 않는다.
    	connectionStatus = false;
    	connectionReset = true;
    	connectionObjectStatus = "MC"; // 모두 초기화 된다는 의미로 타켓 이동을 위하여 MC(Move Connection) 객체로 상태를 변경해준다.
    	currentServerType = serverType;
    	
    	logManager.info(String.format("* 연결객체 [%s][%s] 재설정 작업[%s][%d][%s]\n", connectionGroupId, connectionKey, currentServerIp, currentServerPort, getServerType(currentServerType)));
    }    
    
    public void initServerInfoByServerType(String serverType) {    	
    	if(serverType.equals("A")) {
    		currentServerIp = activeIp;
    		currentServerPort = activePort;
    	} else if (serverType.equals("B")) {
    		currentServerIp = backupIp;
    		currentServerPort = backupPort;
    	} else if(serverType.equals("D")) {
    		currentServerIp = drIp;
    		currentServerPort = drPort;
    	} else {
    		logManager.warn(String.format("* 지정하신 [%s] 타입은 존재하지는 않는 유형입니다. 다시 한번 확인해주십시요. A(ctive)/ B(ackup)/ D(isaster Recovery)", getServerType(serverType)));
    		return;
    	}
    	
    	// 대부분의 값을 초기화 해준다.
    	failoverTryCount = 0;
    	lastUsedTime = System.currentTimeMillis();
    	// handledMsgCount = 0; - 전체 처리량 확인을 위하여 초기화를 하지 않는다.
    	connectionStatus = false;
    	connectionReset = false;
    	connectionFirstTime = true;
    	currentServerType = serverType;
    	
    	logManager.info(String.format("* 서버타입 [%s]으로 지정연결객체[%s][%s]를 초기화 합니다. 서버 연결 정보[%s][%d]\n", getServerType(currentServerType), connectionGroupId, connectionKey, currentServerIp, currentServerPort));
    }
    
    public boolean validServerTypeWithConnectionInfo(String connectionServerType) {
    	boolean bValid = true;
    	
    	if(connectionServerType.equalsIgnoreCase("D")) {
    		if(drIp == "") bValid= false;
    	} else if(connectionServerType.equalsIgnoreCase("B")) {
    		if(backupIp == "") bValid = false;
    	} else if(connectionServerType.equalsIgnoreCase("A")) {
    		if(activeIp == "") bValid = false;
    	} else {
    		logManager.warn(String.format("["+connectionServerType+"] 알 수 없는 서버 유형입니다. 확인해보시기 바랍니다."));
    		bValid = false;
    	}
    	
    	return bValid;
    }
    
    public String ForcedChangeConnectionServerType(String serverType) {
    	
    	if(this.currentServerType != serverType) {
    		switch(serverType) {
    		case "A":
    			currentServerType = serverType;
    			currentServerIp = activeIp;
    			currentServerPort = activePort;
    			break;
    		case "B":
    			if(backupIp.isBlank() || backupPort == 0) {
    				logManager.warn(String.format("ERROR: 지정하신 %s 서버유형의 접속정보가 존재하지 않습니다. 재확인 바랍니다.\n", serverType));
    			} else {
    				currentServerType = serverType;
    				currentServerIp = backupIp;
    				currentServerPort = backupPort;
    			}	
    			break;
    		case "D":
    			if(drIp.isBlank() || drPort == 0) {
    				logManager.warn(String.format("ERROR: 지정하신 %s 서버유형의 접속정보가 존재하지 않습니다. 재확인 바랍니다.\n", serverType));
    			} else {
    				currentServerType = serverType;
    				currentServerIp = drIp;
    				currentServerPort = drPort;
    			}	
    			break;
    		default:
    			logManager.warn(String.format("ERROR: 지정하신 %s 서버유형은 존재하지 않습니다. 재확인 바랍니다.\n", serverType));
    		}
    	} else {
    		logManager.warn(String.format("ERROR: 이미 연결된 서버와 동일한 서버에 연결하려고 시도합였습니다. 현재 접속서버유형: %s\n", this.currentServerType));
    	}
    	
    	// 오류가 발생한 경우 방금접속 종료되었던 서버에 재접속을 신청한다.
    	return currentServerIp+";"+currentServerPort;
    }

    public String getConnectionTarget(boolean isPortbasedFailOver, int pooledObjectCount) {
    	
    	String connectionInfo = "";
    	this.connectionStatus = false;
    	this.lastUsedTime = System.currentTimeMillis(); // 일단 연결 시도한 시작점이 되므로 현재 시간을 설정해준다.
    	
    	// 연결 시도 횟수를 1 증가시킨다.
    	this.increaseFailoverTryCount(); 
    	
    	if(failoverTryCount == failoverMaxTryCountToSendEvent ) {
    		// 필요시 특정 Action을 취한다.- 지정된 횟수 이상 접속 시도를 수행햇으나 실패했을 경우
    		String eventMessage = String.format("%s;%s;ERROR;|%s| 연결 시도 횟수가 %d회에 도달하였습니다. NE에 문제가 있는지 확인바랍니다.", connectionGroupId, connectionKey, this.description, failoverTryCount);    		
    		Registry.getInstance().addEventSendRequest(eventMessage);
    		logManager.warn(eventMessage);
    	}
    	
    	if(connectionReset) {
        	// 이미 resetByServerType에서 변경을 하였기에 connectionInfo 설정해서 넘겨주고, reset flag를 false 상태로 만들어준다.
        	connectionReset = false;
        	connectionInfo = this.currentServerIp+";"+this.currentServerPort;
        	logManager.info("connection Reset 되었습니다. ["+this.currentServerIp+"]["+this.currentServerPort+"]");
        	
        } else {        
        	// 포트기반 절체이면서 failover 모드가 자동(Auto)인 경우만 들어온다.
	        if(isPortbasedFailOver && failoverMode.equals("A")) {
	        	logManager.info("//// getConnectionTarget: Port 기반 절체인 경우 ["+this.failoverPolicy+"][현재서버유형: "+currentServerType+"]");
	        	
	        	if(connectionFirstTime) {
	        		connectionFirstTime = false; // 첫번째 접속입니다.
	        		logManager.info("* 첫번쨰 접속 시도입니다. 이후 포트단위 절체에서는 A => B => A 반복 접속을 시도합니다.");
	        	} else {

		        	// 해당 값이 존재하지 않을 경우 다시 이전의 연결정보로 롤백하기 위하여 임시 저장해준다.
	        		String tmpServerType = currentServerType;
	        		String tmpServerIp = currentServerIp;
	        		int tmpServerPort = currentServerPort;
	        		
		        	currentServerType = (currentServerType.equals("A")) ? "B" : "A"; // A(ctive)이면 B(ackup)으로 전환
		        	currentServerIp = (currentServerType.equals("A")) ? activeIp : backupIp;
		        	currentServerPort =  (currentServerType.equals("A")) ? activePort : backupPort;
		        	
		        	// 연결을 위해 지정한 IP나 Port의 값이 없거나 0인 경우 서버 접속 정보를 원상태 해준다.
		        	if(currentServerIp.isBlank() || currentServerPort == 0) {
		        		logManager.info("////////////////////////////////////////////////////////////////////////////////////////////");
		        		logManager.info(String.format("//// 새로 설정한 서버유형에 접속 정보가 존재하지 않습니다. 다시 기존의 서버유형으로 접속을 시도합니다. [%s][%d]///\n", currentServerIp, currentServerPort));
		        		logManager.info("////////////////////////////////////////////////////////////////////////////////////////////");
		        		currentServerType = tmpServerType;
		        		currentServerIp = tmpServerIp;
		        		currentServerPort = tmpServerPort;
		        	}
	        	}	        	
	        	connectionInfo = currentServerIp+";"+currentServerPort;
	            
	        } else if ((!isPortbasedFailOver || failoverMode.equals("M"))) { // 호스트 기반의 경우 처리를 위하여 들어본다.
	        	// 호스트 기반 절체인 경우 현재의 연결 정보를 유지해준다. (서버 정보 변경 위치: ConnectionTryMonitor 에서 수행)
	        	if(!isPortbasedFailOver && !currentServerType.equals("D")) {
	        		logManager.info(String.format("** getConnectionTarget: Host 기반 절체인 경우 [%d][FailOver정책: %s][현재 접속 서버: %s]\n", pooledObjectCount, this.failoverPolicy, getServerType(this.currentServerType)));
	        	} else {
	        		logManager.info(String.format("** getConnectionTarget: [FailOver정책:%s] 기반 절체 [%s][현재 Pool에 있는 갯수 %d]", this.failoverPolicy, getServerType(this.currentServerType), pooledObjectCount));
	        	}
	        	connectionInfo = this.currentServerIp+";"+this.currentServerPort;
	        }
        }        
        return connectionInfo;
    }

    // Active서버 또는 Backup서버에 연결되었는지 여부 return
    public boolean isConnectedToActiveServer() {
        //짝수이면 Active, 홀수이면 backup
        if(currentServerType.equals("A")) {
        	return true;
        } else {
        	return false;
        }
    }
    
    public boolean isConnectedToDRServer() {
    	if(currentServerType.equals("D")) {
        	return true;
    	} else {
    		return false;
    	}
    }    
    
    public long getNEConnId() { return neConnId; }
    public void setNEConnId(long neConnId) { this.neConnId = neConnId; }
    
    public String getConnectionKey() { return this.connectionKey; }
    public void setConnectionkey(String connectionKey) { this.connectionKey = connectionKey; }
    
    public String getConnectionGroupId() { return connectionGroupId; }
    public void setConnectionGroupId(String connectionGroupId) { this.connectionGroupId = connectionGroupId; }
    
    public int getConnectionCount() { return this.connectionCount; }
    public void setConnectionCount(int connectionCount) { if(connectionCount != -1) this.connectionCount = connectionCount; }
    
    public int getHandledMsgCount() { return this.handledMsgCount; }
    public void resetHandledMsgCount() { this.handledMsgCount = 0; }
    public void increateHandledMsgCount() { this.handledMsgCount++; }
    
    public String getFailOverPolicy() { return this.failoverPolicy; }
    public void setFailOverPolicy(String failoverPolicy) { this.failoverPolicy = failoverPolicy; }
    
    public String getFailOverMode() { return this.failoverMode; }    
    public void setFailOverMode(String failoverMode) { this.failoverMode = failoverMode; }
    
    public String getCurrentServerType() { return this.currentServerType; }    
    public void setCurrentServerType(String currentServerType) { this.currentServerType = currentServerType; }
    
    public String getCurrentServerIp() { return currentServerIp; }
    public void setCurrentServerIp(String currentServerIp) { this.currentServerIp = currentServerIp; } 
    
    public int getCurrentServerPort() { return this.currentServerPort; }
    public void setCurrentServerPort(int currentServerPort) { this.currentServerPort = currentServerPort; }
    
    public boolean getConnectionStatus() { return connectionStatus; }
    public void setConnectionStatus(boolean connectionStatus) { this.connectionStatus = connectionStatus; }
    
    public boolean getConnectionFirstTime() { return connectionFirstTime; }
    public void setConnectionFirstTime(boolean connectionFirstTime) { this.connectionFirstTime = connectionFirstTime; }
    
    public boolean getConnectionReset() { return connectionReset; }
    public void setConnectionReset(boolean connectionReset) { this.connectionReset = connectionReset; }
    
    public String getConnectionObjectStatus() { return connectionObjectStatus; }
    public void setConnectionObjectStatus(String connectionObjectStatus) { this.connectionObjectStatus = connectionObjectStatus; }
    
    public String getConnectionType() { return connectionType; }
    public void setConnectionType(String connectionType) { this.connectionType = connectionType; }
    
    public int getFailOverTryCount() { return failoverTryCount; }
    public void setFailOverTryCount(int failoverTryCount) { this.failoverTryCount = failoverTryCount; }
 
    public String getAutoFailOverYN() { return autoFailOverYN; }
    public void setAutoFailOverYN(String autoFailOverYN) { 
    	this.autoFailOverYN = autoFailOverYN;
    	if("Y".equalsIgnoreCase(autoFailOverYN)) {
    		this.failoverMode = "A"; // 자동으로
    	} else {
    		this.failoverMode = "M"; // 수동으로
    	}
    }
    
    public String getHeartBeatMessage( ) { return heartbeatMessage; }    
    public void setHeartBeatMessage(String heartbeatMessage) { this.heartbeatMessage = heartbeatMessage; }
    
    public String getNEManager() { return neManager; }    
    public void setNEManager(String neManager) { this.neManager = neManager; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    // 설정값이 -1인 경우 ConnectionObject 생성시 지정된 기본값을 유지한다. : -1값을 갖는 경우는 DB에 설정값이 NULL 혹은 Invalid값 일때 발생한다. stringToInteger, stringToLong
    public int getReadTimeOut() { return readTimeOut; }
    public void setReadTimeOut(int readTimeOut) { if(readTimeOut != -1) this.readTimeOut = readTimeOut; } 
    
    public int getReadTimeOutRetryCount() { return readTimeOutRetryCount; }
    public void setReadTimeOutRetryCount(int readTimeOutRetryCount) { if(readTimeOutRetryCount != -1) this.readTimeOutRetryCount = readTimeOutRetryCount; }
    
    public int getWriteTimeOut() { return writeTimeOut; }
    public void setWriteTimeOut(int writeTimeOut) { if(writeTimeOut != -1) this.writeTimeOut = writeTimeOut; } 
    
    public int getWriteTimeOutRetryCount() { return writeTimeOutRetryCount; }
    public void setWriteTimeOutRetryCount(int writeTimeOutRetryCount) { if(writeTimeOutRetryCount != -1) this.writeTimeOutRetryCount = writeTimeOutRetryCount; }
    
    public int getHeartBeatInterval() { return heartbeatInterval; }    
    public void setHeartBeatInterval(int heartbeatInterval) { if(readTimeOut != -1) this.heartbeatInterval = heartbeatInterval; }
    
    public int getHeartBeatTryCount() { return heartbeatTryCount; }
    public void setHeartBeatTryCount(int heartbeatTryCount) { if(readTimeOut != -1) this.heartbeatTryCount = heartbeatTryCount; }
    
    public int getHeartBeatFailSeconds() { return heartbeatFailSeconds; }
    public void setHeartBeatFailSeconds(int heartbeatFailSeconds ) { if(readTimeOut != -1) this.heartbeatFailSeconds = heartbeatFailSeconds; }
    
    public int getFailOverMaxTryCountToSendEvent() { return failoverMaxTryCountToSendEvent;} 
    public void SetFailOverMaxTryCountToSendEvent(int failoverMaxTryCountToSendEvent) { if(readTimeOut != -1) this.failoverMaxTryCountToSendEvent = failoverMaxTryCountToSendEvent;}

    public Connectable getConnection() { return connectableSession; }
    public void closeSession() {
		ConnectionRepository.getInstance().closeConnectable(this.connectableSession);
		this.connectableSession = null;
    }

    public ConnectionObject setConnection(Connectable connectable) {
		try{
			this.connectableSession = connectable;
			this.initialize();
			return this;
		}catch(Exception e){
			logManager.error(String.format("Exception:" + e.getMessage()));
			return null;
		}
    }

    public String getConnectionId() { return connectionId;}

    public long getLastUsedTime() { return lastUsedTime; }    
    public void setLastUsedTime(long lastUsedTime) { this.lastUsedTime = lastUsedTime; }

    public String getActiveIp() { return activeIp; }
    public void setActiveIp(String activeIp) { if(!activeIp.isEmpty() && !activeIp.equalsIgnoreCase("NULL")) this.activeIp = activeIp; }

    public int getActivePort() { return activePort; }
    public void setActivePort(int activePort) { if(activePort > 0) this.activePort = activePort; }
    
    public String getBackupIp() { return backupIp; }
    public void setBackupIp(String backupIp) {
    	
    	if(!backupIp.isEmpty() && !backupIp.equalsIgnoreCase("NULL")) { 
    		logManager.info("////// 1111 backupIp value is ["+backupIp+"]");
    		this.backupIp = backupIp;
    	} else {
    		logManager.info("////// 2222 backupIp value is ["+backupIp+"]");
    	}
    }

    public int getBackupPort() { return backupPort; }
    public void setBackupPort(int backupPort) { if(backupPort > 0) this.backupPort = backupPort; }
        
    // DR 서버 관련 설정
    public String getDrIp() { return drIp; }
    public void setDrIp(String drIp) { if(!drIp.isEmpty() && !drIp.equalsIgnoreCase("NULL")) this.drIp = drIp; }

    public int getDrPort() { return drPort; }
    public void setDrPort(int drPort) { if(drPort > 0) this.drPort = drPort; }
    
    public long getFailoverTryCount() { return failoverTryCount; }
    public void increaseFailoverTryCount() { this.failoverTryCount++; }
    
    private void initialize() {
    	this.connectionStatus = true; // 연결되었음을 알려준다.
    	this.lastUsedTime = System.currentTimeMillis();
        this.failoverTryCount = 0;
        this.connectionObjectStatus = "EC";
        // this.handledMsgCount = 0; - 임시 전체 처리량을 확인하기 위하여
    }
    
    public boolean isSameConnectedServerInfoWithNewConfigInfo() {
    	
    	boolean bRet = false;
    	if(currentServerType.equalsIgnoreCase("A")) {
    		logManager.info(String.format("**** Active 서버 접속 상태 현재 정보 [%s][%d] == [%s][%d]\n", currentServerIp, currentServerPort, activeIp, activePort));    		
    		if(currentServerIp.equalsIgnoreCase(activeIp) && currentServerPort == activePort) {
    			return true;
    		} else {
    			logManager.warn(String.format("Active 서버 접속 상태이나 정보가 서로 다릅니다. [%s][%d] != [%s][%d]\n", currentServerIp, currentServerPort, activeIp, activePort));
    			currentServerIp = activeIp;
    			currentServerPort = activePort;
    		}
    	} else if(currentServerType.equalsIgnoreCase("B")) {
    		logManager.info(String.format("**** Backup 서버 접속 상태 현재 정보. [%s][%d] == [%s][%d]\n", currentServerIp, currentServerPort, backupIp, backupPort));
    		if(currentServerIp.equalsIgnoreCase(backupIp) && currentServerPort == backupPort) {
    			return true;
    		} else {
    			logManager.warn(String.format("Backup 서버 접속 상태이나 정보가 서로 다릅니다. [%s][%d] != [%s][%d]\n", currentServerIp, currentServerPort, backupIp, backupPort));
    			currentServerIp = backupIp;
    			currentServerPort = backupPort;
    		}    		
    	} else if(currentServerType.equalsIgnoreCase("D")) {
    		logManager.info(String.format("***** DR 서버 접속 상태 현재 정보 [%s][%d] == [%s][%d]\n", currentServerIp, currentServerPort, drIp, drPort));
    		if(currentServerIp.equalsIgnoreCase(drIp) && currentServerPort == drPort) {
    			return true;
    		} else {
    			logManager.warn(String.format("DR 서버 접속 상태이나 정보가 서로 다릅니다. [%s][%d] != [%s][%d]\n", currentServerIp, currentServerPort, drIp, drPort));
    			currentServerIp = drIp;
    			currentServerPort = drPort;
    		}
    	} else {
    		logManager.warn(String.format("알 수 없는 서버 유형입니다. - 확인이 필요합니다. ["+currentServerType+"]"));
    	}
    	
    	return bRet;	
    }
}
