package com.lguplus.pvs;

import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.lguplus.pvs.util.LogManager;

public class Registry {
    private static Registry ourInstance = new Registry();

    public static Registry getInstance() {
        return ourInstance;
    }

    // Queue for Heartbeat
    private BlockingQueue<String> heartbeatQueue = null;

    // Queue for Open Connection
    private BlockingQueue<String> connectionRequestQueue = null;
    
    // Queue for Close Connection
    private BlockingQueue<String> disconnectionRequestQueue = null;
    
    // Queue for DisconnectAdnConnection
    private BlockingQueue<String> disconnAndConnectionRequestQueue = null;
    
    // Queue for send ConnectionManager's events to the system monitoring  
    private BlockingQueue<String> eventSendRequestQueue = null;
    
    // Queue for send SMS-Message 
    private BlockingQueue<String> smsSendRequestQueue = null;
    
    // 모든 연결 요청 온것들의 컨넥션 ID를 관리, 연결 성공시 까지 계속 시도하는 것을 보장하기 위함.
    // 별도 Thread가 주기적으로 이 Vector를 모니터링 하여,
    // 즉, 향후 연결 성공하면 삭제 됨
    private final Vector<String> connectionTryVector = new Vector<>();
    
    private final LogManager logManager;

    private Registry() {
    	logManager = LogManager.getInstance();
    }

    public void init(int size) {
        this.connectionRequestQueue = new ArrayBlockingQueue<>(size);
        this.disconnectionRequestQueue = new ArrayBlockingQueue<>(size);
        this.disconnAndConnectionRequestQueue = new ArrayBlockingQueue<>(size);
        this.eventSendRequestQueue  = new ArrayBlockingQueue<>(size);
        this.heartbeatQueue = new ArrayBlockingQueue<>(size);
        this.smsSendRequestQueue = new ArrayBlockingQueue<>(size);
    }

    public boolean needHeartBeat(String connectionId) {
    	return HeartbeatMonitor.needHeartBeat(connectionId);
    }
    
    public String takeHeartBeat() throws Exception {
    	return heartbeatQueue.take();
    }
    
    public boolean putHeartBeat(String connectionId) {
    	try {
			if(connectionId != null && !heartbeatQueue.contains(connectionId)) heartbeatQueue.put(connectionId);
			return true;
    	}catch(Exception e) {
    		logManager.error("heartBeatQueue error:" + e.getMessage());
    		return false;
    	}
    }
    
    public boolean addHeartBeat(String connectionId) {
    	try {
			if(connectionId != null && !heartbeatQueue.contains(connectionId)) heartbeatQueue.add(connectionId);
			return true;
    	}catch(Exception e) {
    		logManager.error("ConnectionId:" + connectionId + " dont add because HeartBeat queue is full " + " error:" + e.getMessage());
    		return false;
    	}
    }

    public String takeConnRequest() throws Exception {
    	return connectionRequestQueue.take();
    }
    
    /** put은 queue 에 공간이 없는 경우에 무한 블록킹이 된다.  add 는 exception 이 발생되고 바로 반환된다. **/
    public boolean putConnRequest(String connectionId) {
    	try {
			if(connectionId != null && !connectionRequestQueue.contains(connectionId)) connectionRequestQueue.put(connectionId);
			return true;
    	}catch(Exception e) {
    		logManager.error("connectionRequestQueue error:" + e.getMessage());
    		return false;
    	}
    }

    public boolean addConnRequest(String connectionId){
    	try {
			if(connectionId != null && !connectionRequestQueue.contains(connectionId)) connectionRequestQueue.add(connectionId);
			return true;
    	}catch(Exception e) {
    		logManager.error("ConnectionId:" + connectionId + " dont add because connectionRequestQueue is full " + " error:" + e.getMessage());
    		return false;
    	}
    }

    public String takeDisconnRequest() throws Exception {
    	return disconnectionRequestQueue.take();
    }
    
    public boolean putDisconnRequest(String connectionId) {
    	try {
			if(connectionId != null && !disconnectionRequestQueue.contains(connectionId)) disconnectionRequestQueue.put(connectionId);
			return true;
    	}catch(Exception e) {
    		logManager.error("disconnectionRequestQueue error" + e.getMessage());
    		return false;
    	}
    }

