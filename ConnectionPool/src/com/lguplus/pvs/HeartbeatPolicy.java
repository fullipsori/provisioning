package com.lguplus.pvs;

import com.lguplus.pvs.util.LogManager;

public class HeartbeatPolicy {
    // heartbeat을 주고 받을 주기(단위=초)
    private int intervalSeconds;
    // heartbeat 실패로 처리할 시간(단위=초), 통상 heartbeat 주기의 2.5~3.5배
    private int heartbeatFailSeconds;
    // heartbeat을 사용할 지 말지 여부
    private boolean useHeartbeat = false;

    public HeartbeatPolicy(boolean useHeartbeat) {
        this.useHeartbeat = useHeartbeat;
    }

    public HeartbeatPolicy(int intervalSeconds, int maxIdleSeconds, boolean useHeartbeat) {
    	LogManager.getInstance().info(String.format("[하트비트 인터벌: "+intervalSeconds+"초][최대 유휴 시간:"+maxIdleSeconds+"초]["+useHeartbeat+"]"));
        this.intervalSeconds = intervalSeconds; 
        this.heartbeatFailSeconds = maxIdleSeconds;
        this.useHeartbeat = useHeartbeat;
    }

    public int getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public boolean isUseHeartbeat() {
        return useHeartbeat;
    }

    public void setUseHeartbeat(boolean useHeartbeat) {
        this.useHeartbeat = useHeartbeat;
    }

    public int getHeartbeatFailSeconds() {
        return heartbeatFailSeconds;
    }

    public void setHeartbeatFailSeconds(int heartbeatFailSeconds) {
        this.heartbeatFailSeconds = heartbeatFailSeconds;
    }
}
