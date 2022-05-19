package com.lguplus.pvs;

import java.util.Optional;
import java.util.Vector;
import java.util.Map.Entry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class Registry {
    private static Registry ourInstance = new Registry();

    public static Registry getInstance() {
        return ourInstance;
    }

    // Queue for Heartbeat
    public BlockingQueue<String> heartbeatQueue = null;

    // Queue for Open Connection
    public BlockingQueue<String> connectionRequestQueue = null;
    
    // Queue for Close Connection
    public BlockingQueue<String> disconnectionRequestQueue = null;
    
    // Queue for DisconnectAdnConnection
    public BlockingQueue<String> disconnAndConnectionRequestQueue = null;
    
    // Queue for send ConnectionManager's events to the system monitoring  
    public BlockingQueue<String> eventSendRequestQueue = null;

    // 모든 연결을 저장관리, KEY=컨넥션ID, Value=BW컨넥션
    private ConcurrentHashMap<String, Connectable> connectionMapByConnectionId = new ConcurrentHashMap<>();

    // 모든 연결 요청 온것들의 컨넥션 ID를 관리, 연결 성공시 까지 계속 시도하는 것을 보장하기 위함.
    // 별도 Thread가 주기적으로 이 Vector를 모니터링 하여,
    // 즉, 향후 연결 성공하면 삭제 됨
    public Vector<String> connectionTryVector = new Vector<>();

    private Registry() {
    }

    public void init(int size) {
        this.connectionRequestQueue = new ArrayBlockingQueue<>(size);
        this.disconnectionRequestQueue = new ArrayBlockingQueue<>(size);
        this.disconnAndConnectionRequestQueue = new ArrayBlockingQueue<>(size);
        this.eventSendRequestQueue  = new ArrayBlockingQueue<>(size);
        this.heartbeatQueue = new ArrayBlockingQueue<>(size);
    }

    public String getConnectionIdWithConnection(Connectable connectable) {
        Optional<Entry<String, Connectable>> result = this.connectionMapByConnectionId.entrySet().stream().filter(conn -> conn.getValue().equals(connectable)).findFirst();
        if(result.isPresent()) {
            return result.get().getKey();
        }
        LogManager.getInstance().warn("getConnectionIdWithConnection not found : " + connectable);
        return null;
    }

    public void putConnection(Connectable connectable, String connectionId) {
        this.connectionMapByConnectionId.put(connectionId, connectable);
    }

    public void removeConnection(Connectable connectable, String connectionId) {
        this.connectionMapByConnectionId.remove(connectionId);
    }

    public Connectable getConnection(String connectionId) {
        return this.connectionMapByConnectionId.get(connectionId);
    }
}
