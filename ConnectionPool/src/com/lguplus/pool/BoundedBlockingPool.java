package com.lguplus.pool;

import java.util.concurrent.*;

public class BoundedBlockingPool<T> implements Pool<T> {
    private int size;
    private ConcurrentHashMap<String ,T>   allObjects = new ConcurrentHashMap<>();
    private BlockingDeque<T> idleObjects;
    private Validator<T> validator;
    private ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean shutdownCalled;

    public BoundedBlockingPool(
            int size,
            Validator<T> validator)
    {
        super();
        this.validator = validator;
        this.size = size;
        idleObjects = new LinkedBlockingDeque<T>(size);
        shutdownCalled = false;
    }

    @Override
    public void addObject(String id, T t) {
    	int prevIdleSize = this.idleObjects.size(); 
    	int prevAllSize = this.allObjects.size();
        if(this.allObjects.containsKey(id)) {
            throw new IllegalStateException("The object id is duplicated - " + id);
        }
        this.allObjects.put(id,t);
        this.idleObjects.addLast(t);
        // System.out.printf("//// [addObject 연결 후 [%s] IdleObject [%d->%d개] AllObject [%d->%d개]\n", id, prevIdleSize, this.idleObjects.size(), prevAllSize, this.allObjects.size());
    }

    public T borrowObject(long timeOut, TimeUnit unit) throws TimeoutException {
    	
    	T t = null;    	
        if(!shutdownCalled) {
        	
            try {
        		t = idleObjects.pollFirst(timeOut, unit);
                if(t==null) throw new TimeoutException(String.format("%d;%d;BorrowObject timeout %dsecs", this.allObjects.size(), this.idleObjects.size(),timeOut));  
                else return t;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
        }
        throw new IllegalStateException("Object pool is already shutdown");
    }    

    @Override
    public T getObjectById(String id) {
    	return this.allObjects.get(id);
    }

    public void returnObject(String id, T t) {
    	int prevIdleSize = getIdleObjectCount();
        this.idleObjects.addLast(t);
        // System.out.printf("//// [%s]은 Idle객체의 수는 반환 후 %d개에서 %d개로 바뀌었습니다.\n", id, prevIdleSize,  getIdleObjectCount());
    }

    @Override
    public int getIdleObjectCount() {
        return this.idleObjects.size();
    }

    @Override
    public int getActiveObjectCount() {
        return this.allObjects.size() - this.idleObjects.size();
    }

    @Override
    public int getPoolObjectCount() {
        return this.allObjects.size();
    }

    @Override
    public T borrowObject() {
        if(!shutdownCalled) {
            T t = null;
            try {
                t = idleObjects.takeFirst(); // 가용 객체가 생길때 까지 계속 대기
            }catch(InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return t;
        }
        throw new IllegalStateException("Object pool is already shutdown");
    }


    public void shutdown() {
        shutdownCalled = true;
        executor.shutdownNow();
        clearResources();
    }

    private void clearResources() {
        for(T t: idleObjects) {
            validator.invalidate(t,this);
        }
    }

    //all object에서도 삭제할 것.
    public void invalidateObject(String id, T t) {
        this.allObjects.remove(id);
        if(t != null) {
			this.idleObjects.remove(t);
			this.validator.invalidate(t,this);
        }
        // System.out.println("[deleteObject 연결을 종료시키고 deque & 가용 객체 관리맵에서 모두 삭제합니다 ["+id+"][모든 객체 개수: "+this.allObjects.size()+"개][유휴 객체 개수: "+this.idleObjects.size()+"개]");
    }

}