    public boolean addDisconnRequest(String connectionId) {
    	try {
			if(connectionId != null && !disconnectionRequestQueue.contains(connectionId)) disconnectionRequestQueue.add(connectionId);
			return true;
    	}catch(Exception e) {
    		logManager.error("ConnectionId:" + connectionId + " dont add because disconnectionRequestQueue is full " + " error:" + e.getMessage() );
    		return false;
    	}
    }
    
    public String takeDisconnAndConnRequest() throws Exception {
    	return disconnAndConnectionRequestQueue.take();
    }
    
    public boolean putDisconnAndConnRequest(String connectionId) {
    	try {
			if(connectionId != null && !disconnAndConnectionRequestQueue.contains(connectionId)) disconnAndConnectionRequestQueue.put(connectionId);
			return true;
    	}catch(Exception e) {
    		logManager.error("disconnAndConnectionRequestQueue error" + e.getMessage());
    		return false;
    	}
    }
    
    public boolean addDisconnAndConnRequest(String connectionId) {
    	try {
			if(connectionId != null && !disconnAndConnectionRequestQueue.contains(connectionId)) disconnAndConnectionRequestQueue.add(connectionId);
			return true;
    	}catch(Exception e) {
    		logManager.error("ConnectionId:" + connectionId + " dont add because disconnAndConnectionRequestQueue is full " + " error:" + e.getMessage());
    		return false;
    	}
    }
    
    public String takeEventSendRequest() throws Exception {
    	return eventSendRequestQueue.take();
    }
    
    public boolean putEventSendRequest(String message) {
    	try {
			eventSendRequestQueue.put(message);
			return true;
    	}catch(Exception e) {
    		logManager.error("eventSendRequestQueue error" + e.getMessage());
    		return false;
    	}
    }
    
    public boolean addEventSendRequest(String message) {
    	try {
    		String rmessage = message + ";true";
			eventSendRequestQueue.add(rmessage);
			return true;
    	}catch(Exception e) {
    		logManager.error("Message:" + message + " dont add because eventSendRequestQueue is full " + " error:" + e.getMessage());
    		return false;
    	}
    }
    
    public boolean addEventSendRequest(String message, boolean statusChanged) {
    	try {
    		String rmessage = message;
    		if(statusChanged) {
    			rmessage += ";true";
    		}else {
    			rmessage += ";false";
    		}
			eventSendRequestQueue.add(rmessage);
			return true;
    	}catch(Exception e) {
    		logManager.error("Message:" + message + " dont add because eventSendRequestQueue is full " + " error:" + e.getMessage());
    		return false;
    	}
    }

    public String takeSMSSendRequest() throws Exception {
    	return smsSendRequestQueue.take();
    }

    public boolean putSMSSendRequest(String connectionId, String message) {
    	try {
    		if(connectionId == null) return false;
    		String smsMessage = String.format("[%s][%s]", ConnectionObject.getGroupIdFromConnectionId(connectionId), message);
			smsSendRequestQueue.put(smsMessage);
			return true;
    	}catch(Exception e) {
    		logManager.error("smsSendRequestQueue error" + e.getMessage());
    		return false;
    	}
    }
    
    public boolean addSMSSendRequest(String connectionId, String message) {
    	try {
    		if(connectionId == null) return false;
    		String smsMessage = String.format("[%s][%s]", connectionId, message);
			smsSendRequestQueue.add(smsMessage);
			return true;
    	}catch(Exception e) {
    		logManager.error("Message:" + connectionId +  ":" + message + " dont add because smsSendRequestQueue is full " + " error:" + e.getMessage());
    		return false;
    	}
    }

    public void addConnectionTry(String connectionId) {
    	if(!connectionTryVector.contains(connectionId)) {
    		connectionTryVector.add(connectionId);
    	}
    }
    
    public boolean removeConnectionTry(String connectionId) {
    	if(connectionTryVector.contains(connectionId)) return connectionTryVector.remove(connectionId);
    	return false;
    }
    
    public Vector<String> getConnectionTry() {
    	return connectionTryVector;
    }
}
