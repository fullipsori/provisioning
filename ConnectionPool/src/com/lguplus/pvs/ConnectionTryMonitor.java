package com.lguplus.pvs;

import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.lguplus.pvs.util.LogManager;

public class ConnectionTryMonitor {
    private ExecutorService monitor = null;
    private long reconn_interval = 0;
    private long start_delay = 10;
    private boolean bStarted = false;
    private final LogManager logManager;

    /*
     * 연결 요청 처리를 위한 Thread
     */
    class ConnectionTryMonitorTask implements Runnable {
        @Override
        public void run() {
        	
        	// 연결객체 Pool과 ConnectionConfig 정보와 불일치 여부가 있는지 확인하고 정리해주는 작업을 먼저 수행한다.
        	checkInvalidateConnection();
        	
        	if(ConnectionConfig.getInstance().getFailoverPolicy().isHostbasedFailOver()) {
        		// 호스트 기반의 절체인 경우
        		doHostBaseFailOver();
        	}else if (ConnectionConfig.getInstance().getFailoverPolicy().isPortbasedFailOver()) {
        		// 포트 기반의 절체인 경우
        		doPortBaseFailOver();
        	}
        }
    }
    
    /*
     * ConnectionConfig내 정보와 ConnectionPool내 정보가 불일치할 경우를 파악하고 정정해준다.
     */
    private void checkInvalidateConnection() {    	
    	int count = 0;
    	logManager.info("******************************************************************************");
    	for( String connectionId : ConnectionConfig.getInstance().getConnections().keySet()) {
			count++;
			ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);			            	
			if(connObj.getConnectionType().equalsIgnoreCase("POOL") && connObj.getConnectionStatus() == true) {				
				// ConnectionPool에 해당 객체가 있는지 확인한다.
				if(ConnectionConfig.getInstance().getConnectionObjectFromPool(connObj.getConnectionGroupId(), connObj.getConnectionId()) == null) {					
					connObj.initServerInfoByServerType(connObj.getCurrentServerType());
					logManager.info(String.format("[%d] Config는 있느나 Pool내 객체 정보가 없습니다. [%s][%s][%d][%s]를 넣어주었습니다.\n", 
									count, connectionId, connObj.getCurrentServerIp(), connObj.getCurrentServerPort(), connObj.getCurrentServerType()));
				} else {
					logManager.info(String.format("[%d] Config와 Pool내 정보가 일치합니다. [%s][%s][%d][%s]\n", 
									count, connectionId, connObj.getCurrentServerIp(), connObj.getCurrentServerPort(), connObj.getCurrentServerType()));
				}
				
			} else if(connObj.getConnectionType().equalsIgnoreCase("POOL") && connObj.getConnectionStatus() == false){
				// ConnectionPool에 연결객체 값이 있으면 오류 - (연결이 종료된 연결객체 입니다)
				if(ConnectionConfig.getInstance().getConnectionObjectFromPool(connObj.getConnectionGroupId(), connObj.getConnectionId()) == null) {
					// 정상 케이스 - nothing to do
				} else {
					// 비정상 케이스 - 연결이 종료된 경우는 객체가 있으면 안된다. - 자동 정리 혹은 정리 작업을 수행해준다.
				}
			}
		}
    	logManager.info("******************************************************************************");
    }
    
    public ConnectionTryMonitor() {
    	logManager = LogManager.getInstance();
        monitor = Executors.newScheduledThreadPool(1);
    }

    public void startMonitor() {
        start_delay = ConnectionConfig.getInstance().getReconnectionTryIntervalSec();        
        reconn_interval = ConnectionConfig.getInstance().getReconnectionTryIntervalSec();
        logManager.info(String.format("//// 재연결 모니터링 작업 [시작 지연: %d초][재연결 인터벌: %d초][재연결 모니터링 데몬을 시작합니다.]\n", start_delay, reconn_interval));
        
        ((ScheduledExecutorService) monitor).scheduleAtFixedRate(
                new ConnectionTryMonitorTask(),
                start_delay,
                reconn_interval,
                TimeUnit.SECONDS
        );
        bStarted = true;
    }    
    
    public boolean isStarted() {
    	return bStarted;
    }
    
    public void stopMonitor() {
    	logManager.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    	logManager.info("+ NE Application 정지에 따라 TCP 연결 요청 모니터링 수행을 멈춥니다.");
    	logManager.info("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
    	if(!monitor.isShutdown()) {
    		monitor.shutdown();
    	}
    	bStarted = false;
    }
    
        
    /*
     *  PortBased 절체 방식으로 수행해야 할 경우
     *  Registry의 connectionTryVector iterate 하면서, 이직 연결되지 않은 "컨넥션ID"를
     *  Registry의 connectionRequestQueue에 전달(enqueue)하는 작업 수행
     */
    private void doPortBaseFailOver() {
    	
    	int numOfConnectedObjects = ConnectionConfig.getInstance().getNumberOfConnectedObjectsInPools(); // 현재 ConnectionPool에 있는 값 기준
    	int numOfConnectionGroups = ConnectionConfig.getInstance().getNumberOfConnectionGroups();
    	
    	String connectionServerType = ConnectionConfig.getInstance().getConnectionServerType();
    	String connectionFailOverMode = ConnectionConfig.getInstance().getConnectionFailOverMode();
    	String connectionStatus = ConnectionConfig.getInstance().getConnectionStatus();
    	String alreadyReqConnectionIds = ""; 
    	
    	// 연결 혹은 비연결 상태인 경우 처리 connectionTryMonitor를 수행한다. - (C)onnection or (D)isconnection    
    	if(connectionStatus.equalsIgnoreCase("C") || connectionStatus.equalsIgnoreCase("D")) {	    	
    		
	    	if(connectionFailOverMode.equals("A")) {
		    	// 포트 기반 절체 이면서 DR이 아닌 경우 스위칭하면서 연결하도록 한다. - 호스트 혹은 DR의 경우 전체적으로 변경후 재연결을 수행한다.
		    	if(connectionServerType != "D") {
		    		// 1. connectionTryVector에 연결 요청이 들어와 있는 경우
		    		alreadyReqConnectionIds = putConnectionRequestQueue();
		    	}
		    	
		    	// 서버 단위 절체에서는 ConnectionManager 내에 있는 모든 값들이 0이 되는 경우 A => B => A 로 전체 연결을 시도한다.
		    	putConnectionRequestQueueWhenlessNumOfConnectedObjected(alreadyReqConnectionIds);
		    	
	    	} else if(connectionFailOverMode.equals("M") ) {
	    		// 수동 절체 모드로 모니터링에서는 작업하지 않습니다.
	    		logManager.info("*************************************************************************");
	    		logManager.info("서버 접속 모드를 수동으로 운영 중입니다. 관리자 화면에서 접속을 원하는 서버 유형을 지정하십시요.");
	    		logManager.info(String.format("    A(ctive) / B(ackup) / D(isaster Recovery) [%s]\n", ConnectionConfig.getInstance().getConnectionServerType()));
	    		logManager.info("*************************************************************************");
	    		
	    		// 4. 수동 재연결 시도를 요청한 vector에서 꺼내서 재설정 요청을 수행한다.
	    		putConnectionRequestQueueWhenManualMode();
	    	} 
	    	
	    	// Connection 연결객체 정보를 콘솔에 출력한다.
	    	displayConnectionObjectInfo(connectionServerType, numOfConnectionGroups, numOfConnectedObjects);
	    	
    	} else if(connectionStatus.equalsIgnoreCase("U")) {
	    	// 업데이트 중인 경우에 대해서 정리한다. (U)pdate connection object information
    		logManager.info("//////////////////////////////////////////////////////////////////////////////");
    		logManager.info("// 현재 NE 설정 정보를 업데이트하고 있습니다. - 완료가 되면 D(isconnet) or C(onnect)로 변경됩니다.");
    		logManager.info("//////////////////////////////////////////////////////////////////////////////");
    	}
    }
    
    /*
     *  PortBased 절체 방식으로 수행해야 할 경우
     *  Registry의 connectionTryVector iterate 하면서, 이직 연결되지 않은 "컨넥션ID"를
     *  Registry의 connectionRequestQueue에 전달(enqueue)하는 작업 수행
     */
    private void doHostBaseFailOver() {
    	
    	int numOfConnectedObjects = ConnectionConfig.getInstance().getNumberOfConnectedObjectsInPools(); // 현재 ConnectionPool에 있는 값 기준
    	int numOfConnectionGroups = ConnectionConfig.getInstance().getNumberOfConnectionGroups();
    	
    	String connectionServerType = ConnectionConfig.getInstance().getConnectionServerType();
    	String connectionFailOverMode = ConnectionConfig.getInstance().getConnectionFailOverMode();
    	String connectionStatus = ConnectionConfig.getInstance().getConnectionStatus();
    	String alreadyReqConnectionIds = ""; 
    	
    	// 연결 혹은 비연결 상태인 경우 처리 connectionTryMonitor를 수행한다. - (C)onnection or (D)isconnection    
    	if(connectionStatus.equalsIgnoreCase("C") || connectionStatus.equalsIgnoreCase("D")) {	    	
    		
	    	if(connectionFailOverMode.equals("A")) {
		    	// 포트 기반 절체 이면서 DR이 아닌 경우 스위칭하면서 연결하도록 한다. - 호스트 혹은 DR의 경우 전체적으로 변경후 재연결을 수행한다.
		    	if(numOfConnectedObjects > 0) {
		    		// 1. connectionTryVector에 연결 요청이 들어와 있는 경우
		    		alreadyReqConnectionIds = putConnectionRequestQueue();
		    		
		    	} else if(numOfConnectedObjects == 0 && !connectionServerType.equalsIgnoreCase("D")) {
		    		// 서버 단위 절체에서는 ConnectionManager 내에 있는 모든 값들이 0이 되는 경우 A => B => A 로 전체 연결을 시도한다.
		    		// 모든 연결객체가 끊어졌다는(종료) 의미이므로 연결객체 내부의 IP Address를 변경해준다. 
		    		boolean bChanged = changeAllConnectionObjectsServerType();		    		
		    		// 변경이 되었다는 의미 - 모든 연결객체가 연결 종료된 경우 처리
		    		if(bChanged) {
		    			putConnectionRequestQueueWhenServerTypeChanged();
		    		} else {
		    			// 이 경우 혹시 MC가 있는지 획인해봅니다. (M)ove (C)onnection 강제로 타켓 서버를 이동시킨다는 의미
		    			putConnectionRequestQueueWhenAllDisconnected();
		    		}
		    	} 
		    	
		    	// 위에서 들어왔는데도 불구하고 아직도 작은 경우 처리
		    	if (numOfConnectedObjects < ConnectionConfig.getInstance().getPoolConnectionCount()) {
		    		// 3. 연결되지 않는 객체가 존재한다는 의미이다. - 위에서 신청되지 않는 것 중에서 connectionTryVector에 없는 객체 중 연결상태가 종료로 되어 있는 경우
		    		putConnectionRequestQueueWhenlessNumOfConnectedObjected(alreadyReqConnectionIds);
		    	}
		    	
	    	} else if(connectionFailOverMode.equals("M") ) {
	    		// 수동 절체 모드로 모니터링에서는 작업하지 않습니다.
	    		logManager.info("*************************************************************************");
	    		logManager.info("서버 접속 모드를 수동으로 운영 중입니다. 관리자 화면에서 접속을 원하는 서버 유형을 지정하십시요.");
	    		logManager.info(String.format("    A(ctive) / B(ackup) / D(isaster Recovery) [%s]\n", ConnectionConfig.getInstance().getConnectionServerType()));
	    		logManager.info("*************************************************************************");
	    		
	    		// 4. 수동 재연결 시도를 요청한 vector에서 꺼내서 재설정 요청을 수행한다.
	    		putConnectionRequestQueueWhenManualMode();
	    	} 

    	} else if(connectionStatus.equalsIgnoreCase("U")) {
    		logManager.info("//////////////////////////////////////////////////////////////////////////////");
    		logManager.info("// 현재 NE 설정 정보를 업데이트하고 있습니다. - 완료가 되면 D(isconnet) or C(onnect)로 변경됩니다.");
    		logManager.info("//////////////////////////////////////////////////////////////////////////////");
    	}
		// Connection 연결객체 정보를 콘솔에 출력한다.
		displayConnectionObjectInfo(connectionServerType, numOfConnectionGroups, numOfConnectedObjects);
    }
    
    private void displayConnectionObjectInfo(String connectionServerType, int numOfConnectionGroups, int numOfConnectedObjects) {
        // 매5초마다 들어오는지 확인한다 - 현재 연결되어 있는 서버 타입 : 총 그룹 수와 연결객체 수 -> 0로면 변경해주고 연결 방실을 정리해줘야 한다.
    	boolean bLoop = true;
    	if(Registry.getInstance().getConnectionTry().size() == 0) {
    		bLoop = false;
    	}
    	
    	if(ConnectionConfig.getInstance().getFailoverPolicy().isPortbasedFailOver()) {
    		ConnectionConfig.getInstance().displayConnectionObjectInfo();
    	}else if(ConnectionConfig.getInstance().getFailoverPolicy().isHostbasedFailOver()) {
    		ConnectionConfig.getInstance().displayConnectionObjectInfo();
    		logManager.info(String.format("connectionTryMonitor 상에서 체크하기 [%d초] 수행 중 입니다. [for loop 진입 여부: %s][연결 서버타입:%s][그룹수: %d개][연결 객체수: %d개]\n",
    							reconn_interval, bLoop, ConnectionObject.getServerType(connectionServerType), numOfConnectionGroups, numOfConnectedObjects));		    		
    		logManager.info("////////////////////////////////////////////////////////////////////////////////////////////////");
    	}
    }
    
    private boolean changeAllConnectionObjectsServerType() {    	
    	String connectionServerType = ConnectionConfig.getInstance().getConnectionServerType();    	
		if(connectionServerType.equalsIgnoreCase("A")) {
			// 모든 connectionObject의 연결 IP Address를 Backup으로 전환해준다.
			if(ConnectionConfig.getInstance().changeAllConnectionObjectsTargetServer("B") > 0) {		    			
				return true;
			}
		} else if(connectionServerType.equalsIgnoreCase("B")) {
			// 모든 connectionObject의 연결 IP Address를 Active로 전환해준다.
			if(ConnectionConfig.getInstance().changeAllConnectionObjectsTargetServer("A") > 0) {
				return true;
			}
		}
		return false;
    }
    
    /*
     * 1. connectionTryVector에 연결 요청이 들어와 있는 경우
     */
    private String putConnectionRequestQueue() {
    	String alreadyReqConnectionIds = "";
    	
    	Vector<String> connTryVector = Registry.getInstance().getConnectionTry();
    	for(String connectionId : connTryVector) {        	
        	logManager.info(String.format("1. [%s] 재연결을 위하여 connectionRequestQueue에 넣어줍니다.\n", connectionId));
			ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);			            	
			if(connObj.getConnectionType().equalsIgnoreCase("POOL")) {
				Registry.getInstance().putConnRequest(connectionId);
				alreadyReqConnectionIds += connectionId + ",";
			} else {
				logManager.info("//// 연결객체 정보가 INFO 유형인 경우 - 연결에 필요한 정보만 보유 [필요시: 로직 구현]\n");
			}
        }
    	return alreadyReqConnectionIds;
    }

	/*
	 * 2.1 모든 연결 객체가 끊어진 경우 - 이전 상태가 SC인 경우에도 연결해야 하는 경우 SC 비교 구문을 빼준다.
	 */
    private void putConnectionRequestQueueWhenServerTypeChanged() {
    	int count = 0;
		for( String connectionId : ConnectionConfig.getInstance().getConnections().keySet()) {
			count++;
			ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);			            	
			if(connObj.getConnectionType().equalsIgnoreCase("POOL") && !connObj.getConnectionObjectStatus().equalsIgnoreCase("SC")) {
				if(Registry.getInstance().putConnRequest(connectionId)) {
					logManager.info(String.format("2.1 [%d] 대상 서버가 변경되었습니다. 재접속을 요청 큐에 [%s][%s][%d]를 넣어주었습니다. [bChanged] \n", count, connectionId, 
																						connObj.getCurrentServerIp(), connObj.getCurrentServerPort()));
					
				}
			}
		}
    }

	/*
	 * 2.2 호스트 단위의 절체이면서 객체의 연결상태가 false 이면서 객체 상태가 MC - SC 구분 없이 연결요청에 들어간다.
	 */
    private void putConnectionRequestQueueWhenAllDisconnected() {
    	int count = 0;
		logManager.info(String.format("2.2 [(M)oving (C)onnections 로 옮겨간 서버 유형이 %s입니다.]\n", ConnectionConfig.getInstance().getConnectionServerType()));
		for( String connectionId : ConnectionConfig.getInstance().getConnections().keySet()) {
			count++;
			ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);

			if(connObj.getConnectionType().equalsIgnoreCase("POOL") && connObj.getConnectionObjectStatus().equalsIgnoreCase("MC")) {
				if(Registry.getInstance().putConnRequest(connectionId)) {
					logManager.info(String.format("2.2 [%d] 대상 서버가 변경되었습니다. 재접속을 요청 큐에 [%s]를 넣어주었습니다. [연결객체 상태: %s] \n", count, connectionId, connObj.getConnectionObjectStatus()));
				}
			}
		}
    }
	    
    /*
     * 3. 연결객체 수가 Connection Pool 모든 객체 수보다 적은 경우 
     */
    public void putConnectionRequestQueueWhenlessNumOfConnectedObjected(String alreadyReqConnectionIds) {
    	int count = 0;
    	for( String connectionId : ConnectionConfig.getInstance().getConnections().keySet()) {
			count++;
			ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
			if(connObj.getConnectionStatus() == false && connObj.getConnectionType().equalsIgnoreCase("POOL") &&
			   alreadyReqConnectionIds.contains(connectionId) == false && !connObj.getConnectionObjectStatus().equalsIgnoreCase("SC")) {
				if(Registry.getInstance().putConnRequest(connectionId)) {
					logManager.info(String.format("3. [%d] 대상 서버가 변경되었습니다. 재접속을 요청 큐에 [%s]를 넣어주었습니다. [연결되지 않은 객체가 존재하는 경우]\n", count, connectionId));
				}
			} else {
				// 연결 대상이 아닙니다. ConnectionTryVector상에서 처리했거 연결객체 상태가 (S)uspend (C)onnection 인 경우입니다.
				// System.out.printf("[%d] 이 연결객체[%s]은 이미 위에서 연결요청 큐에 있거나 대상이 아닙니다.\n", count, connectionId);
			}
		}
    }
    
    /*
     * 4. 수동 재연결 시도를 요청한 vector에서 꺼내서 재설정 요청을 수행한다.
     */
    private void putConnectionRequestQueueWhenManualMode() {
    	Vector<String> connTryVector = Registry.getInstance().getConnectionTry();
	    for(String connectionId : connTryVector) {
	    	logManager.info(String.format("[재연결을 위하여 %s 객체를 연결요청 큐에 넣어줍니다]\n", connectionId));
			ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
			if(connObj.getConnectionType().equalsIgnoreCase("POOL")) {
				Registry.getInstance().putConnRequest(connectionId);
			} else {
				logManager.info("//// 연결객체 정보가 INFO 유형인 경우 - 연결에 필요한 정보만 보유 [필요시: 로직 구현]\n");
			}
		}
    }
}
