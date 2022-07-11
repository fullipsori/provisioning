package com.lguplus.pvs;

import com.lguplus.pvs.util.LogManager;

public enum NECONN {
	/*
	 *  0. NECONN_ID						NE 시퀀스 넘버 (유일키)
	 *  1. NEA_APPNAME						NE 에이전트 유형명  4GHSS
	 *  2. NEA_PODNAME						NE 에이전트명(POD 명)    예) HSS-01
	 *  3. NEA_PROCNAME			    		NE 에이전트의 Process Name BW
	 *  4. CONN_COUNT						연결 객체 최대 설정 개수
	 *  5. CONN_KEY							연결 ID  예) A-Z 중 하나
	 *  6. CONN_IP_A						Active 연결 IP  예) 127.0.0.1	   
	 *  7. CONN_PORT_A						Active 포트 번호
	 *  8. CONN_IP_B						Backup 연결 IP  예) 127.0.0.1	   
	 *  9. CONN_PORT_B						Backup 포트 번호
	 * 10. CONN_IP_D						DR 연결 IP  예) 127.0.0.1	   
	 * 11. CONN_PORT_D						DR 포트 번호
	 * 12. CONN_TYPE						연결객체 유형 POOL, INFO(연결 정보만 유지)
	 * 13. CONN_GROUPNAME					연결객체 그룹명  예) HSS, VMS
	 * 14. FAILOVER_POLICY					Failover 정책: Port | Host 단위
	 * 15. FAILOVER_RETRY_COUNT				FailOver 발생시 문제 발생 이벤트를 발생시킬 횟수 지정		
	 * 16. RECONNECTION_TRY_INTERVAL_SEC	재연결을 위한 인터벌
	 * 17. BORROW_WAIT_TIMEOUT				연결객체 가져오기 최대 대기 시간
	 * 18. BORROW_WAIT_TIMEOUT_RETRY_COUNT  연결객체 가져오기 최대 반복 횟수 (오류 발생시)
	 * 19. READ_TIMEOUT						BW READ 대기 시간 (타임아웃)
	 * 20. READ_TIMEOUT_RETRY_COUNT 		BW READ Timeout 발생 최대 몇번 시도할 것인지를 결정한다.
	 * 21. WRITE_TIMEOUT						BW READ 대기 시간 (타임아웃)
	 * 22. WRITE_TIMEOUT_RETRY_COUNT 		BW READ Timeout 발생 최대 몇번 시도할 것인지를 결정한다.
	 * 23. BACKUP_TYPE						백업 유형: cold/hot/active-slave
	 * 24. DR_EXIST_YN						DR 구성 유무
	 * 25. AUTO_FAILOVER_YN					자동 절체 여부
	 * 26. HEARTBEAT_YN						하트비트 사용유무
	 * 27. HEARTBEAT_INTERVAL				하트비트 간격
	 * 28. HERATBEAT_TRY_COUNT				하트비트 시도 횟수
	 * 29. HERATBEAT_MESSAGE				하트비트 메시지
	 * 30. NE_MANAGEMENT 					담당자 정보
	 * 31. COMMENT							주석
	 * 32. CONN_MODE                        SOCKET/BW
	 * 33. NEAGENT_ID                       NEAGENT ID
	 */    	
	
	 NECONN_ID(0),				
	 NEA_APPNAME(1),			
	 NEA_PODNAME(2),
	 NEA_PROCNAME(3),			
	 CONN_COUNT(4),
	 CONN_KEY(5),
	 CONN_IP_A(6),	 
	 CONN_PORT_A(7),
	 CONN_IP_B(8),	 
	 CONN_PORT_B(9),
	 CONN_IP_D(10),	 
	 CONN_PORT_D(11),
	 CONN_TYPE(12),
	 CONN_GROUPNAME(13),			
	 FAILOVER_POLICY(14),
	 FAILOVER_RETRY_COUNT(15),
	 RECONNECTION_TRY_INTERVAL_SEC(16),
	 BORROW_WAIT_TIMEOUT(17),	
	 BORROW_WAIT_TIMEOUT_RETRY_COUNT(18), 
	 READ_TIMEOUT(19),
	 READ_TIMEOUT_RETRY_COUNT(20),
	 WRITE_TIMEOUT(21),
	 WRITE_TIMEOUT_RETRY_COUNT(22),	 
	 BACKUP_TYPE(23),			
	 DR_EXIST_YN(24),			
	 AUTO_FAILOVER_YN(25),		
	 HEARTBEAT_YN(26),
	 HEARTBEAT_INTERVAL(27),		
	 HERATBEAT_TRY_COUNT(28),
	 HERATBEAT_MESSAGE(29),
	 NE_MANAGEMENT(30),
	 DESCRIPTION(31),
	 CONN_MODE(32),
	 NEAGENT_ID(33);
	
	 public final int idx;
	 
	 public static void printAllValueAndIndex() {
		 for (NECONN neconfig : NECONN.values()) {  
	           LogManager.getInstance().info(String.format(neconfig+" Index ("+neconfig.idx+")"));
		 }
	 }
	 
	 private NECONN(int idx) {
		 this.idx = idx;
	 }
}
