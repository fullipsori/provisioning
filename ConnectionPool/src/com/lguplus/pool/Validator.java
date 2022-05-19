package com.lguplus.pool;

public interface Validator <T> {
    public void invalidate(T t, Pool<T> pool);
}

