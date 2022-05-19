package com.lguplus.pvs;

import com.lguplus.pool.BoundedBlockingPool;
import com.lguplus.pool.Pool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class BasePoolProxy {
    private ConnectionInvalidator connectionInvalidator = null;
    private HeartbeatMonitor heartbeatMonitor = null;
    private ExecutorService proxyTaskThread = Executors.newSingleThreadExecutor();
    private ConnectionTryMonitor connectionTryMonitor = null;
    
    private static int MULTIPLY_POOL_SIZE = 3;// 현재 설정된 Database 값보다 3배수 크게 사이즈를 가져간다.
    private ArrayList<String> NEConfigInfo = null;

    private Map<String, Pool<ConnectionObject>> poolMap = null;
    
    /* debug : 개발 모드 , info : 개발용 로그 제외, warn : warning, error : 에러만 출력 */
    public BasePoolProxy(String logLevel) {
    	LogManager.getInstance().setLevel(logLevel);
    	LogManager.getInstance().warn(String.format("/// PoolProxy 객체를 최초 생성합니다. - NEConfigInfo 정보를 담을 ArrayList를 같이 만들어 줍니다. ///"));
    	NEConfigInfo = new ArrayList<>();
    }
    
    /*
     * ConnectionGroupId 기반: Idle Object를 가져오기
     */
    public Map<String, Pool<ConnectionObject>> getPoolMap() { return poolMap; }
    
    public void requestSendEventMessage(String reason, Connectable connectable) {    	
    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnectionObject(connectable);
    	
    	if(connObj != null) {
	        String code = "INFO";
	        String eventMessage = String.format("%s;%s;%s;%s", connObj.getConnectionGroupId(), connObj.getConnectionKey(), code, reason);
	    	Registry.getInstance().eventSendRequestQueue.add(eventMessage);
    	}
    }
    
    public String getHeartBeatMessage(String connectionId) {
    	String heartbeatMessage = "dummy heartbeat message";
    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj != null) {
    		heartbeatMessage = connObj.getHeartBeatMessage();
			LogManager.getInstance().info("getHeartBeatMessage by connectionId ["+connectionId+"]["+heartbeatMessage+"]");
    	} else {
    		heartbeatMessage = String.format("[ERROR] 연결객체 %s가 존재하지 않습니다. NEConfig DB를 확인하시기 바랍니다.", connectionId);
    		LogManager.getInstance().warn(heartbeatMessage);
    	}
    	
    	return heartbeatMessage;
    }
    
    public int[] getBorrowTimeOutAndRetryCount() {
    	return ConnectionConfig.getInstance().getBorrowTimeOutAndRetryCount();
    }
    
    public int[] getReadWriteTimeOutAndRetryCount(Connectable connectable) {
    	return ConnectionConfig.getInstance().getReadWriteTimeOutAndRetryCount(connectable);
    }
    
    public void addNEConfig(String neConfig ) {
    	NEConfigInfo.add(neConfig);
    }
    
    public void displayNEConfigInfo() {
    	
    	NECONN.printAllValueAndIndex();
    	
    	LogManager.getInstance().warn("** NE 설정값을 가져와서 ArrayList에 넣은 값을 보여줍니다.");
    	NEConfigInfo.stream().forEach(s -> LogManager.getInstance().warn(s));
    	LogManager.getInstance().warn("**********************************************************");
    }
    
    public void setConnectionFailOverMode(String failOverMode) {
    	String prevFailOverMode = ConnectionConfig.getInstance().getConnectionFailOverMode(); 
    	if(prevFailOverMode.equalsIgnoreCase(failOverMode)) {
    		LogManager.getInstance().info(String.format("* 현재의 Failover 연결 모드를 [%s] ==[%s] 동일합니다. 현재 모드를 유지하겠습니다.\n", prevFailOverMode, failOverMode));
    	} else {
    		LogManager.getInstance().info(String.format("* Failover 연결 모드를 [%s] 에서 [%s] 로 전환하였습니다.\n", prevFailOverMode, failOverMode));
    		ConnectionConfig.getInstance().setConnectionFailOverMode(failOverMode);
    	}
    }
    
    public void setConnectionInfoByIndexWithServerType(int index, String serverType) {
    	Connectable connectable = ConnectionConfig.getInstance().setConnectionInfoByIndexWithServerType(index, serverType);
    	if(connectable != null) {
    		this.requestReconnect(connectable);
    	} else {
    		LogManager.getInstance().info(String.format("PoolProxy:setConnectionInfoByIndexWithServerType[%d][%s]\n", index, serverType));
    	}
    }
    
    public String[] changeTargetServerBasedOnPorts(String serverType, String connectionIdList) {
    	String[] connectionIds = connectionIdList.split(",");    	
    	LogManager.getInstance().info(String.format("/// [ERROR][%s][%s]\n", serverType, connectionIdList));
    	for(int i=0; i < connectionIds.length; i++) {
    		connectionIds[i] = String.format("%s-0",connectionIds[i]);
    		LogManager.getInstance().info(String.format("[%d] 옮길 대상 ConnectionId [%s]\n", i, connectionIds[i]));
    	}
    	return connectionIds;
    }
        
    
    public String changeTargetServer(String serverType) {
    	// Failover Mode를 자동 혹은 수동으로 전환 하기 위하여 사용한다. A(ctive), B(ackup), D(isaster Recovery)
    	String failOverMode = ConnectionConfig.getInstance().getConnectionFailOverMode();
    	if(serverType.equalsIgnoreCase("D")) {
    		String setFOM = failOverMode;
    		failOverMode = "M"; // 강제적으로 전환합니다.
    		LogManager.getInstance().info(String.format("* DR의 경우 반드시 수동 절체 모든만 지원합니다.[failverMode 전환: %s => %s][serverType: (%s)isater]\n", setFOM, failOverMode, serverType));
    	} else {
    		if("Y".equalsIgnoreCase(ConnectionConfig.getInstance().getAutoFailOverYN())) {
    			failOverMode = "A";	// 자동절체로 설정한다.
    		} else {
    			failOverMode = "M"; // 수동절체로 설정한다.
    		}
    	}
    	
    	String retMsg = "";
    	LogManager.getInstance().info("* 서버간 접속 변경 요청 ["+ConnectionConfig.getInstance().getConnectionServerType()+"] == ["+serverType+"]");
    	if(!ConnectionConfig.getInstance().getConnectionServerType().equalsIgnoreCase(serverType)) {
	    	if(ConnectionConfig.getInstance().setConnectionServerType(serverType)) { 
	    		this.setConnectionFailOverMode(failOverMode); // FailOver 정책을 수동으로 전환한다.
	    		retMsg = String.format("지정하신 서버모드 %s로 성공적으로 전환하였습니다.", ConnectionConfig.getInstance().displayServerType(serverType));
	    	} else {
	    		retMsg = String.format("[ERROR] 지정하신 서버유형 %s는 존재하지 않는 유형이거나 연결 정보가 존재하지 않습니다. 연결정보DB를 확인해보시기 바랍니다.", 
	    								ConnectionConfig.getInstance().displayServerType(serverType));
	    	}    	 
    	} else {
    		retMsg = String.format("[ERROR] 지정하신 서버 유형 %s는 현재 접속된 서버유형과 동일합니다. 서버간 전환 요인이 없습니다.", ConnectionConfig.getInstance().displayServerType(serverType));;
    	}
    	return retMsg;
    }
    
    public boolean isReadyToStart() {
    	boolean bRet = false;
    	int numOfConnectionObjects = ConnectionConfig.getInstance().getConnectionCount();
    	int numOfPooledObjects = ConnectionConfig.getInstance().getNumberOfConnectedObjectsInPools();
    	if(numOfConnectionObjects == numOfPooledObjects || numOfPooledObjects >= 1) bRet = true;
    	LogManager.getInstance().info("/////////////////////////////////////////////////////////////////////////////////////");
    	LogManager.getInstance().info(String.format("/// 시작할 준비가 되었는지 확인합니다. [연결객체 정보 총: %d개] == [Pooled 연결객체 총: %d개][%s]\n", numOfConnectionObjects, numOfPooledObjects, bRet));
    	LogManager.getInstance().info("/////////////////////////////////////////////////////////////////////////////////////");
    	
    	return bRet;
    }
    
    /*
     * 신규 연결객체를 ConnectionManager에 추가한다.
     * Param1: connectionGroupId;;connectionKey
     * Param2: Active,serverIp,serverPort,NECONN_ID;
     */
    public String addNewConnection(String groupIdAndConnectionKey, String serverIPsAndServerPorts) {
    	
    	if(groupIdAndConnectionKey.split(";").length == 2) {
    		String connectionGroupId = groupIdAndConnectionKey.split(";")[0];
    		String connectionKey = groupIdAndConnectionKey.split(";")[1];    	
    	
    		LogManager.getInstance().info(String.format("PoolProxy-addNewConnection 파라미터 [%s][%s][%s]\n", connectionGroupId, connectionKey, serverIPsAndServerPorts));    	
    		return ConnectionConfig.getInstance().addNewConnection(connectionGroupId, connectionKey, serverIPsAndServerPorts);
    	} else {
    		String retMsg = String.format("[ERROR] PoolProxy-addNewConnection 파라미터 [%s][%s] 값이 오류가 있습니다. 확인하시고 다시 요청하십시요.", groupIdAndConnectionKey, serverIPsAndServerPorts);
    		LogManager.getInstance().error(retMsg);
    		return retMsg;
    	}
    }
    
    /*
     * 지정한 connection을 ConnectionManager에서 삭제한다. 
     */
    public Connectable disconnectConnection(String groupId, String connectionKey) {
    	LogManager.getInstance().info(String.format("PoolProxy.disconnectConnection [%s;;%s] 연결 객체를 connection pool에서 삭제하도록 하겠습니다.\n", groupId, connectionKey));
    	return ConnectionConfig.getInstance().disconnectConnection(groupId,  connectionKey);
    }
    
    /*
     * BW에서 최초 Pool을 초기화 -  XML String으로 넘겨준경우
     */
    public String[] initPool(String xmlString) {
        if(poolMap!=null) throw new RuntimeException("Already initialized exception");
        this.initPoolWork(xmlString);

        return getPoolConnectionList();
    }
    
    private String[] getPoolConnectionList() {
    	
        String connectionList = "";
        for(String connectionId : ConnectionConfig.getInstance().getConnections().keySet()) {        
        	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
        	if(connObj.getConnectionType().equalsIgnoreCase("POOL")) {
        		connectionList += connectionId + ",";
        	} else {
        		LogManager.getInstance().info(String.format("///// [%s][%s]는 연결정보만 유지합니다.\n", connectionId, connObj.getConnectionType()));
        	}	
        }        
        
        String[] test = connectionList.split(",");        
        LogManager.getInstance().info(connectionList);
        
        for(int i=0; i < test.length; i++ ) {
        	LogManager.getInstance().info(String.format("//// [%s] 연결대상 정보\n", test[i]));
        }
        
        LogManager.getInstance().info("///////////////////////////////////////////////////////////");
        
        return connectionList.split(",");
    }

    private void initPoolWork(String xmlString) {
    	
    	if(xmlString.contains("WITH-DB-CONFIG")) {
    		ConnectionConfig.getInstance().initConfigWithDB(NEConfigInfo);
    	} else {
    		ConnectionConfig.getInstance().initConfigWithXML(xmlString);
    	}
    	
        poolMap = new HashMap<>(ConnectionConfig.getInstance().getConnectionGroupList().size());
        for(String connectionGroupId : ConnectionConfig.getInstance().getConnectionGroupList()) {
            // Pool size는 설정된 개수 *3배 만큼 여유있게 함, 나중에 추가될 수 있으므로
            Pool<ConnectionObject> pool = new BoundedBlockingPool(ConnectionConfig.getInstance().getConnectionCount()*MULTIPLY_POOL_SIZE
                    	,new ConnectionInvalidator(ConnectionConfig.getInstance().getFailoverPolicy()));
            poolMap.put(connectionGroupId, pool);
        }

        Registry.getInstance().init(ConnectionConfig.getInstance().getConnectionCount()*MULTIPLY_POOL_SIZE);
        // 마지막으로 poolMap을 설정해준다. - 2021/12/29 
        ConnectionConfig.getInstance().setPoolMap(poolMap);
        // 설정 작업이 모두 끝나고 나면
        if(NEConfigInfo.size() > 0) {
        	NEConfigInfo.removeAll(NEConfigInfo);
        	LogManager.getInstance().info("/// 설정 작업이 모두 끝났으므로 NEConfigInfo 리스트 내의 모든 값을 제거 합니다.");
        }
    }

    /**
     * BW OnStartup 정상 수행 이후에 1회 호출 하면 됨.
     * Java에서 필요한 작업 수행 트리거
     */
    public void activatePoolMonitor() {
        //initPool 할때, 최초 한번만 ConnectionTryMonitor를 Start 시킴
    	LogManager.getInstance().info("//// ConnectionTryMoinitor와 HeartbeatMonitor를 시작하겠습니다.");
        this.connectionTryMonitor = new ConnectionTryMonitor();
        this.connectionTryMonitor.startMonitor();
        LogManager.getInstance().info(String.format("//// PoolProxy:activatePoolMonitor: [%s]\n", ConnectionConfig.getInstance().getHeartbeatPolicy().isUseHeartbeat()));
        if(heartbeatMonitor == null && ConnectionConfig.getInstance().getHeartbeatPolicy().isUseHeartbeat()) {
        	
            heartbeatMonitor = new HeartbeatMonitor();
            heartbeatMonitor.startMonitor();
        }
        
        /* 전체 메시지 보내기
         * Registry.getInstance().eventSendRequestQueue.add("ALL;;ALL");
         */
    }
    
    /*
     * Update Configuration 후 재기동을 수행한다.
     */
    public void restartPoolMonitor() {
    	
    	LogManager.getInstance().info("//// ConnectionTryMoinitor와 HeartbeatMonitor를 재기동하겠습니다.");    	
    	deactivatePoolMonitor();
    	
    	heartbeatMonitor = new HeartbeatMonitor();
        heartbeatMonitor.startMonitor();
        
        connectionTryMonitor = new ConnectionTryMonitor();
        connectionTryMonitor.startMonitor();
    }
    
    /*
     * 
     */
    public void deactivatePoolMonitor() {
       
    	LogManager.getInstance().info("//// ConnectionTryMoinitor와 HeartbeatMonitor를 정지키겠습니다.");
    	if(this.connectionTryMonitor != null && this.connectionTryMonitor.isStarted()) {
    		this.connectionTryMonitor.stopMonitor();
    	}
    	
    	if(this.heartbeatMonitor != null && this.heartbeatMonitor.isStarted()) {
    		this.heartbeatMonitor.stopMonitor();
    	}
    }

    public void sendConnManagerEvent(Connectable connectable, String code, String reason) {
    	    	
    	if(connectable != null ) {
    			ConnectionObject connObj = ConnectionConfig.getInstance().getConnectionObjectByConnection(connectable);
    			if(connObj != null) {
		    		String connectionGroupId = connObj.getConnectionGroupId();
		    		String connectionKey = connObj.getConnectionKey();
		    		String eventMessage = String.format("%s;%s;%s;%s", connectionGroupId, connectionKey, code, reason);
		    		Registry.getInstance().eventSendRequestQueue.add(eventMessage);
    			} else {
    				LogManager.getInstance().info(String.format("/// sendConnManagerEvent[bwConnection] : connObj 값이 NULL 입니다. 매칭되는 connectionId가 존재하지 않습니다.[%s][%s]\n", code, reason));
    			}
    	} else {
    		LogManager.getInstance().warn(String.format("/// sendConnManagerEvent[bwConnection] : bwConnection 값이 NULL 입니다. [%s][%s]\n", code, reason));
    	}
    }
    
    public void sendConnManagerEvent(String connectionId, String code, String reason) {
    
    	if(connectionId != null ) {
	    	String[] arr = connectionId.split(";");
	    	if(arr.length == 3) {
	    		String connectionGroupId = arr[0];
	    		String connectionKey = arr[2];
	    		String eventMessage = String.format("%s;%s;%s;%s", connectionGroupId, connectionKey, code, reason);
	    		Registry.getInstance().eventSendRequestQueue.add(eventMessage);
	    	} else {
	    		LogManager.getInstance().warn(String.format("/// sendConnManagerEvent: ConnectionId 오류 값이 형식에 맞지 않습니다. [%s][%s][%s][%d]\n", connectionId, code, reason, arr.length));
	    	}
    	} else {
    		LogManager.getInstance().warn(String.format("/// sendConnManagerEvent: connectionId 값이 NULL 입니다. [%s][%s][%s]\n", connectionId, code, reason));
    	}
    }
    
    /**
     * BW에서 OnStartup 과정 중에 최초 연결 시도 실패를 했고, 이후 계속 연결 요청이 필요한 경우 호출
     * @param connectionId 컨넥션 ID
     */
    public void tryConnectionInBackground(String connectionId) {
        try {
            // step.1 BW에 Connection 수행 요청
            // connectionQueue는 BW의 Java Event Source와 연결되어 있어, BW에서 Connection 요청을 진행 할 것임.
            LogManager.getInstance().info("/// ["+Thread.currentThread().getName()+"] connection request - " + connectionId);
            String connectionType = ConnectionConfig.getInstance().getConnections().get(connectionId).getConnectionType();
            
            // 없는 경우에만 넣는 것이 의미있을 듯
            if(connectionType.equalsIgnoreCase("POOL")) {
	            if(!Registry.getInstance().connectionRequestQueue.equals(connectionId)) {
	            	LogManager.getInstance().info(String.format("/// tryConnectionInBackground: 연결요청 큐에 [%s]를 추가 하였습니다.\n", connectionId));
	            	Registry.getInstance().connectionRequestQueue.put(connectionId);
	            } else {
	            	LogManager.getInstance().info(String.format("/// tryConnectionInBackground: 이미 연결요청 큐에 [%s]가 잇습니다.", connectionId));
	            }
            } else {
            	LogManager.getInstance().info(String.format("[%s]는 연결객체 유형이 [%s]로 연결이 필요없는 객체입니다.\n", connectionId, connectionType));
            }
            
            /* 필요한지 여부를 체크해볼 필요가 잇다 - 이미 connectionRequestQueue에 값을 넣었다면 필요가 없다. - 테스트 후 필요 없는 경우 삭제
            //step.2 연결 monitor vector에 추가            
            if(!Registry.getInstance().connectionTryVector.equals(connectionId)) {
            	System.out.println("연결요청 벡터에 ["+connectionId+"] 추가하였습니다..");
            	Registry.getInstance().connectionTryVector.add(connectionId);
            } else {
            	System.out.println("이미 연결요청 벡터에 ["+connectionId+"]가 잇습니다.");
            }
            */
            
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    
    public String[] updatePool() {
    	LogManager.getInstance().info("/// Database내 정보 변경시 해당 내용을 정리해서 반영한다. 연결객체 정보 배열을 전달해줍니다.");
    	ConnectionConfig.getInstance().updateConfigWithDB(NEConfigInfo);
    	
    	// 사이즈가 0보다 크면 삭제
    	if(NEConfigInfo.size() > 0 ) {
    		NEConfigInfo.clear(); // 다음 요청을 받기 위하여 NEConfigInformation을 업데이트 모두 삭제해준다 
    	}
    	
    	return getPoolConnectionList(); 
    }
    
    public String[] updateAndReconnect() {
    	LogManager.getInstance().info("/// Database내 정보 변경시 해당 내용을 정리해서 반영한다. 연결객체 정보 배열을 전달해줍니다.");
    	ConnectionConfig.getInstance().updateConfigWithDBAndReconnect(NEConfigInfo);
    	
    	// 사이즈가 0보다 크면 삭제
    	if(NEConfigInfo.size() > 0 ) {
    		NEConfigInfo.clear(); // 다음 요청을 받기 위하여 NEConfigInformation을 업데이트 모두 삭제해준다 
    	}
    	
    	return getPoolConnectionList(); 
    }
    
    // BW에서 connection정보가 변경된 경우 호출 됨
    public void updatePool(String xmlString) {
//        proxyTaskThread.submit(new UpdatePoolTask(xmlString));  // 별도 Thread로 실행하고 즉시 리턴 처리 함
    	proxyTaskThread.submit(()->updatePoolWork(xmlString));
    }

    private void updatePoolWork(String xmlString) {
        ConnectionConfig.getInstance().updateConfigWithXML(xmlString);
        connectionInvalidator.setFailoverPolicy(ConnectionConfig.getInstance().getFailoverPolicy());
    }

    public void addConnectionToPool(String connectionId, Connectable connectable) {
        // 설정에 있는 connection-id 인지 여부 체크
        if(!ConnectionConfig.getInstance().getConnections().containsKey(connectionId)) throw new RuntimeException("[addConnectionToPool] Unknown connection-id exception");
        
        LogManager.getInstance().info(String.format("//// addConnectionToPool - (%s)\n",  connectionId));
        // 컨넥션연결 관리 Vector에서 제거
        boolean bRet = Registry.getInstance().connectionTryVector.remove(connectionId);
        LogManager.getInstance().info(String.format(">>>> Registry.getInstance().connectionTryVector.remove(%s) [result: %s]\n", connectionId, bRet));

		ConnectionObject connectionObject = ConnectionConfig.getInstance().getConnections().get(connectionId).setConnection(connectable);

        // BW의 TCP connection 을 Key로, ConnectionID를 Value로 하는 HashMap 관리
        // 향후, TCP connection byte배열로 ConnectionID를 찾기 위함.
        Registry.getInstance().putConnection(connectionObject.getConnection(), connectionId);
        LogManager.getInstance().info(String.format("//// putBwConnection successfully! - (%s)\n",  connectionId));

        // 연결된 객체를 ConnectionObject에 설정해준다. 설정시 : 상태를 true, 연결 실패횟수를 0으로 설정해준다. - 오류가 발생할 때에 대한 처리가 없다.
        this.poolMap.get(parseConnectionGroupId(connectionId)).addObject(connectionId, connectionObject);
        
        // 연결 관련 이벤트를 생성해준다.
        String connectionGroupId = connectionId.split(";")[0];
        String connectionKey = connectionId.split(";")[2];
        String code = "INFO";
        String reason = "접속 요청이 성공적으로 수행되었습니다.";
        String eventMessage = String.format("%s;%s;%s;%s", connectionGroupId, connectionKey, code, reason);
        Registry.getInstance().eventSendRequestQueue.add(eventMessage);
        LogManager.getInstance().info(String.format("//// eventSendRequestQueue에 연결 성공 이벤트 정보를 갱신해준다.! - (%s)\n",  connectionId));
        
    }

    public static String parseConnectionGroupId(String connectionId) {
        return connectionId.substring(0,connectionId.indexOf(ConnectionObject.CID_DELIMETER));
    }
    
    public Connectable borrowConnectable(String connectionGroupId, long waitTimeSeconds) throws TimeoutException {
    	
		Connectable connectable = null;
        
        // check connnectionGroupID가 있는지 확인한다.
        if(this.poolMap.containsKey(connectionGroupId)){
        	ConnectionObject connObj = this.poolMap.get(connectionGroupId).borrowObject(waitTimeSeconds, TimeUnit.MILLISECONDS);
        	if(connObj != null) {
        		connectable = connObj.getConnection();
        		connObj.borrowConnection(); // borrowed = true로 설정 
            } else {
            	LogManager.getInstance().warn(String.format("[%s] 그룹에서 연결 객체를 가져올수 없었습니다. 다시 시도해보세요.\n", connectionGroupId));
            }        	
        } else {
        	LogManager.getInstance().warn(String.format("[%s] 그룹은 연결객체 관리자 내에 존재하지 않는 그룹아이디 이므로 확인 바랍니다.\n", connectionGroupId));
        }
                
        // System.out.printf("//// [%s] borrowConnection-[그룹아이디 유무:%s][%s][%d msecs]\n", Thread.currentThread().getName(), this.poolMap.containsKey(connectionGroupId), connectionId, waitTimeSeconds);
        return connectable;  
    }
    
   
    public void returnConnection(Connectable connectable) {
    	if(connectable == null) return;
    	
        ConnectionObject connObj =  ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable));
        // 마지막 처리 후 시간 및 갯수를 설정해준다. (문제점: Heartbeat 체크용으로 호출된 경우
        String connectionGroupId = connObj.getConnectionGroupId();        
        connObj.setLastUsedTime(System.currentTimeMillis());
        connObj.increateHandledMsgCount();
        connObj.releaseConnection(); 
        // System.out.printf("/// [%s] returnConnection-[%s][%s]%s][%d]\n", Thread.currentThread().getName(), connectionGroupId, connObj.getConnectionId(), connObj.getCurrentServerIp(), connObj.getCurrentServerPort());
        this.poolMap.get(connectionGroupId).returnObject(connObj.getConnectionId(), connObj);
    }
    
    public void requestReconnect(Connectable connectable) {
    	
    	String connectionGroupId = "";
        String currentServerIp = "";
        int currentServerPort = 0;
        long failoverTryCount = 0;
        String connectionId = "";
    	
    	if( connectable != null) {    		
    		ConnectionObject connObj = ConnectionConfig.getInstance().getConnectionObjectByConnection(connectable);	        
    		if(connObj == null) {
    			LogManager.getInstance().warn("/// 555-ERROR bwConnection에 해당하는 connectionObject가 없습니다. [확인이 필요합니다.]");
    			return; 
    		}
    		
    		connectionGroupId = connObj.getConnectionGroupId();
            currentServerIp = connObj.getCurrentServerIp();
            currentServerPort = connObj.getCurrentServerPort();
            failoverTryCount = connObj.getFailoverTryCount();
            connectionId = connObj.getConnectionId();
	        
	        if(!connObj.getConnectionObjectStatus().equalsIgnoreCase("SC")) {
	        	// SC의 경우 이미 제거했기 때문에 이중 제거의 의미가 없다.
	        	this.poolMap.get(connectionGroupId).invalidateObject(connectionId, ConnectionConfig.getInstance().getConnections().get(connectionId));	        
	        	// BW ConnectionMap에서만 제거한다.
	        	Registry.getInstance().removeConnection(connectable, connectionId);	        	
	        	LogManager.getInstance().info(String.format("//// [%s][%s][%s][%s][%d][연결 문제 발생][재접속 연결 시도 횟수: %s][재연결을 위한 큐에 넣습니다.]\n",
        				Thread.currentThread().getName(), connectionGroupId, connectionId, currentServerIp, currentServerPort, failoverTryCount));	        	
	        } else {
	        	LogManager.getInstance().info(String.format("//// [%s][%s][%s][%s][%d][연결 문제 발생][재접속 연결 시도 횟수: %s][재연결을 위한 큐에 넣습니다.] 연결객체 상태 [SC] 입니다.\n",
        				Thread.currentThread().getName(), connectionGroupId, connectionId, currentServerIp, currentServerPort, failoverTryCount));
	        }
	        
	        try {
	        	LogManager.getInstance().info(String.format("//// [%s][%s][%s][%d] 재연결을 위하여 ConnectionTryVector에 값을 넣습니다.\n", 
	        				Thread.currentThread().getName(), connectionId, currentServerIp, currentServerPort ));
	        	// Registry.getInstance().connectionTryVector.add(connectionId);
	        	Registry.getInstance().connectionRequestQueue.add(connectionId);
	        } catch(Exception ex) {
	        	
	        }
    	} else {
    		LogManager.getInstance().warn(String.format("//// [%s] PoolProxy:InvalidConnection: ++ bw connection 값이 NULL 입니다.\n", Thread.currentThread().getName()));
    	}
    }
    
   public void invalidateConnection(Connectable connectable, String code, String reason) {
    	
    	if( connectable != null) {
    		
    		String connectionGroupId = ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable)).getConnectionGroupId();
    		String connectionKey = ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable)).getConnectionKey();
	        String currentServerIp = ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable)).getCurrentServerIp();
	        int currentServerPort = ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable)).getCurrentServerPort();
	        long failoverTryCount = ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable)).getFailoverTryCount();
	        String connectionId = ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable)).getConnectionId();
	        
	        ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable)).setConnectionStatus(false);
	        ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable)).releaseConnection();
	        LogManager.getInstance().info(String.format("//// [%s][%s][%s][%s][%d][연결 문제 발생][재접속 연결 시도 횟수: %s][재연결을 위한 큐에 넣습니다.]\n",
	        				Thread.currentThread().getName(), connectionGroupId, connectionId, currentServerIp, currentServerPort, failoverTryCount));
	
	        this.poolMap.get(connectionGroupId).invalidateObject(connectionId, ConnectionConfig.getInstance().getConnections().get(connectionId));
	        
	        // BW ConnectionMap에서만 제거한다.
	        Registry.getInstance().removeConnection(connectable, connectionId);
	        
	        try {
	        	LogManager.getInstance().info(String.format("//// [%s][%s][%s][%d] 재연결을 위하여 ConnectionTryVector에 값을 넣습니다.\n", 
	        				Thread.currentThread().getName(), connectionId, currentServerIp, currentServerPort ));
	        	
	        	// 연결 재요청을 위한 Vector에 값을 넣어준다.
	        	Registry.getInstance().connectionTryVector.add(connectionId);	        	
	        	// 오류 이벤트가 발생했을 알려주고 메시지를 전달합니다 ---
	        	String eventMessage = String.format("%s;%s;%s;%s", connectionGroupId, connectionKey, code, reason);
	        	Registry.getInstance().eventSendRequestQueue.add(eventMessage);	        	
	        	
	        } catch(Exception ex) {
	        	 // 사용 Exception  생성 후
	        	 LogManager.getInstance().error(String.format("/// invalidateConnection: 연결객체 ID: %s에 오류가 관련 처리 중 오류가 발생하였습니다.\n", connectionId));
	        	 ex.printStackTrace();
	        }
	        
    	} else {
    		LogManager.getInstance().warn(String.format("//// [%s] PoolProxy:InvalidConnection: ++ bw connection 값이 NULL 입니다.\n", Thread.currentThread().getName()));
    	}
    }
    

    /*
     * 연결객체 재연결 / 일시 정지 요청을 처리합니다.
     */
    public String resumeConnectionObject(String connectionGroupId, String connectionKey) {
    	/* 
    	 * 재연결 작업 요청
    	 * 1) 상태를 SC (Suspended Connection) => NC connection으로 변경해준다.
    	 * 2) connectionRequestQueue에 추가해준다.
    	 * => 나머지 처리는 TCPConnection 프로세스에 의해서 연결 후 connection에 저장된다.
    	 *    저장될 때 다른 것과 동일한 곳에 붙어야 하므로 ConnectionConfig의 ServerType과 일치하도록 설정해준다.
    	 */
    	boolean bIsExistTarget = false;
    	int numOfConns = 0;
    	String errorMsg = "[ERROR] ";    	
    	String tmpConnectionKey = String.format("%s-0",connectionKey);
		String connectionId = String.format(ConnectionObject.CID_FORMAT, connectionGroupId, tmpConnectionKey);
		
		LogManager.getInstance().info(String.format("재연결 후 서비스 시작을 요청합니다. [%s][%s]\n", connectionGroupId, connectionKey));

		// connObj가 있는 확인부터 한다. 최소 1개 이상이 있어야 합니다. 
		ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
		
		if(connObj != null && connObj.getConnection() != null) {			
			numOfConns = connObj.getConnectionCount();
			LogManager.getInstance().info(String.format("[%s] 연결객체 정보와 개수가 몇개인지를  확인한다. [총 %d개]\n", connectionId, numOfConns));
			for(int i=0; i < numOfConns; i++) {
				tmpConnectionKey = String.format("%s-%d",connectionKey, i);
				connectionId = String.format(ConnectionObject.CID_FORMAT, connectionGroupId, tmpConnectionKey);
				connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
				if(connObj.getConnectionObjectStatus().equalsIgnoreCase("SC")) {
					connObj.setConnectionObjectStatus("NC"); // 재연결을 요청합니다. - 요청할 떄 절체 방식에 따라서 접속서버를 결정해야 한다.
					LogManager.getInstance().info(String.format("[%s] 재연결 후 서비스를 시작합니다.\n", connectionId));
					ConnectionConfig.getInstance().putConnectionRequestQueue(connectionId);
					bIsExistTarget = true;
				} else {
					errorMsg += connectionId; 
				}
			}
			if(!bIsExistTarget ) {
				errorMsg += " 이미 모두 서비스 중인 연결객체 입니다.";
			}
			
		} else {
			errorMsg = String.format("[ERROR] [%s] 연결 객체 정보가 없습니다.", connectionId);
		}
    	
		if(bIsExistTarget) {
			return String.format("정상적으로 재연결 및 서비스를 재개합니다. [%s][%s][총%d개]", connectionGroupId, connectionKey, numOfConns); 
		} else {
			return errorMsg;
		}
    }
    
    public String[] suspendConnectionObject(String connectionGroupId, String connectionKey) {
    	/*
    	 *  일시 정지 요청
    	 *  1) bwConnection 객체 값을 가져온다.
    	 *  2) connecitonId로 connection Object count 값을 가져온다. - groupId;;connnectionKey-0
    	 *  3) connectionObject Count를 가져온다. 여러 개인 경우 처리를 위하여 Loop 
    	 *  4) removeFromConnectionPool (connectionId, bwConnection)
    	 */
    	boolean bIsExistTarget = false;
    	String errorMsg = "[ERROR] ";
    	String connectionIds = "";
    	String tmpConnectionKey = String.format("%s-0",connectionKey); // 연결객체에 시퀀서를 부여한다 - 예외처리 필요
		String connectionId = String.format(ConnectionObject.CID_FORMAT, connectionGroupId, tmpConnectionKey);		
		LogManager.getInstance().info("["+connectionGroupId+"]["+connectionKey+"]["+connectionId+"]");

		// connObj가 있는 확인부터 한다. 최소 1개 이상이 있어야 합니다. 
		ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
		
		if(connObj != null && connObj.getConnection() != null) {			
			int numOfConns = connObj.getConnectionCount();
			LogManager.getInstance().info(String.format("[%s] 연결객체 정보와 개수가 몇개인지를  확인한다. [총%d개]\n", connectionId, numOfConns));
			for(int i=0; i < numOfConns; i++) {
				tmpConnectionKey = String.format("%s-%d",connectionKey, i);				
				connectionId = String.format(ConnectionObject.CID_FORMAT, connectionGroupId, tmpConnectionKey);
				connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
				if(!connObj.getConnectionObjectStatus().equalsIgnoreCase("SC")) {
					LogManager.getInstance().info(String.format("[%d][%s] 일시멈춤을 시작합니다.\n", i, connectionId));
					connObj.setConnectionObjectStatus("SC");
					connectionIds += connectionId + ",";					
					bIsExistTarget = true;
				} else {					
					errorMsg += connectionId;
				}
			}
			LogManager.getInstance().info(String.format("//// %s 를 모두 일시 멈춤시키겠습니다.\n", connectionIds));
			if(!bIsExistTarget) {
				errorMsg += " 는 이미 일시 멈춤 상태의 객체들입니다.";
			}
		} else {
			errorMsg = String.format("[%s] 연결 객체 정보가 없습니다.", connectionId);
			LogManager.getInstance().warn(errorMsg);
		}
		
		if(bIsExistTarget) {
			return connectionIds.split(",");
		} else {
			return errorMsg.split(",");
		}
		
    }
    
    public String removeFromConnectionPool(Connectable connectable) {
    	String connectionId = "";    	
    	connectionId = Registry.getInstance().getConnectionIdWithConnection(connectable);    	
    	if(connectionId != "") {
    		this.removeFromConnectionPool(connectionId, connectable);
    	}
    	return connectionId;
    }
    
    public void removeFromConnectionPool(String connectionId, Connectable connectable) {
    	
    	if(connectionId.split(";").length >= 2) {
    		String connectionGroupId = connectionId.split(";")[0];
    		String connectionKey = connectionId.split(";")[2];
	    	if( connectable != null) {
		        LogManager.getInstance().info(String.format("//// [%s][%s] 객체 정보를 (PoolMap, Registry)에서 삭제한 후 SC 상태로 변경합니다.\n", Thread.currentThread().getName(), connectionId));
		        // PoolMap에서 삭제해준다.
		        this.poolMap.get(connectionGroupId).invalidateObject(connectionId, ConnectionConfig.getInstance().getConnections().get(connectionId));
		        // BW ConnectionMap에서만 제거한다. - Registry 내에 있는 정보도 삭제해준다.	        
		        Registry.getInstance().removeConnection(connectable, connectionId);	        
		        // ConnectionObject의 상태 정보를 초기화 시켜준다.
		        ConnectionConfig.getInstance().resetConnectionObjectInfoByForcedDisconnected(connectionId);	
		        
		        // ConnectionManager 이벤트 메시지를 발송한다.
		        String eventMessage = String.format("%s;%s;INFO;%s", connectionGroupId, connectionKey, "정지 요청을 수행합니다.");
		        Registry.getInstance().eventSendRequestQueue.add(eventMessage);
		        
		        
	    	} else {
	    		LogManager.getInstance().warn(String.format("//// [%s] PoolProxy:InvalidConnection: ++ bw connection 값이 NULL 입니다.\n", Thread.currentThread().getName()));
	    	}
    	} else {
    		LogManager.getInstance().warn(String.format("[deleteFromConnectionPool][%s] 가 유효하지 않은 값을 가지고 있습니다.\n", connectionId));
    	}
    }
    
    public String removeFromConnectionConfig(Connectable connectable) {
    	String connectionId = "";    	
    	connectionId = Registry.getInstance().getConnectionIdWithConnection(connectable);    	
    	if(connectionId != "") {
    		this.removeFromConnectionConfig(connectionId, connectable);
    	}
    	return connectionId;
    }
    
    public void removeFromConnectionConfig(String connectionId, Connectable connectable) {
    	
    	if(connectionId.split(";").length >= 2) {
    		String connectionGroup = connectionId.split(";")[0];
    		String connectionKey = connectionId.split(";")[2];			
    	
	    	if( connectable != null) {
		        LogManager.getInstance().info(String.format("//// deleteFromConnectionConfig() ["+Thread.currentThread().getName()+"]["+connectionId+"] 객체 정보를 모든 저장공간에서 삭제합니다 (PoolMap, Registry)"));
		        // PoolMap에서 삭제해준다.
		        this.poolMap.get(connectionGroup).invalidateObject(connectionId, ConnectionConfig.getInstance().getConnections().get(connectionId));
		        // BW ConnectionMap에서만 제거한다. - Registry 내에 있는 정보도 삭제해준다.	        
		        Registry.getInstance().removeConnection(connectable, connectionId);	        
		        // ConnectionConfig 내에서 완전히 ConnectionObject을 삭제한다.
		        LogManager.getInstance().info(String.format("[%s][%s]->[%s] 연결객체를 제거합니다. ConnectioConfig로부터 완전히 삭제합니다.\n", connectionGroup, connectionKey, connectionId));
		        ConnectionConfig.getInstance().getConnections().remove(connectionId); 
		        
	    	} else {
	    		LogManager.getInstance().warn(String.format("//// ["+Thread.currentThread().getName()+"] PoolProxy:InvalidConnection: ++ bw connection 값이 NULL 입니다."));
	    	}
    	} else {
    		LogManager.getInstance().warn(String.format("[deleteFromConnectionConfig]["+connectionId+"] 가 유효하지 않은 값을 가지고 있습니다."));
    	}
    }
    
    public void requestConnections(String groupId, String connectionKey) {
    	
    	// 연결이 끊어진 객체의 연결을 시도합니다.- 복잡도가 올라가는 것을 방지하기 위해서 적당한 선에서 기능 정의를 마무리해야 한다.
    	LogManager.getInstance().info(String.format("[%s][%s] connection Config 내에 있는 모든 유휴 객체들을 재연결을 요청한다.\n", groupId, connectionKey));
    	for(String connectionId : ConnectionConfig.getInstance().getConnections().keySet()) {    		
    		ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    		
	    	try {
 	        	LogManager.getInstance().info(String.format("//// [%s][%s][%s][%d] 재연결을 위하여 ConnectionRequestQueue에 값을 넣습니다.\n", 
 	        					Thread.currentThread().getName(), connectionId, connObj.getCurrentServerIp(), connObj.getCurrentServerPort()));
 	        	
 	        	String connectionType = ConnectionConfig.getInstance().getConnections().get(connectionId).getConnectionType(); 	        	
 	        	if(connectionType.equalsIgnoreCase("POOL")) {
	 	        	if(!Registry.getInstance().connectionRequestQueue.equals(connectionId)) {
	 	            	LogManager.getInstance().info(String.format("연결요청 큐에 [%s]를 추가 하였습니다.\n", connectionId));
	 	            	Registry.getInstance().connectionRequestQueue.put(connectionId);
	 	            } else {
	 	            	LogManager.getInstance().info(String.format("이미 연결요청 큐에 [%s]가 잇습니다.\n", connectionType));
	 	            }
	    		} else {
	    			LogManager.getInstance().info(String.format("[%s]는 연결객체 유형이 [%s]로 연결이 필요없는 객체입니다.\n", connectionId, connectionType));
	    		}
 	        } catch(Exception ex) {
 	        	LogManager.getInstance().error(String.format("["+connectionId+"] 처리 중 오류가 발생하였습니다."+ex));
 	        }
    	}
    	// 연결 요청을 시도합니다. - 연결 가능 상태로 변경합니다. 
    	ConnectionConfig.getInstance().setConnectionStatus("D");
    }

    /**
     * BW에서 연결할 호스트 정보를 얻기 위해 호출
     * @param connectionId 연결 설정정보에서 관리하는 컨넥션 ID
     * @return IP;Port 형태의 문자열로 리턴
     */
    public String getConnectionTarget(String connectionId) {
    	// DR 구성이 되었을 경우  처리 방법에 대해서 고민할 필요가 있음.
    	
        String connectionGroup = parseConnectionGroupId(connectionId);
        ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
        
        // HOST 기준 첫번째 연결인 경우 설정값을 넣어준다.
        int numOfPoolObjects = getPooledObjectCount();        
        LogManager.getInstance().info(String.format("[PoolProxy:getConnectionTarget: %s][%d][%s]%d][현재서버유형: %s]\n", connectionId, numOfPoolObjects, 
        					connObj.getCurrentServerIp(), connObj.getCurrentServerPort(), connObj.getCurrentServerType()));
        
        return connObj.getConnectionTarget(ConnectionConfig.getInstance().getFailoverPolicy().isPortbasedFailOver(), numOfPoolObjects);
    }
    
    public String getConnectionTargetByServerType(String serverType, String connectionId) {
    	String connectionTargetServerInfo = "0.0.0.0;9999";
    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj != null) {
    		
    		// BW ConnectionMap에서만 제거한다.
        	this.poolMap.get(connObj.getConnectionGroupId()).invalidateObject(connectionId, ConnectionConfig.getInstance().getConnections().get(connectionId));
        	Registry.getInstance().removeConnection(connObj.getConnection(), connectionId);	
    		
    		LogManager.getInstance().info(String.format("/// [%s][%s][%s] \n", serverType, connectionId, connObj.getCurrentServerType()));
    		connectionTargetServerInfo = connObj.ForcedChangeConnectionServerType(serverType);
    		
    		if(connectionTargetServerInfo.contains("ERROR") == false) {
    			// 정상적인 처리 과정에 있을 경우
    			LogManager.getInstance().info(String.format("/// 연결서버 정보: [%s]\n", connectionTargetServerInfo));
    		} else {
    			LogManager.getInstance().warn(String.format("/// 해당하는 연결서버 정보를 가져올 수 없습니다. 오류 내용 [%s]\n", connectionTargetServerInfo));
    		}
    		
    	} else {
    		LogManager.getInstance().warn(String.format("/// [%s]에 해당하는 연결객체 정보가 ConnectionConfig 내에 없습니다. 확인바랍니다.\n", connectionId));
    	}
    	return connectionTargetServerInfo;
    }
    
    public String[] getConnectionId(Connectable connectable) {
    	String[] retVal = {"",""};
    	String connectionId = Registry.getInstance().getConnectionIdWithConnection(connectable);

    	if(connectionId.split(";;").length >= 2) {
    		retVal[0] = connectionId.split(";")[0];
    		retVal[1] = connectionId.split(";")[2];
    		LogManager.getInstance().info(String.format("bwConnection에 매칭되는 ConnecitonId는 [%s][%s] 입니다.\n", retVal[0], retVal[1]));
    	} else {
    		LogManager.getInstance().warn(String.format("bwConnection에 매칭되는 ConnecitonId가 없습니다. 확인이 필요합니다.\n"));
    	}
    	return retVal;
    }
    
    public int getPooledObjectCount() {
    	
    	int numOfPoolObjects = 0;
        int numOfGroups = ConnectionConfig.getInstance().getConnectionGroupList().size();
        
        for(String connectionGroupId : ConnectionConfig.getInstance().getConnectionGroupList()) {
            // Pool size는 설정된 개수 *3배 만큼 여유있게 함, 나중에 추가될 수 있으므로        	
        	numOfPoolObjects += poolMap.get(connectionGroupId).getPoolObjectCount();
            // System.out.println("["+connectionGroupId+"]["+poolMap.get(connectionGroupId).getPoolObjectCount()+" 개]");
        }
        
        // System.out.println("[ 총 그룹수: "+numOfGroups+" 개]["+numOfPoolObjects+"]");
        return numOfPoolObjects;
    }

    /**
     * 컨넥션 ID를 가지고, BW에서 생성한 Connection(byte배열)을 얻음
     * @param connectionId 켄넥션ID
     * @return BW Connection
     */
    public Connectable getConnectionById(String connectionId) {
        return Registry.getInstance().getConnection(connectionId);
    }
    
    public Connectable getConnectionByIndex(int index) {    	
    	return ConnectionConfig.getInstance().getConnectionByIndex(index); 
    }
    
    public int getConnectionCount() {
    	// 현재 연결객체의 수를 나타내준다.
    	return ConnectionConfig.getInstance().getConnectionCount();
    }
    
    public int readyToDisconnect() {
    	// 상태 정버도 변경해준다.
    	// 20220115 = ConnectionConfig.getInstance().setConnectionStatus("U"); // Update를 위하여 강제적으로 종료시킬 경우
    	return ConnectionConfig.getInstance().getConnectionCount();
    }
    
    public int startToConnect() {
    	// 상태 정버도 변경해준다.
    	ConnectionConfig.getInstance().setConnectionStatus("D"); // Update를 위하여 강제적으로 종료시킬 경ㅇ
    	return ConnectionConfig.getInstance().getConnectionCount();
    }

    /**
     * 해당 ConnectionGroup에 연결된 connection 갯수 리턴
     * @param connectionGroup Connection https://www.datacenters.com/news/what-is-a-vcpu-and-how-do-you-calculate-vcpu-to-cpuoup 명
     * @return 연결된 connection 수
     */
    public int getConnectionCount(String connectionGroup) {
        if(this.poolMap==null) return 0; //초기화 전에 호출된 경우에 대비
        return this.poolMap.get(connectionGroup).getPoolObjectCount();
    }
    
    public String getConnectionInfo(String groupId, String connectionKey, String code, String reason) {    	
    	LogManager.getInstance().info(String.format("/// [%s][%s]의 연결상태 정보를 가져온다.\n", groupId, connectionKey));    	
    	return ConnectionConfig.getInstance().getConnectionInfo(groupId, connectionKey, code, reason);
    }

    public void receivedHeartBeat(Connectable connectable) {
        ConnectionObject connObj =  ConnectionConfig.getInstance().getConnections().get(Registry.getInstance().getConnectionIdWithConnection(connectable));
        connObj.setLastUsedTime(System.currentTimeMillis());
    }
}