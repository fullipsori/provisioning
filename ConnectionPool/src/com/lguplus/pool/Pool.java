package com.lguplus.pool;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public interface Pool<T> {
    // Pool된 객체 얻기, idle 객체가 반환될 때 까지 계속 기다림
    T borrowObject();

    // Pool에서 객체 얻기, 단 일정 시간만 기다리고 리턴 함. 만약 idle 객체가 없으면 null을 리턴 함
    T borrowObject(long time, TimeUnit unit) throws TimeoutException;
    
    /* 사용한 객체를 Pool에 반환 */
    void returnObject(String id, T t);

    /* Pool 없애기 */
    void shutdown();

    /* 생성한 객체를 Pool에 저장 요청 */
    void addObject(String id, T t);

    // id 기반으로 Object 리턴
    T getObjectById(String id);

    /* 객체가 유효하지 않아, Pool 에서 invalidate 시킬 것 요청 */
    void invalidateObject(String id, T t);

    int getIdleObjectCount();

    int getActiveObjectCount();

    int getPoolObjectCount();
}
