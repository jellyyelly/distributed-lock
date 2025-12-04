package com.cheatsheet.distributedlock.enums;

/**
 * 지원하는 락 타입을 정의하는 열거형
 */
public enum LockType {
    /**
     * MySQL GET_LOCK/RELEASE_LOCK
     */
    MYSQL_SESSION,
    
    /**
     * PostgreSQL Advisory Lock
     */
    POSTGRES_ADVISORY,
    
    /**
     * Redis Lua Script Lock
     */
    REDIS_LUA,
    
    /**
     * Redis SETNX Lock
     */
    REDIS_SETNX
}
