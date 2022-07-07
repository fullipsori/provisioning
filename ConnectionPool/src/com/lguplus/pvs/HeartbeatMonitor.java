package com.lguplus.pvs;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.lguplus.pvs.util.LogManager;

import java.time.format.DateTimeFormatter;  
import java.time.LocalDateTime;    

public class HeartbeatMonitor {
    private ExecutorService monitor = null;
    private boolean bStarted = false;
    private static final int HB_MONITOR_DELAY = 30;
    private static final int HB_THREAD_INTERVAL_SEC =10;
    // ConnectionConfig.getInstance().getHeartbeatPolicy().getIntervalSeconds() <= 이전에 없다.
    private final LogManager logManager;

    public HeartbeatMonitor() {
    	logManager = LogManager.getInstance();
        monitor = Executors.newScheduledThreadPool(1);
    }

    public void startMonitor() {
    	((ScheduledExecutorService) monitor).scheduleAtFixedRate(
                ()->monitorIdleConnections(),
                HB_MONITOR_DELAY,
                HB_THREAD_INTERVAL_SEC,
                TimeUnit.SECONDS
        );
        bStarted = true;
        logManager.info(String.format("//// 하트비트 모니터링을 시작합니다. [쓰레드 체크 간격: %d][%s]\n", HB_THREAD_INTERVAL_SEC, bStarted));
    }
    
    public boolean isStarted() {
    	return bStarted;
    }
    
    public void stopMonitor() {
    	bStarted = false;
    	logManager.info("///////////////////////////////////////////////////////////////////////");
    	logManager.info(String.format("// NE Application 정지에 따라 하트비트 모니터링 수행을 멈춥니다. [%s]\n", bStarted));
    	logManager.info("///////////////////////////////////////////////////////////////////////");
    	
    	if(!monitor.isShutdown()) {
    		monitor.shutdown();
    	}
 
    }
    
    private void displayHeartbeatMessage(ConnectionObject connObj, long idleTime, String msg) {        
    	logManager.info(String.format("[%s - 하트비트 Idle [%d초/%d초][%s][%d][%s][%s][하트비트 실패 인터벌: %d초][연결상태: %s][%s]\n",
			    			Thread.currentThread().getName(), idleTime/1000, connObj.getHeartBeatInterval()/1000, 
			    			connObj.getCurrentServerIp(),
			    			connObj.getCurrentServerPort(),
			    			connObj.getCurrentServerType(),
			    			connObj.getConnectionObjectStatus(),
			    			connObj.getHeartBeatFailSeconds()/1000,	                	
			    			connObj.getConnectionStatus(), msg));
    }

    public static boolean needHeartBeat(String connectionId) {
    	ConnectionObject connObj = ConnectionConfig.getInstance().getConnections().get(connectionId);
    	if(connObj == null) return false;

		long idleTime = (System.currentTimeMillis() - connObj.getLastUsedTime()); 
		return (!ConnectionConfig.getInstance().getConnectionStatus().equalsIgnoreCase("U") && ConnectionConfig.getInstance().getHeartbeatPolicy().isUseHeartbeat())
				&& (connObj.getConnectionType().equalsIgnoreCase("POOL") && !connObj.getConnectionObjectStatus().equalsIgnoreCase("SC") && !connObj.isBorrowed() && idleTime > connObj.getHeartBeatInterval());
    }

    private void monitorIdleConnections() {
    
    	DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");  
	   	LocalDateTime now = LocalDateTime.now();
	   	int currSecond = now.getSecond();
	   	logManager.info(String.format("/////////////////// 하트비트 모니터: %s [현재: %d초]\n", dtf.format(now), currSecond));
	   	
    	String connectionStatus = ConnectionConfig.getInstance().getConnectionStatus();
    	
    	
    	if(!connectionStatus.equalsIgnoreCase("U") && ConnectionConfig.getInstance().getHeartbeatPolicy().isUseHeartbeat()) {
    		/*
    		 * 1) Heartbeat 사용 여부가 false로 되어 있는 경우
    		 * 2) 연결객체 정보를 Update하는 중이면 수행하지 않는다.   
    		 */
    		
	        for(ConnectionObject connObj : ConnectionConfig.getInstance().getConnections().values()) {
	        	
	            long idleTime = (System.currentTimeMillis() - connObj.getLastUsedTime()); // DB 설정은 초 단위로 설정 | 실제 프로그램 상에서는 밀리세컨드 단위로 운여
	            
	            // Idle 상태에 있는 객체만 Heartbeat 메시지 전송 대상으로 체크한다. 
	            if(connObj.getConnectionType().equalsIgnoreCase("POOL") && !connObj.getConnectionObjectStatus().equalsIgnoreCase("SC") && !connObj.isBorrowed()) {
	            	
	            	if(idleTime> connObj.getHeartBeatFailSeconds()) {
		            	// 삭제 먼저 진행하고 작업해야 한다. -  설정값에 따라서 진행되진다. 종료 후 재연결을 시도한다.  POOL 타입이건, SC(Suspended Connection 상태)인 경우
		            	// heartbeatInterval * heartbeatTryCount = 최대 시도횟수 곱하기를 시도한다.
	            		displayHeartbeatMessage(connObj, idleTime, "하트비트 메시지 체크 시간 초과: 연결종료 후 재접속 시도");		                
		                Registry.getInstance().addDisconnAndConnRequest(connObj.getConnectionId());
		                
		            } else if(idleTime > connObj.getHeartBeatInterval()) {	            	
		                // 하트비트 인터벌을 초과했으며, 현재 접속 상태인 경우에만 보낸다.		            	
		            	displayHeartbeatMessage(connObj, idleTime, "마지막 사용시간이 하트비트 인터벌 시간 초과하였습니다. - 하트비트 메시지 보내기");	            	
		                Registry.getInstance().addHeartBeat(connObj.getConnectionId());
		                
		            } else {
		            	// 하트비트 체크 대상이 아닌 경우  idleTime < connObj.getHeartBeatFailSeconds() 미만인 경우 
		            	displayHeartbeatMessage(connObj, idleTime, "마지막 사용시간이 하트비트 체크간격 미만입니다. - SKIP 체크 다음");
		            }
	            	
		        } else if(connObj.getConnectionObjectStatus().equalsIgnoreCase("SC")) {
		        	// 현재 Suspend 상태인 경우 - 가장 최근 사용시간을 현재 시간으로 변경해준다.
		        	connObj.setLastUsedTime(System.currentTimeMillis());
		        }
	        } // for loop - ConnectionConfig내에 있는 모든 ConnectionObject를 체크하기 전까지
	        
    	} else {
    		// 현재 정보 변경 강제 종료 혹은 Disconnected 상태입니다.
    		boolean useHeartBeat = ConnectionConfig.getInstance().getHeartbeatPolicy().isUseHeartbeat();
    		if(connectionStatus.equalsIgnoreCase("U")) { 
    			
    			logManager.info(String.format("* Heartbeat 메시지를 보낼 상태 [%s]가 아닙니다. Heartbeat 사용유무 [%s]\n", connectionStatus, useHeartBeat));
    		} else {
    			// Heartbeat 사용 여부 확인
    			logManager.info(String.format("* Heartbeat 사용여부 [%s]로 확인되었습니다.\n", useHeartBeat));
    		}
    	}
    }
}
