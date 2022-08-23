package com.lguplus.pvs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Statistics {

	private static boolean printTransaction = false;
	private static int inTxnCount = 0;
	private static int outTxnCount = 0;
	private static long sumOfElapsed = 0;
	private static final Map<Integer, Integer> distMap = new ConcurrentHashMap<>();
	private static final Map<String, Pair<Integer, Long>> tagTimeMap = new ConcurrentHashMap<>();
	private static final Map<String, ArrayList<Pair<String, Long>>> runningMap = new ConcurrentHashMap<>();
	private static ScheduledExecutorService perfChecker = null;
	
	public Statistics(){}

	public static int getInTxnCount() {
		return inTxnCount;
	}

	public static void setInTxnCount(int inTxnCount) {
		Statistics.inTxnCount = inTxnCount;
	}

	public static int getOutTxnCount() {
		return outTxnCount;
	}

	public static void setOutTxnCount(int outTxnCount) {
		Statistics.outTxnCount = outTxnCount;
	}

	/**
	 * 
	 * @param seq Transaction 의 sequence number
	 * @param startTime Transaction 이 BW 로 최초 유입된 시간으로 seq+starttime 을 unique key 로 사용하여 구분한다.
	 * @param tag 측정 위치의 tag 정보로서 "start" 와 "end" 는 Transaction 의 시작과 끝으로서 고정이고, 나머지 tag 는 해당 위치의 명시작 정보이다.
	 */
	public static void addPerformance(String seq, String startTime, String tag) {
		
		long currentTime = System.currentTimeMillis();
		String uniqueKey = String.format("%s:%s", seq, startTime);
		
		if("start".equalsIgnoreCase(tag)) {
			setInTxnCount(getInTxnCount() + 1);
			ArrayList<Pair<String, Long>> elem = new ArrayList<>();
			elem.add(new Pair<>(tag, currentTime));
			runningMap.put(uniqueKey, elem);
		}else if("end".equalsIgnoreCase(tag)) {
			ArrayList<Pair<String, Long>> elem = runningMap.remove(uniqueKey);
			if(elem == null) return;
			setOutTxnCount(getOutTxnCount() + 1);
			elem.add(new Pair<>(tag, currentTime));
			String perfResult = addStatistics(uniqueKey, elem);
			if(printTransaction && perfResult != null) System.out.println(perfResult);

		}else {
			ArrayList<Pair<String, Long>> elem = runningMap.get(uniqueKey);
			if(elem == null) {
				elem = new ArrayList<Pair<String, Long>>();
			}
			elem.add(new Pair<>(tag, currentTime));
			runningMap.put(uniqueKey, elem);
		}
	}

	private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
	/**
	 *  addStatistics 함수는 "end" 를 만나는 순간 호출되어 tag 들의 평균 latency 들을 기록한다.
	 * @param uniqueKey seq+starttime 으로서 transaction 들을 구분하기 위한 key 이다.
	 * @param list transaction 의 latency 정보이다.
	 * @return  transaction 의 latency 정보로서 출력용도로 사용한다.
	 */
	private static String addStatistics(String uniqueKey, ArrayList<Pair<String, Long>> list) {
		// TODO Auto-generated method stub
		StringBuffer perfResult = new StringBuffer(String.format("Perf:%s", uniqueKey));
		long txnStart = 0;
		long prevTime = 0;
		long elapsed = 0;
		for(Pair<String, Long> pair : list) {
			String tag = pair.getFirst();
			long time = pair.getSecond();
			long diffTime = time - prevTime;
			
			if("start".equalsIgnoreCase(tag)) {
				txnStart = pair.getSecond();
				prevTime = txnStart;
				perfResult.append(String.format(",%s,%s", simpleDateFormat.format(new Date()), tag));
			}else {
				if(txnStart <= 0) return null;
				
				if("end".equalsIgnoreCase(tag)) {
					elapsed = time - txnStart;
					sumOfElapsed += elapsed;
					distMap.merge((int)elapsed/10, 1, Integer::sum);
				}
				tagTimeMap.merge(tag, new Pair<>(1,diffTime), (a,b)-> new Pair<>(a.getFirst()+b.getFirst(), a.getSecond()+b.getSecond()));
				perfResult.append(String.format(",%d,%s", diffTime, tag));
				prevTime = time;
			}
		}
		perfResult.append(String.format(",%d,elapsed\n", elapsed));
		return perfResult.toString();
	}
	
	/**
	 * 특정 시점에 통계정보를 출력할때 사용한다.
	 */
	public static void printStatistics() {
		if(distMap.isEmpty() || tagTimeMap.isEmpty()) return;
		
		System.out.println("******* Performance Statistics *******");
		System.out.printf("***** AVG_LAT[%d] ", sumOfElapsed/outTxnCount);
		System.out.printf("IN_CNT[%d], ", inTxnCount);
		System.out.printf("OUT_CNT[%d]\n", outTxnCount);
		System.out.println("***** DIST(LAT:CNT) -> " + distMap.entrySet().stream().map(data -> String.format("(%d:%d)", data.getKey(), data.getValue())).collect(Collectors.joining(",")));
		System.out.println("***** TIME MAP(TAG:[RUN_CNT,AVG_LAT] -> " + tagTimeMap.entrySet().stream().map(data -> String.format("(%s:[%d,%d])",data.getKey(), data.getValue().getFirst(), data.getValue().getSecond()/data.getValue().getFirst())).collect(Collectors.joining(",")));
		System.out.println("**************************************");
		
	}
	

	private static int prevInput = 0;
	private static int prevOutput = 0;
	
	/**
	 * 주기적으로 전 주기 대비 in-transaction 의 변화량, out-transaction 의 변화량, 현재 처리중인 transaction 갯수 (PROC) 를 로그로 출력한다.
	 * @param start 시작 여
	 * @param period 몇초단위로 할지 
	 */
	private static void runPeriodicChecker(boolean start, int period) {
		if(perfChecker != null && !perfChecker.isShutdown()) perfChecker.shutdown();
		if(start) {
			perfChecker = Executors.newScheduledThreadPool(1); 
			perfChecker.scheduleAtFixedRate(()-> { 
					System.out.printf("******** CHECKER->TIME[%s]:IN[%d],OUT[%d],PROC[%d]\n", 
						simpleDateFormat.format(new Date()),
						prevInput!=0? (getInTxnCount()-prevInput)/period:0,
						prevOutput!=0? (getOutTxnCount()-prevOutput)/period:0,
						runningMap.size()); 
					prevInput = getInTxnCount();
					prevOutput = getOutTxnCount();
				},
				3, 
				(long)period, 
				TimeUnit.SECONDS);
		}
	}
	
	/**
	 * BW Activator 에서 해당 모듈을 초기화하기 위해 호출한다. 
	 * @param printTxnMode transaction 단위로 로그로 출력할지 여부 
	 * @param runCheckerMode runPeriodicChecker 의 시작 여부 
	 * @param checkPeriod runPeriodicChecker 의 체크 주기 
	 */
	public static void initialize(boolean printTxnMode, boolean runCheckerMode, int checkPeriod) {
		System.out.println("initialize:" + printTxnMode + " " + runCheckerMode + " " + checkPeriod);
		printTransaction = printTxnMode;
		if(runCheckerMode) {
			runPeriodicChecker(runCheckerMode, checkPeriod);
		}
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
